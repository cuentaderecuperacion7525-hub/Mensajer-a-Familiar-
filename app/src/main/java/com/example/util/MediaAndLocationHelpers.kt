package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Address
import android.location.Geocoder
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.max

data class GpsLocationResult(
    val title: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

object ImageCompressionHelper {
    private const val MAX_DIM_PX = 960
    private const val MAX_BYTES = 320 * 1024

    suspend fun compressToDataUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sampleSize = 1
            val maxSide = max(bounds.outWidth, bounds.outHeight)
            while (maxSide / sampleSize > MAX_DIM_PX * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return@withContext null

            val scale = MAX_DIM_PX.toFloat() / max(decoded.width, decoded.height).coerceAtLeast(1)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                ).also { if (it != decoded) decoded.recycle() }
            } else {
                decoded
            }

            var quality = 78
            val baos = ByteArrayOutputStream()
            do {
                baos.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                quality -= 12
            } while (baos.size() > MAX_BYTES && quality >= 30)

            scaled.recycle()
            val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$encoded"
        } catch (_: Exception) {
            null
        }
    }

    fun resolveCoilModel(mediaData: String?): Any? {
        if (mediaData.isNullOrBlank()) return null
        if (mediaData.startsWith("data:image")) {
            val commaIdx = mediaData.indexOf(',')
            if (commaIdx <= 0) return null
            val b64 = mediaData.substring(commaIdx + 1)
            return try {
                Base64.decode(b64, Base64.DEFAULT)
            } catch (_: Exception) {
                null
            }
        }
        return mediaData
    }
}

class VoiceAudioHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartMs: Long = 0L

    private var player: MediaPlayer? = null
    private var activeRingtone: Ringtone? = null

    fun startRecording(): Boolean {
        stopRecordingCleanup()
        return try {
            val dir = File(context.cacheDir, "voice_notes").apply { mkdirs() }
            val outFile = File(dir, "rec_${UUID.randomUUID()}.m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(32_000)
            mr.setAudioSamplingRate(16_000)
            mr.setOutputFile(outFile.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            recordingFile = outFile
            recordingStartMs = System.currentTimeMillis()
            true
        } catch (_: Exception) {
            stopRecordingCleanup()
            false
        }
    }

    fun readNormalizedAmplitude(): Float {
        return try {
            val maxAmp = recorder?.maxAmplitude ?: 0
            (maxAmp / 16000f).coerceIn(0.18f, 1.0f)
        } catch (_: Exception) {
            0.2f
        }
    }

    suspend fun stopAndEncodeToDataUri(): Pair<Int, String>? = withContext(Dispatchers.IO) {
        val file = recordingFile
        val elapsedSec = ((System.currentTimeMillis() - recordingStartMs) / 1000L).toInt().coerceAtLeast(1)
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        recordingFile = null

        if (file == null || !file.exists() || file.length() <= 0L) return@withContext null
        return@withContext try {
            val bytes = file.readBytes()
            if (bytes.size > 480 * 1024) {
                file.delete()
                null
            } else {
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                file.delete()
                elapsedSec to "data:audio/mp4;base64,$b64"
            }
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    fun cancelRecording() {
        stopRecordingCleanup()
    }

    private fun stopRecordingCleanup() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        recordingFile?.delete()
        recordingFile = null
    }

    suspend fun prepareAndStartPlayback(
        messageId: String,
        mediaData: String,
        onPrepared: (durationMs: Int) -> Unit,
        onCompleted: () -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        stopPlayback()
        return try {
            val playFile = withContext(Dispatchers.IO) {
                if (mediaData.startsWith("data:audio")) {
                    val commaIdx = mediaData.indexOf(',')
                    if (commaIdx <= 0) return@withContext null
                    val b64 = mediaData.substring(commaIdx + 1)
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    val cacheDir = File(context.cacheDir, "voice_cache").apply { mkdirs() }
                    val target = File(cacheDir, "play_${messageId}.m4a")
                    if (!target.exists() || target.length() != bytes.size.toLong()) {
                        target.writeBytes(bytes)
                    }
                    target
                } else {
                    val uri = Uri.parse(mediaData)
                    if (uri.scheme == "file" && uri.path != null) File(uri.path!!) else null
                }
            }
            if (playFile == null || !playFile.exists()) {
                onError("Audio no disponible en este dispositivo")
                return false
            }
            val mp = MediaPlayer().apply {
                setDataSource(playFile.absolutePath)
                setOnCompletionListener {
                    stopPlayback()
                    onCompleted()
                }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    onError("No se pudo reproducir el audio")
                    true
                }
                prepare()
                start()
            }
            player = mp
            onPrepared(mp.duration.coerceAtLeast(1))
            true
        } catch (e: Exception) {
            stopPlayback()
            onError(e.localizedMessage ?: "Error al reproducir nota de voz")
            false
        }
    }

    fun playbackProgress(): Float {
        val mp = player ?: return 0f
        return try {
            if (!mp.isPlaying || mp.duration <= 0) 0f
            else (mp.currentPosition.toFloat() / mp.duration.toFloat()).coerceIn(0f, 1f)
        } catch (_: Exception) {
            0f
        }
    }

    fun stopPlayback() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    fun startRingtone(isIncoming: Boolean) {
        stopRingtone()
        try {
            val type = if (isIncoming) RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION
            val uri = RingtoneManager.getDefaultUri(type) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = isIncoming
            }
            ringtone?.play()
            activeRingtone = ringtone
        } catch (_: Exception) {
        }
    }

    fun stopRingtone() {
        try {
            activeRingtone?.stop()
        } catch (_: Exception) {
        }
        activeRingtone = null
    }

    fun applyAudioCallHardware(isMuted: Boolean, isSpeakerOn: Boolean) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.isMicrophoneMute = isMuted
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = isSpeakerOn
        } catch (_: Exception) {
        }
    }

    fun releaseAll() {
        stopRecordingCleanup()
        stopPlayback()
        stopRingtone()
    }
}

object LocationHelper {
    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentGpsLocation(context: Context): Result<GpsLocationResult> = withContext(Dispatchers.IO) {
        try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val location = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                ?: client.lastLocation.await()
                ?: return@withContext Result.failure(IllegalStateException("Activa el GPS del dispositivo e intenta de nuevo."))

            val resolvedAddress = reverseGeocode(context, location.latitude, location.longitude)
            val coordLabel = String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude)
            val displayAddr = resolvedAddress ?: "Coordenadas GPS: $coordLabel"
            Result.success(
                GpsLocationResult(
                    title = "Ubicación GPS actual",
                    address = displayAddr,
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        if (cont.isActive) cont.resume(formatAddress(addresses.firstOrNull()))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val list = geocoder.getFromLocation(lat, lng, 1)
                formatAddress(list?.firstOrNull())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatAddress(addr: Address?): String? {
        if (addr == null) return null
        val line = addr.getAddressLine(0)
        if (!line.isNullOrBlank()) return line
        return listOfNotNull(addr.thoroughfare, addr.subLocality, addr.locality, addr.countryName)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { null }
    }

    fun openInMaps(context: Context, name: String, address: String, lat: Double?, lng: Double?) {
        val uri = if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) {
            Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(name)})")
        } else {
            val query = address.ifBlank { name }
            Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
