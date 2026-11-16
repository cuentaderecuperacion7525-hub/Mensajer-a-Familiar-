package com.example.calls

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

class WebRtcCallManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var boundLocalRenderer: SurfaceViewRenderer? = null
    private var boundRemoteRenderer: SurfaceViewRenderer? = null

    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false
    private var appliedRemoteAnswer = false

    private val _hasRemoteVideo = MutableStateFlow(false)
    val hasRemoteVideo: StateFlow<Boolean> = _hasRemoteVideo.asStateFlow()

    private val _iceStateText = MutableStateFlow("Conectando red...")
    val iceStateText: StateFlow<String> = _iceStateText.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    companion object {
        @Volatile
        private var factoryInitialized = false

        private val STUN_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302",
            "stun:openrelay.metered.ca:80"
        )
    }

    private var customTurnUrl: String = PreferencesManager.DEFAULT_TURN_URL
    private var customTurnUsername: String = PreferencesManager.DEFAULT_TURN_USERNAME
    private var customTurnCredential: String = PreferencesManager.DEFAULT_TURN_CREDENTIAL

    fun setCustomTurnConfig(url: String, username: String, credential: String) {
        customTurnUrl = url.trim().ifBlank { PreferencesManager.DEFAULT_TURN_URL }
        customTurnUsername = username.trim().ifBlank { PreferencesManager.DEFAULT_TURN_USERNAME }
        customTurnCredential = credential.trim().ifBlank { PreferencesManager.DEFAULT_TURN_CREDENTIAL }
    }

    private fun ensureFactoryReady(): Boolean {
        if (peerConnectionFactory != null && eglBase != null) return true
        return try {
            synchronized(WebRtcCallManager::class.java) {
                if (!factoryInitialized) {
                    val initOptions = PeerConnectionFactory.InitializationOptions.builder(appContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                    PeerConnectionFactory.initialize(initOptions)
                    factoryInitialized = true
                }
            }
            val egl = EglBase.create()
            eglBase = egl
            val audioModule = JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()
            val encoderFactory = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(egl.eglBaseContext)
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioModule)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
            audioModule.release()
            true
        } catch (_: Throwable) {
            _iceStateText.value = "Modo voz/señal activa"
            false
        }
    }

    private fun configureAudioRouting(enableSpeaker: Boolean) {
        try {
            val am = audioManager ?: return
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
            setSpeakerphone(enableSpeaker)
        } catch (_: Throwable) {
        }
    }

    @Suppress("DEPRECATION")
    fun setSpeakerphone(enabled: Boolean) {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = am.availableCommunicationDevices
                val targetType = if (enabled) {
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                val device = devices.firstOrNull { it.type == targetType }
                if (device != null) {
                    am.setCommunicationDevice(device)
                } else {
                    am.isSpeakerphoneOn = enabled
                }
            } else {
                am.isSpeakerphoneOn = enabled
            }
        } catch (_: Throwable) {
        }
    }

    private fun createPeerConnectionAndTracks(
        isVideoCall: Boolean,
        onLocalIceCandidate: (IceCandidate) -> Unit,
        onIceConnected: () -> Unit
    ): PeerConnection? {
        closeSessionInternal()
        if (!ensureFactoryReady()) return null
        val factory = peerConnectionFactory ?: return null
        val egl = eglBase ?: return null

        configureAudioRouting(enableSpeaker = isVideoCall)

        val iceServers = buildList {
            STUN_SERVERS.forEach { stun ->
                add(PeerConnection.IceServer.builder(stun).createIceServer())
            }
            val turnUrls = customTurnUrl.split(",", ";", "\n")
                .map { it.trim() }
                .filter { it.startsWith("turn:") || it.startsWith("turns:") }
                .ifEmpty {
                    listOf(
                        "turn:openrelay.metered.ca:80",
                        "turn:openrelay.metered.ca:443",
                        "turn:openrelay.metered.ca:443?transport=tcp"
                    )
                }
            turnUrls.forEach { turnUri ->
                add(
                    PeerConnection.IceServer.builder(turnUri)
                        .setUsername(customTurnUsername)
                        .setPassword(customTurnCredential)
                        .createIceServer()
                )
            }
            if (!turnUrls.any { it.contains("openrelay.metered.ca:443") }) {
                add(
                    PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                        .setUsername(PreferencesManager.DEFAULT_TURN_USERNAME)
                        .setPassword(PreferencesManager.DEFAULT_TURN_CREDENTIAL)
                        .createIceServer()
                )
            }
        }
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val pc = factory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    when (state) {
                        PeerConnection.IceConnectionState.CHECKING -> {
                            _iceStateText.value = "Estableciendo audio/video..."
                        }
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            _iceStateText.value = "Voz y video HD en vivo"
                            onIceConnected()
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            _iceStateText.value = "Reconectando señal..."
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            _iceStateText.value = "Red limitada"
                        }
                        else -> {}
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate != null) {
                        onLocalIceCandidate(candidate)
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is VideoTrack) {
                        track.setEnabled(true)
                        remoteVideoTrack = track
                        _hasRemoteVideo.value = true
                        mainHandler.post {
                            boundRemoteRenderer?.let { renderer ->
                                runCatching { track.addSink(renderer) }
                            }
                        }
                    }
                }
            }
        ) ?: return null

        peerConnection = pc

        // Audio track
        try {
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            }
            val aSource = factory.createAudioSource(audioConstraints)
            audioSource = aSource
            val aTrack = factory.createAudioTrack("FAMICHAT_AUDIO_0", aSource)
            aTrack.setEnabled(true)
            localAudioTrack = aTrack
            pc.addTrack(aTrack, listOf("FAMICHAT_STREAM"))
        } catch (_: Throwable) {
        }

        // Video track (if video call)
        if (isVideoCall) {
            try {
                val enumerator = Camera2Enumerator(appContext)
                val deviceNames = enumerator.deviceNames
                val frontName = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                    ?: deviceNames.firstOrNull()
                if (frontName != null) {
                    _isFrontCamera.value = enumerator.isFrontFacing(frontName)
                    val capturer = enumerator.createCapturer(frontName, null)
                    if (capturer != null) {
                        videoCapturer = capturer
                        val texHelper = SurfaceTextureHelper.create("FamiChatCamThread", egl.eglBaseContext)
                        surfaceTextureHelper = texHelper
                        val vSource = factory.createVideoSource(capturer.isScreencast)
                        videoSource = vSource
                        capturer.initialize(texHelper, appContext, vSource.capturerObserver)
                        capturer.startCapture(640, 480, 24)

                        val vTrack = factory.createVideoTrack("FAMICHAT_VIDEO_0", vSource)
                        vTrack.setEnabled(true)
                        localVideoTrack = vTrack
                        pc.addTrack(vTrack, listOf("FAMICHAT_STREAM"))

                        mainHandler.post {
                            boundLocalRenderer?.let { renderer ->
                                runCatching { vTrack.addSink(renderer) }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        return pc
    }

    fun startCallerSession(
        isVideoCall: Boolean,
        onOfferCreated: (String) -> Unit,
        onLocalIceCandidate: (IceCandidate) -> Unit,
        onIceConnected: () -> Unit = {}
    ) {
        try {
            val pc = createPeerConnectionAndTracks(isVideoCall, onLocalIceCandidate, onIceConnected) ?: return
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(
                    MediaConstraints.KeyValuePair(
                        "OfferToReceiveVideo",
                        if (isVideoCall) "true" else "false"
                    )
                )
            }
            pc.createOffer(
                object : SimpleSdpObserver() {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        if (desc == null) return
                        pc.setLocalDescription(
                            object : SimpleSdpObserver() {
                                override fun onSetSuccess() {
                                    onOfferCreated(desc.description)
                                }
                            },
                            desc
                        )
                    }
                },
                constraints
            )
        } catch (_: Throwable) {
        }
    }

    fun startCalleeSession(
        isVideoCall: Boolean,
        offerSdp: String,
        onAnswerCreated: (String) -> Unit,
        onLocalIceCandidate: (IceCandidate) -> Unit,
        onIceConnected: () -> Unit = {}
    ) {
        try {
            val pc = createPeerConnectionAndTracks(isVideoCall, onLocalIceCandidate, onIceConnected) ?: return
            val remoteOffer = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
            pc.setRemoteDescription(
                object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        remoteDescriptionSet = true
                        drainPendingCandidates()
                        val constraints = MediaConstraints().apply {
                            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                            mandatory.add(
                                MediaConstraints.KeyValuePair(
                                    "OfferToReceiveVideo",
                                    if (isVideoCall) "true" else "false"
                                )
                            )
                        }
                        pc.createAnswer(
                            object : SimpleSdpObserver() {
                                override fun onCreateSuccess(desc: SessionDescription?) {
                                    if (desc == null) return
                                    pc.setLocalDescription(
                                        object : SimpleSdpObserver() {
                                            override fun onSetSuccess() {
                                                onAnswerCreated(desc.description)
                                            }
                                        },
                                        desc
                                    )
                                }
                            },
                            constraints
                        )
                    }
                },
                remoteOffer
            )
        } catch (_: Throwable) {
        }
    }

    fun applyRemoteAnswerIfNeeded(answerSdp: String) {
        if (answerSdp.isBlank() || appliedRemoteAnswer) return
        val pc = peerConnection ?: return
        try {
            appliedRemoteAnswer = true
            val remoteAnswer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
            pc.setRemoteDescription(
                object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        remoteDescriptionSet = true
                        drainPendingCandidates()
                    }
                },
                remoteAnswer
            )
        } catch (_: Throwable) {
        }
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidateSdp: String) {
        if (candidateSdp.isBlank()) return
        try {
            val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateSdp)
            val pc = peerConnection
            if (pc != null && remoteDescriptionSet) {
                pc.addIceCandidate(candidate)
            } else {
                synchronized(pendingRemoteCandidates) {
                    pendingRemoteCandidates.add(candidate)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun drainPendingCandidates() {
        val pc = peerConnection ?: return
        synchronized(pendingRemoteCandidates) {
            val copy = pendingRemoteCandidates.toList()
            pendingRemoteCandidates.clear()
            copy.forEach { cand ->
                runCatching { pc.addIceCandidate(cand) }
            }
        }
    }

    fun setMuted(muted: Boolean) {
        try {
            localAudioTrack?.setEnabled(!muted)
            audioManager?.isMicrophoneMute = muted
        } catch (_: Throwable) {
        }
    }

    fun setCameraEnabled(enabled: Boolean) {
        try {
            localVideoTrack?.setEnabled(enabled)
        } catch (_: Throwable) {
        }
    }

    fun switchCamera() {
        try {
            val capturer = videoCapturer ?: return
            capturer.switchCamera(
                object : CameraVideoCapturer.CameraSwitchHandler {
                    override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                        _isFrontCamera.value = isFrontCamera
                        mainHandler.post {
                            boundLocalRenderer?.setMirror(isFrontCamera)
                        }
                    }

                    override fun onCameraSwitchError(errorDescription: String?) {}
                }
            )
        } catch (_: Throwable) {
        }
    }

    fun bindLocalVideoRenderer(renderer: SurfaceViewRenderer) {
        mainHandler.post {
            try {
                val egl = eglBase ?: return@post
                if (boundLocalRenderer !== renderer) {
                    boundLocalRenderer?.let { old -> runCatching { localVideoTrack?.removeSink(old) } }
                    runCatching { renderer.release() }
                    runCatching { renderer.init(egl.eglBaseContext, null) }
                    renderer.setMirror(_isFrontCamera.value)
                    renderer.setEnableHardwareScaler(true)
                    renderer.setZOrderMediaOverlay(true)
                    boundLocalRenderer = renderer
                }
                localVideoTrack?.addSink(renderer)
            } catch (_: Throwable) {
            }
        }
    }

    fun bindRemoteVideoRenderer(renderer: SurfaceViewRenderer) {
        mainHandler.post {
            try {
                val egl = eglBase ?: return@post
                if (boundRemoteRenderer !== renderer) {
                    boundRemoteRenderer?.let { old -> runCatching { remoteVideoTrack?.removeSink(old) } }
                    runCatching { renderer.release() }
                    runCatching { renderer.init(egl.eglBaseContext, null) }
                    renderer.setMirror(false)
                    renderer.setEnableHardwareScaler(true)
                    boundRemoteRenderer = renderer
                }
                remoteVideoTrack?.addSink(renderer)
            } catch (_: Throwable) {
            }
        }
    }

    private fun closeSessionInternal() {
        remoteDescriptionSet = false
        appliedRemoteAnswer = false
        synchronized(pendingRemoteCandidates) {
            pendingRemoteCandidates.clear()
        }
        _hasRemoteVideo.value = false
        _iceStateText.value = "Conectando red..."

        try {
            boundLocalRenderer?.let { r -> runCatching { localVideoTrack?.removeSink(r) } }
            boundRemoteRenderer?.let { r -> runCatching { remoteVideoTrack?.removeSink(r) } }
        } catch (_: Throwable) {
        }

        try {
            videoCapturer?.let { cap ->
                runCatching { cap.stopCapture() }
                runCatching { cap.dispose() }
            }
            videoCapturer = null
        } catch (_: Throwable) {
        }

        try {
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null
        } catch (_: Throwable) {
        }

        try {
            localVideoTrack?.dispose()
            localVideoTrack = null
            remoteVideoTrack = null
            videoSource?.dispose()
            videoSource = null
        } catch (_: Throwable) {
        }

        try {
            localAudioTrack?.dispose()
            localAudioTrack = null
            audioSource?.dispose()
            audioSource = null
        } catch (_: Throwable) {
        }

        try {
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
        } catch (_: Throwable) {
        }

        try {
            val am = audioManager
            if (am != null) {
                am.isMicrophoneMute = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = false
                }
                am.mode = AudioManager.MODE_NORMAL
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                } else {
                    @Suppress("DEPRECATION")
                    am.abandonAudioFocus(null)
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun closeSession() {
        closeSessionInternal()
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
