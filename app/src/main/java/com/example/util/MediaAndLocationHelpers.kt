package com.example.util

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Address
import android.location.Geocoder
import android.media.AudioManager
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.LruCache
import androidx.core.content.FileProvider
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
    private const val MAX_DIM_PX = 1280
    private const val MAX_BYTES = 420 * 1024

    private val byteArrayCache = object : LruCache<String, ByteArray>(36) {}
    private val bitmapCache = object : LruCache<String, Bitmap>(24) {}

    private fun cacheKeyFor(mediaData: String): String =
        "${mediaData.length}_${mediaData.hashCode()}_${mediaData.takeLast(24)}"

    suspend fun compressToDataUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var rotationDegrees = 0f
            var flipHorizontal = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { exifStream ->
                        val exif = ExifInterface(exifStream)
                        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> rotationDegrees = 90f
                            ExifInterface.ORIENTATION_ROTATE_180 -> rotationDegrees = 180f
                            ExifInterface.ORIENTATION_ROTATE_270 -> rotationDegrees = 270f
                            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipHorizontal = true
                            ExifInterface.ORIENTATION_TRANSPOSE -> {
                                rotationDegrees = 90f
                                flipHorizontal = true
                            }
                            ExifInterface.ORIENTATION_TRANSVERSE -> {
                                rotationDegrees = 270f
                                flipHorizontal = true
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }

            var sampleSize = 1
            val maxSide = max(bounds.outWidth, bounds.outHeight)
            while (maxSide / sampleSize > MAX_DIM_PX * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawDecoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return@withContext null

            val oriented = if (rotationDegrees != 0f || flipHorizontal) {
                val matrix = Matrix().apply {
                    if (flipHorizontal) preScale(-1f, 1f)
                    if (rotationDegrees != 0f) postRotate(rotationDegrees)
                }
                Bitmap.createBitmap(rawDecoded, 0, 0, rawDecoded.width, rawDecoded.height, matrix, true)
                    .also { if (it != rawDecoded) rawDecoded.recycle() }
            } else {
                rawDecoded
            }

            val scale = MAX_DIM_PX.toFloat() / max(oriented.width, oriented.height).coerceAtLeast(1)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    oriented,
                    (oriented.width * scale).toInt().coerceAtLeast(1),
                    (oriented.height * scale).toInt().coerceAtLeast(1),
                    true
                ).also { if (it != oriented) oriented.recycle() }
            } else {
                oriented
            }

            var quality = 82
            val baos = ByteArrayOutputStream()
            do {
                baos.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                quality -= 10
            } while (baos.size() > MAX_BYTES && quality >= 32)

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
            val key = cacheKeyFor(mediaData)
            synchronized(byteArrayCache) {
                byteArrayCache.get(key)?.let { return it }
            }
            val commaIdx = mediaData.indexOf(',')
            if (commaIdx <= 0) return null
            val b64 = mediaData.substring(commaIdx + 1)
            return try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                synchronized(byteArrayCache) {
                    byteArrayCache.put(key, bytes)
                }
                bytes
            } catch (_: Exception) {
                null
            }
        }
        return mediaData
    }

    fun decodeToBitmap(mediaData: String?): Bitmap? {
        if (mediaData.isNullOrBlank()) return null
        if (!mediaData.startsWith("data:image")) return null
        val key = cacheKeyFor(mediaData)
        synchronized(bitmapCache) {
            val cached = bitmapCache.get(key)
            if (cached != null && !cached.isRecycled) return cached
        }
        val bytes = resolveCoilModel(mediaData) as? ByteArray ?: return null
        return try {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            synchronized(bitmapCache) {
                bitmapCache.put(key, bmp)
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }
}

class VoiceAudioHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartMs: Long = 0L

    private var player: MediaPlayer? = null
    private var activeRingtone: Ringtone? = null
    private var currentSpeed: Float = 1.0f

    fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0.75f, 2.0f)
        val mp = player ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (mp.isPlaying) {
                    mp.playbackParams = mp.playbackParams.setSpeed(currentSpeed)
                }
            } catch (_: Exception) {
            }
        }
    }

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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && currentSpeed != 1.0f) {
                    try {
                        playbackParams = playbackParams.setSpeed(currentSpeed)
                    } catch (_: Exception) {
                    }
                }
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

