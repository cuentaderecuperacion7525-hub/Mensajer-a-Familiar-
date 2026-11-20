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
import org.webrtc.CameraEnumerationAndroid
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
import org.webrtc.RendererCommon
import org.webrtc.RtpParameters
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

data class CaptureProfile(
    val width: Int,
    val height: Int,
    val fps: Int
)

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
    private var audioSender: RtpSender? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoSender: RtpSender? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var boundLocalRenderer: SurfaceViewRenderer? = null
    private var boundRemoteRenderer: SurfaceViewRenderer? = null

    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false
    private var lastAppliedAnswerSdp: String? = null
    private var lastAppliedOfferSdp: String? = null

    private var isCallerRole: Boolean = false
    private var currentIsVideoCall: Boolean = false
    private var onOfferCreatedCallback: ((String) -> Unit)? = null
    private var iceRestartCount: Int = 0
    private var lastIceRestartAtMs: Long = 0L
    private var pendingDisconnectRestartRunnable: Runnable? = null

    private val _hasRemoteVideo = MutableStateFlow(false)
    val hasRemoteVideo: StateFlow<Boolean> = _hasRemoteVideo.asStateFlow()

    private val _iceStateText = MutableStateFlow("Conectando red...")
    val iceStateText: StateFlow<String> = _iceStateText.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    companion object {
        @Volatile
        private var factoryInitialized = false

        const val TARGET_VIDEO_WIDTH = 1280
        const val TARGET_VIDEO_HEIGHT = 720
        const val TARGET_VIDEO_FPS = 30
        const val VIDEO_MAX_BITRATE_BPS = 2_000_000
        const val VIDEO_MIN_BITRATE_BPS = 250_000
        const val AUDIO_MAX_BITRATE_BPS = 48_000

        private const val MAX_ICE_RESTARTS = 3
        private const val ICE_RESTART_COOLDOWN_MS = 8_000L
        private const val DISCONNECT_RECOVERY_DELAY_MS = 3_500L

        private val STUN_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302",
            "stun:openrelay.metered.ca:80"
        )

        /**
         * Selects the best camera capture profile up to 720p @ 30fps,
         * falling back gracefully on older front cameras.
         */
        fun selectOptimalCaptureProfile(
            supported: List<Pair<Int, Int>>,
            targetWidth: Int = TARGET_VIDEO_WIDTH,
            targetHeight: Int = TARGET_VIDEO_HEIGHT,
            targetFps: Int = TARGET_VIDEO_FPS
        ): CaptureProfile {
            if (supported.isEmpty()) {
                return CaptureProfile(targetWidth, targetHeight, targetFps)
            }
            val exactHd = supported.firstOrNull { (w, h) ->
                (w == targetWidth && h == targetHeight) || (w == targetHeight && h == targetWidth)
            }
            if (exactHd != null) {
                return CaptureProfile(exactHd.first, exactHd.second, targetFps)
            }
            val capped = supported.filter { (w, h) ->
                maxOf(w, h) <= maxOf(targetWidth, targetHeight) &&
                    minOf(w, h) <= minOf(targetWidth, targetHeight) &&
                    minOf(w, h) >= 360
            }
            val best = capped.maxByOrNull { (w, h) ->
                val pixels = w.toLong() * h.toLong()
                val aspectPenalty = kotlin.math.abs((w.toDouble() / h.coerceAtLeast(1)) - (16.0 / 9.0))
                pixels - (aspectPenalty * 120_000).toLong()
            } ?: supported.minByOrNull { (w, h) ->
                kotlin.math.abs(w - targetWidth) + kotlin.math.abs(h - targetHeight)
            } ?: (640 to 480)
            return CaptureProfile(best.first, best.second, targetFps)
        }

        /**
         * Tunes Opus voice parameters in SDP for WhatsApp-like clarity and resilience:
         * - useinbandfec=1 (Forward Error Correction for 4G/5G packet loss recovery)
         * - usedtx=1 (Discontinuous Transmission to save bandwidth for video during silence)
         * - maxaveragebitrate=48000 (48 kbps wideband HD voice)
         * - minptime=10 (low latency)
         */
        fun tuneSdpForHdQuality(sdp: String): String {
            if (sdp.isBlank()) return sdp
            val lineBreak = if (sdp.contains("\r\n")) "\r\n" else "\n"
            val lines = sdp.split("\r\n", "\n").toMutableList()
            val opusRtpmapRegex = Regex("^a=rtpmap:(\\d+) opus/48000(?:/\\d+)?$", RegexOption.IGNORE_CASE)
            var opusPayload: String? = null
            var opusRtpmapIndex = -1
            for (i in lines.indices) {
                val match = opusRtpmapRegex.matchEntire(lines[i].trim())
                if (match != null) {
                    opusPayload = match.groupValues[1]
                    opusRtpmapIndex = i
                    break
                }
            }
            if (opusPayload == null) return sdp

            val desiredParams = linkedMapOf(
                "minptime" to "10",
                "useinbandfec" to "1",
                "usedtx" to "1",
                "maxaveragebitrate" to AUDIO_MAX_BITRATE_BPS.toString()
            )
            val fmtpPrefix = "a=fmtp:$opusPayload "
            val fmtpIndex = lines.indexOfFirst { it.startsWith(fmtpPrefix) }
            if (fmtpIndex >= 0) {
                val existingRaw = lines[fmtpIndex].removePrefix(fmtpPrefix).trim()
                val existingMap = linkedMapOf<String, String>()
                existingRaw.split(";").forEach { part ->
                    val trimmed = part.trim()
                    if (trimmed.isNotEmpty()) {
                        val eq = trimmed.indexOf('=')
                        if (eq > 0) {
                            existingMap[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim()
                        } else {
                            existingMap[trimmed] = ""
                        }
                    }
                }
                desiredParams.forEach { (k, v) -> existingMap[k] = v }
                val rebuilt = existingMap.entries.joinToString(";") { (k, v) ->
                    if (v.isEmpty()) k else "$k=$v"
                }
                lines[fmtpIndex] = "$fmtpPrefix$rebuilt"
            } else if (opusRtpmapIndex >= 0) {
                val built = desiredParams.entries.joinToString(";") { (k, v) -> "$k=$v" }
                lines.add(opusRtpmapIndex + 1, "$fmtpPrefix$built")
            }
            return lines.joinToString(lineBreak)
        }
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
                val targetDevice = if (enabled) {
                    devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                } else {
                    // Automatically prefer Bluetooth / wired headset when speaker is off, else earpiece
                    devices.firstOrNull {
                        it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    } ?: devices.firstOrNull {
                        it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
                    } ?: devices.firstOrNull {
                        it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                }
                if (targetDevice != null) {
                    am.setCommunicationDevice(targetDevice)
                } else {
                    am.isSpeakerphoneOn = enabled
                }
            } else {
                if (!enabled && am.isBluetoothScoAvailableOffCall) {
                    runCatching { am.startBluetoothSco() }
                    runCatching { am.isBluetoothScoOn = true }
                } else {
                    runCatching { am.isBluetoothScoOn = false }
                    runCatching { am.stopBluetoothSco() }
                }
                am.isSpeakerphoneOn = enabled
            }
        } catch (_: Throwable) {
        }
    }

    private fun applyAdaptiveMediaBitrates() {
        try {
            videoSender?.let { sender ->
                val params = sender.parameters
                params.degradationPreference = RtpParameters.DegradationPreference.BALANCED
                params.encodings?.forEach { enc ->
                    enc.maxBitrateBps = VIDEO_MAX_BITRATE_BPS
                    enc.minBitrateBps = VIDEO_MIN_BITRATE_BPS
                    enc.maxFramerate = TARGET_VIDEO_FPS
                }
                sender.parameters = params
            }
        } catch (_: Throwable) {
        }
        try {
            audioSender?.let { sender ->
                val params = sender.parameters
                params.encodings?.forEach { enc ->
                    enc.maxBitrateBps = AUDIO_MAX_BITRATE_BPS
                }
                sender.parameters = params
            }
        } catch (_: Throwable) {
        }
    }

    private fun cancelPendingDisconnectRecovery() {
        pendingDisconnectRestartRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingDisconnectRestartRunnable = null
    }

    private fun scheduleIceRestartIfNeeded(immediate: Boolean) {
        if (!isCallerRole) return
        if (iceRestartCount >= MAX_ICE_RESTARTS) return
        val now = System.currentTimeMillis()
        if (now - lastIceRestartAtMs < ICE_RESTART_COOLDOWN_MS) return

        cancelPendingDisconnectRecovery()
        val runnable = Runnable {
            val pc = peerConnection ?: return@Runnable
            val state = pc.iceConnectionState()
            if (state != PeerConnection.IceConnectionState.DISCONNECTED &&
                state != PeerConnection.IceConnectionState.FAILED
            ) {
                return@Runnable
            }
            if (iceRestartCount >= MAX_ICE_RESTARTS) return@Runnable
            iceRestartCount++
            lastIceRestartAtMs = System.currentTimeMillis()
            _iceStateText.value = "Reconectando red..."
            try {
                val restartConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(
                        MediaConstraints.KeyValuePair(
                            "OfferToReceiveVideo",
                            if (currentIsVideoCall) "true" else "false"
                        )
                    )
                }
                pc.createOffer(
                    object : SimpleSdpObserver() {
                        override fun onCreateSuccess(desc: SessionDescription?) {
                            if (desc == null) return
                            val tuned = SessionDescription(desc.type, tuneSdpForHdQuality(desc.description))
                            pc.setLocalDescription(
                                object : SimpleSdpObserver() {
                                    override fun onSetSuccess() {
                                        onOfferCreatedCallback?.invoke(tuned.description)
                                    }
                                },
                                tuned
                            )
                        }
                    },
                    restartConstraints
                )
            } catch (_: Throwable) {
            }
        }
        pendingDisconnectRestartRunnable = runnable
        if (immediate) {
            mainHandler.post(runnable)
        } else {
            mainHandler.postDelayed(runnable, DISCONNECT_RECOVERY_DELAY_MS)
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

        currentIsVideoCall = isVideoCall
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
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 4
        }

        val pc = factory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    when (state) {
                        PeerConnection.IceConnectionState.CHECKING -> {
                            _iceStateText.value = "Estableciendo audio/video HD..."
                        }
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            cancelPendingDisconnectRecovery()
                            iceRestartCount = 0
                            applyAdaptiveMediaBitrates()
                            _iceStateText.value = if (currentIsVideoCall) "Video HD en vivo" else "Voz HD en vivo"
                            onIceConnected()
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            _iceStateText.value = "Reconectando señal..."
                            scheduleIceRestartIfNeeded(immediate = false)
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            _iceStateText.value = "Reintentando conexión..."
                            scheduleIceRestartIfNeeded(immediate = true)
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

        // Audio track (crystal-clear voice with AEC/NS/AGC)
        try {
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
            }
            val aSource = factory.createAudioSource(audioConstraints)
            audioSource = aSource
            val aTrack = factory.createAudioTrack("FAMICHAT_AUDIO_0", aSource)
            aTrack.setEnabled(true)
            localAudioTrack = aTrack
            audioSender = pc.addTrack(aTrack, listOf("FAMICHAT_STREAM"))
        } catch (_: Throwable) {
        }

        // Video track (HD 720p @ 30fps with automatic fallback for older cameras)
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

                        val supportedFormats: List<CameraEnumerationAndroid.CaptureFormat> = runCatching {
                            enumerator.getSupportedFormats(frontName)
                        }.getOrNull().orEmpty()
                        val profile = selectOptimalCaptureProfile(
                            supported = supportedFormats.map { it.width to it.height },
                            targetWidth = TARGET_VIDEO_WIDTH,
                            targetHeight = TARGET_VIDEO_HEIGHT,
                            targetFps = TARGET_VIDEO_FPS
                        )
                        val maxSupportedFps = supportedFormats
                            .firstOrNull { it.width == profile.width && it.height == profile.height }
                            ?.framerate?.max?.let { it / 1000 }
                            ?.coerceIn(15, TARGET_VIDEO_FPS)
                            ?: TARGET_VIDEO_FPS

                        runCatching {
                            capturer.startCapture(profile.width, profile.height, maxSupportedFps)
                        }.onFailure {
                            runCatching { capturer.startCapture(640, 480, 24) }
                        }

                        val vTrack = factory.createVideoTrack("FAMICHAT_VIDEO_0", vSource)
                        vTrack.setEnabled(true)
                        localVideoTrack = vTrack
                        videoSender = pc.addTrack(vTrack, listOf("FAMICHAT_STREAM"))

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

        applyAdaptiveMediaBitrates()
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
            isCallerRole = true
            onOfferCreatedCallback = onOfferCreated
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
                        val tuned = SessionDescription(desc.type, tuneSdpForHdQuality(desc.description))
                        pc.setLocalDescription(
                            object : SimpleSdpObserver() {
                                override fun onSetSuccess() {
                                    onOfferCreated(tuned.description)
                                }
                            },
                            tuned
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
            isCallerRole = false
            lastAppliedOfferSdp = offerSdp
            val remoteOffer = SessionDescription(SessionDescription.Type.OFFER, tuneSdpForHdQuality(offerSdp))
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
                                    val tuned = SessionDescription(desc.type, tuneSdpForHdQuality(desc.description))
                                    pc.setLocalDescription(
                                        object : SimpleSdpObserver() {
                                            override fun onSetSuccess() {
                                                applyAdaptiveMediaBitrates()
                                                onAnswerCreated(tuned.description)
                                            }
                                        },
                                        tuned
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

    /**
     * Handles a renegotiation / ICE-restart SDP offer sent by the caller when the network changes.
     */
    fun handleRemoteIceRestartOffer(
        offerSdp: String,
        onAnswerCreated: (String) -> Unit
    ) {
        if (offerSdp.isBlank() || offerSdp == lastAppliedOfferSdp) return
        val pc = peerConnection ?: return
        try {
            lastAppliedOfferSdp = offerSdp
            val remoteOffer = SessionDescription(SessionDescription.Type.OFFER, tuneSdpForHdQuality(offerSdp))
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
                                    if (currentIsVideoCall) "true" else "false"
                                )
                            )
                        }
                        pc.createAnswer(
                            object : SimpleSdpObserver() {
                                override fun onCreateSuccess(desc: SessionDescription?) {
                                    if (desc == null) return
                                    val tuned = SessionDescription(desc.type, tuneSdpForHdQuality(desc.description))
                                    pc.setLocalDescription(
                                        object : SimpleSdpObserver() {
                                            override fun onSetSuccess() {
                                                applyAdaptiveMediaBitrates()
                                                onAnswerCreated(tuned.description)
                                            }
                                        },
                                        tuned
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
        if (answerSdp.isBlank() || answerSdp == lastAppliedAnswerSdp) return
        val pc = peerConnection ?: return
        try {
            lastAppliedAnswerSdp = answerSdp
            val remoteAnswer = SessionDescription(SessionDescription.Type.ANSWER, tuneSdpForHdQuality(answerSdp))
            pc.setRemoteDescription(
                object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        remoteDescriptionSet = true
                        drainPendingCandidates()
                        applyAdaptiveMediaBitrates()
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
                    renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
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
                    renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
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
        cancelPendingDisconnectRecovery()
        isCallerRole = false
        currentIsVideoCall = false
        onOfferCreatedCallback = null
        iceRestartCount = 0
        lastIceRestartAtMs = 0L
        remoteDescriptionSet = false
        lastAppliedAnswerSdp = null
        lastAppliedOfferSdp = null
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
            videoSender = null
            remoteVideoTrack = null
            videoSource?.dispose()
            videoSource = null
        } catch (_: Throwable) {
        }

        try {
            localAudioTrack?.dispose()
            localAudioTrack = null
            audioSender = null
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
                    runCatching {
                        am.isBluetoothScoOn = false
                        am.stopBluetoothSco()
                    }
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