object CameraCaptureHelper {
    fun createTempPhotoUri(context: Context): Uri? {
        return try {
            val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
            val file = File(dir, "cam_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    fun createTempVideoUri(context: Context): Uri? {
        return try {
            val dir = File(context.cacheDir, "videos_cache").apply { mkdirs() }
            val file = File(dir, "vid_cam_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.mp4")
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }
}

data class EncodedDocumentPayload(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dataUri: String
)

object DocumentMediaHelper {
    const val MAX_DOCUMENT_BYTES = 5 * 1024 * 1024 // Up to 5 MB with chunked cloud transfer
    const val INLINE_MAX_CHARS = 420_000
    const val CHUNK_SIZE_CHARS = 380_000

    fun splitIntoChunks(dataUri: String): List<String> = dataUri.chunked(CHUNK_SIZE_CHARS)

    fun getCachedDocumentFile(context: Context, messageId: String, fileName: String?): File {
        val cleanName = (fileName ?: "documento_$messageId")
            .replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            .ifBlank { "documento_$messageId.bin" }
        val dir = File(context.cacheDir, "docs_cache").apply { mkdirs() }
        return File(dir, "${messageId.takeLast(6)}_$cleanName")
    }

    suspend fun readAndEncodeDocument(context: Context, uri: Uri): Result<EncodedDocumentPayload> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri)?.ifBlank { null } ?: "application/octet-stream"
                var displayName = "documento_${System.currentTimeMillis()}"
                var reportedSize = -1L

                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                                displayName = cursor.getString(nameIdx).ifBlank { displayName }
                            }
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                                reportedSize = cursor.getLong(sizeIdx)
                            }
                        }
                    }

                if (reportedSize > MAX_DOCUMENT_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException("El archivo supera el límite de 5 MB (${formatFileSize(reportedSize)}).")
                    )
                }

                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(16384)
                    var total = 0
                    while (true) {
                        val read = input.read(chunk)
                        if (read == -1) break
                        total += read
                        if (total > MAX_DOCUMENT_BYTES) {
                            return@withContext Result.failure(
                                IllegalArgumentException("El archivo supera el límite de 5 MB.")
                            )
                        }
                        buffer.write(chunk, 0, read)
                    }
                    buffer.toByteArray()
                } ?: return@withContext Result.failure(IllegalStateException("No se pudo leer el archivo seleccionado."))

                if (bytes.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("El archivo seleccionado está vacío."))
                }

                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val dataUri = "data:$mimeType;base64,$encoded"
                Result.success(
                    EncodedDocumentPayload(
                        fileName = displayName.take(160),
                        mimeType = mimeType.take(120),
                        sizeBytes = bytes.size.toLong(),
                        dataUri = dataUri
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun openDocumentExternally(
        context: Context,
        messageId: String,
        fileName: String?,
        mimeType: String?,
        mediaData: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val outFile = getCachedDocumentFile(context, messageId, fileName)
            if (!outFile.exists() || outFile.length() == 0L) {
                if (mediaData.isNullOrBlank()) {
                    return@withContext Result.failure(IllegalStateException("Archivo no disponible."))
                }
                val commaIdx = mediaData.indexOf(',')
                val b64 = if (commaIdx > 0) mediaData.substring(commaIdx + 1) else mediaData
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                outFile.writeBytes(bytes)
            }
            launchDocumentFileViewer(context, outFile, mimeType)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun launchDocumentFileViewer(context: Context, outFile: File, mimeType: String?) {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
        val resolvedMime = mimeType?.ifBlank { null } ?: "*/*"
        withContext(Dispatchers.Main) {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, resolvedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(viewIntent)
            } catch (_: Exception) {
                val shareFallback = Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = resolvedMime
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    outFile.name
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(shareFallback)
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "Archivo"
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

data class EncodedVideoPayload(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationSec: Int,
    val thumbnailDataUri: String?,
    val dataUri: String,
    val chunks: List<String>
)

data class VideoPreviewInfo(
    val thumbnailBitmap: Bitmap?,
    val durationSec: Int,
    val sizeBytes: Long
)

object VideoMediaHelper {
    const val MAX_VIDEO_BYTES = 5 * 1024 * 1024 // Up to 5 MB for short chat/status videos

    fun getCachedVideoFile(context: Context, messageId: String, fileName: String?): File {
        val cleanName = (fileName ?: "video_$messageId.mp4")
            .replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            .let { if (it.contains('.')) it else "$it.mp4" }
        val dir = File(context.filesDir, "videos_cache").apply { mkdirs() }
        return File(dir, "${messageId.takeLast(6)}_$cleanName")
    }

    fun findExistingCachedVideoFile(context: Context, messageId: String, fileName: String?): File? {
        val primary = getCachedVideoFile(context, messageId, fileName)
        if (primary.exists() && primary.length() > 0L) return primary
        val legacyDir = File(context.cacheDir, "videos_cache")
        val legacy = File(legacyDir, primary.name)
        return if (legacy.exists() && legacy.length() > 0L) legacy else null
    }

    fun deleteCachedVideoFile(context: Context, messageId: String, fileName: String?) {
        runCatching {
            val primary = getCachedVideoFile(context, messageId, fileName)
            if (primary.exists()) primary.delete()
            val legacy = File(File(context.cacheDir, "videos_cache"), primary.name)
            if (legacy.exists()) legacy.delete()
        }
    }

    fun cleanupExpiredStatusVideos(context: Context, maxAgeMs: Long = 26L * 60L * 60L * 1000L) {
        val now = System.currentTimeMillis()
        listOf(
            File(context.filesDir, "videos_cache"),
            File(context.cacheDir, "videos_cache")
        ).forEach { dir ->
            if (!dir.exists()) return@forEach
            dir.listFiles()?.forEach { f ->
                if (f.isFile && (f.name.contains("_estado_") || f.name.endsWith(".tmp")) &&
                    (now - f.lastModified() > maxAgeMs)
                ) {
                    runCatching { f.delete() }
                }
            }
        }
    }

    suspend fun extractVideoPreview(context: Context, uri: Uri): VideoPreviewInfo = withContext(Dispatchers.IO) {
        var sizeBytes = 0L
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) sizeBytes = cursor.getLong(idx)
                }
            }
        } catch (_: Exception) {
        }
        var durationSec = 1
        var thumb: Bitmap? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 1000L
            durationSec = (durMs / 1000L).toInt().coerceAtLeast(1)
            val frame = retriever.getFrameAtTime(200_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime()
            if (frame != null) {
                val maxSide = max(frame.width, frame.height).coerceAtLeast(1)
                val scale = (640f / maxSide).coerceAtMost(1f)
                thumb = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        frame,
                        (frame.width * scale).toInt().coerceAtLeast(1),
                        (frame.height * scale).toInt().coerceAtLeast(1),
                        true
                    ).also { if (it != frame) frame.recycle() }
                } else {
                    frame
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
        VideoPreviewInfo(thumbnailBitmap = thumb, durationSec = durationSec, sizeBytes = sizeBytes)
    }

    suspend fun readAndEncodeVideo(context: Context, uri: Uri): Result<EncodedVideoPayload> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri)?.ifBlank { null } ?: "video/mp4"
                var displayName = "video_${System.currentTimeMillis()}.mp4"
                var reportedSize = -1L

                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                                displayName = cursor.getString(nameIdx).ifBlank { displayName }
                            }
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                                reportedSize = cursor.getLong(sizeIdx)
                            }
                        }
                    }

                if (reportedSize > MAX_VIDEO_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException("El video supera el límite de 5 MB para envío por chat (${DocumentMediaHelper.formatFileSize(reportedSize)}). Elige un video más corto.")
                    )
                }

                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(16384)
                    var total = 0
                    while (true) {
                        val read = input.read(chunk)
                        if (read == -1) break
                        total += read
                        if (total > MAX_VIDEO_BYTES) {
                            return@withContext Result.failure(
                                IllegalArgumentException("El video supera el límite de 5 MB para envío por chat.")
                            )
                        }
                        buffer.write(chunk, 0, read)
                    }
                    buffer.toByteArray()
                } ?: return@withContext Result.failure(IllegalStateException("No se pudo leer el video seleccionado."))

                if (bytes.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("El video seleccionado está vacío."))
                }

                var durationSec = 1
                var thumbnailDataUri: String? = null
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 1000L
                    durationSec = (durMs / 1000L).toInt().coerceAtLeast(1)
                    val frame = retriever.getFrameAtTime(200_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime()
                    if (frame != null) {
                        val maxSide = max(frame.width, frame.height).coerceAtLeast(1)
                        val scale = (320f / maxSide).coerceAtMost(1f)
                        val scaled = if (scale < 1f) {
                            Bitmap.createScaledBitmap(
                                frame,
                                (frame.width * scale).toInt().coerceAtLeast(1),
                                (frame.height * scale).toInt().coerceAtLeast(1),
                                true
                            )
                        } else frame
                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                        if (scaled != frame) scaled.recycle()
                        frame.recycle()
                        val b64Thumb = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        thumbnailDataUri = "data:image/jpeg;base64,$b64Thumb"
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }

                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val dataUri = "data:$mimeType;base64,$encoded"
                val chunks = if (dataUri.length > DocumentMediaHelper.INLINE_MAX_CHARS) {
                    DocumentMediaHelper.splitIntoChunks(dataUri)
                } else {
                    emptyList()
                }
                Result.success(
                    EncodedVideoPayload(
                        fileName = displayName.take(160),
                        mimeType = mimeType.take(120),
                        sizeBytes = bytes.size.toLong(),
                        durationSec = durationSec,
                        thumbnailDataUri = thumbnailDataUri,
                        dataUri = dataUri,
                        chunks = chunks
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun writeAssembledVideoToCache(
        context: Context,
        messageId: String,
        fileName: String?,
        fullDataUri: String
    ): Result<File> = withContext(Dispatchers.IO) {
        var tmpFile: File? = null
        try {
            val outFile = getCachedVideoFile(context, messageId, fileName)
            val commaIdx = fullDataUri.indexOf(',')
            val b64 = if (commaIdx > 0) fullDataUri.substring(commaIdx + 1) else fullDataUri
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            if (bytes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("El archivo de video descargado está vacío."))
            }
            val tmp = File(outFile.parentFile, "${outFile.name}.tmp")
            tmpFile = tmp
            tmp.writeBytes(bytes)
            if (outFile.exists()) outFile.delete()
            if (!tmp.renameTo(outFile)) {
                tmp.copyTo(outFile, overwrite = true)
                tmp.delete()
            }
            tmpFile = null
            Result.success(outFile)
        } catch (e: Exception) {
            runCatching { tmpFile?.delete() }
            Result.failure(e)
        }
    }

    suspend fun saveVideoToGallery(context: Context, videoFile: File): Result<String> =
        withContext(Dispatchers.IO) {
            if (!videoFile.exists()) {
                return@withContext Result.failure(IllegalStateException("El archivo de video no está disponible."))
            }
            try {
                val fileName = "FamiChat_${System.currentTimeMillis()}.mp4"
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/FamiChat")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }
                val itemUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(IllegalStateException("No se pudo guardar el video en la galería."))
                resolver.openOutputStream(itemUri)?.use { out ->
                    videoFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val doneValues = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    resolver.update(itemUri, doneValues, null, null)
                }
                Result.success("Video guardado en Galería (Movies/FamiChat)")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun shareVideoExternally(context: Context, videoFile: File, caption: String = ""): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (!videoFile.exists()) {
                return@withContext Result.failure(IllegalStateException("El archivo de video no está disponible."))
            }
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    videoFile
                )
                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        if (caption.isNotBlank()) {
                            putExtra(Intent.EXTRA_TEXT, caption)
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(shareIntent, "Compartir video familiar")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

object ImageGalleryAndShareHelper {
    private fun decodeBytes(mediaData: String): ByteArray? {
        return try {
            val commaIdx = mediaData.indexOf(',')
            val b64 = if (commaIdx > 0) mediaData.substring(commaIdx + 1) else mediaData
            Base64.decode(b64, Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveImageToGallery(context: Context, mediaData: String): Result<String> =
        withContext(Dispatchers.IO) {
            val bytes = decodeBytes(mediaData)
                ?: return@withContext Result.failure(IllegalArgumentException("No se pudo decodificar la imagen."))
            try {
                val fileName = "FamiChat_${System.currentTimeMillis()}.jpg"
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FamiChat")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val itemUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(IllegalStateException("No se pudo guardar en la galería."))

                resolver.openOutputStream(itemUri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val doneValues = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(itemUri, doneValues, null, null)
                }
                Result.success("Foto guardada en Galería (FamiChat)")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun shareImageExternally(context: Context, mediaData: String, caption: String = ""): Result<Unit> =
        withContext(Dispatchers.IO) {
            val bytes = decodeBytes(mediaData)
                ?: return@withContext Result.failure(IllegalArgumentException("No se pudo preparar la imagen."))
            try {
                val dir = File(context.cacheDir, "shared_media").apply { mkdirs() }
                val file = File(dir, "compartir_${System.currentTimeMillis()}.jpg")
                file.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        if (caption.isNotBlank()) {
                            putExtra(Intent.EXTRA_TEXT, caption)
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(shareIntent, "Compartir foto familiar")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

