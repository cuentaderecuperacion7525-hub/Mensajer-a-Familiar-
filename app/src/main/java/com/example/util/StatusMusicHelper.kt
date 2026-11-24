package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class StatusMusicTrack(
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String = "",
    val previewUrl: String,
    val durationSec: Int = 30,
    val muteOriginalVideo: Boolean = false
)

enum class MusicBrowseCategory(val label: String, val defaultQuery: String) {
    SUGGESTIONS("Sugerencias", "exitos latinos 2025"),
    MOOD("Estado de ánimo", "musica alegre romantica"),
    GENRE("Género", "regional mexicano pop cumbia"),
    FAMILY("Cumpleaños y familia", "las mananitas cumpleanos"),
    FAVORITES("Favoritas", "")
}

object StatusMusicCodec {
    private const val PREFIX = "fmusic:"
    private const val SEP = "|fmusic:"

    fun encodeTrack(track: StatusMusicTrack): String {
        val obj = JSONObject()
        obj.put("id", track.trackId.take(40))
        obj.put("t", track.title.take(80))
        obj.put("a", track.artist.take(70))
        obj.put("img", track.artworkUrl.take(260))
        obj.put("url", track.previewUrl.take(360))
        obj.put("d", track.durationSec.coerceIn(5, 90))
        if (track.muteOriginalVideo) {
            obj.put("mv", true)
        }
        return obj.toString()
    }

    fun decodeTrack(rawJson: String?): StatusMusicTrack? {
        if (rawJson.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(rawJson)
            val url = obj.optString("url", "").trim()
            val title = obj.optString("t", "").trim()
            if (url.isBlank() || title.isBlank()) return null
            StatusMusicTrack(
                trackId = obj.optString("id", "").trim().ifBlank { url.hashCode().toString() },
                title = title,
                artist = obj.optString("a", "").trim().ifBlank { "Música" },
                artworkUrl = obj.optString("img", "").trim(),
                previewUrl = url,
                durationSec = obj.optInt("d", 30).coerceIn(5, 90),
                muteOriginalVideo = obj.optBoolean("mv", false)
            )
        }.getOrNull()
    }

    fun encodeTrackList(tracks: List<StatusMusicTrack>): String {
        val arr = JSONArray()
        tracks.take(40).forEach { track ->
            runCatching { JSONObject(encodeTrack(track)) }.getOrNull()?.let { arr.put(it) }
        }
        return arr.toString()
    }

    fun decodeTrackList(rawJson: String?): List<StatusMusicTrack> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(rawJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    decodeTrack(obj.toString())?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Packs music metadata into existing Firestore-approved status fields (`mediaData` / `videoThumbnailData`)
     * so no extra top-level Firestore document keys are required.
     */
    fun packStatusFields(
        mediaType: String,
        rawMediaData: String?,
        rawVideoThumbnailData: String?,
        musicTrack: StatusMusicTrack?
    ): Pair<String?, String?> {
        if (musicTrack == null) return rawMediaData to rawVideoThumbnailData
        val encoded = encodeTrack(musicTrack)
        return when (mediaType.uppercase()) {
            "VIDEO" -> {
                if (rawMediaData != null && rawMediaData.startsWith("chunked:")) {
                    "${rawMediaData.substringBefore(SEP)}$SEP$encoded" to rawVideoThumbnailData
                } else if (!rawVideoThumbnailData.isNullOrBlank()) {
                    rawMediaData to "${rawVideoThumbnailData.substringBefore(SEP)}$SEP$encoded"
                } else {
                    rawMediaData to "$PREFIX$encoded"
                }
            }
            else -> {
                rawMediaData to "$PREFIX$encoded"
            }
        }
    }

    /**
     * Extracts clean `mediaData`, clean `videoThumbnailData`, and optional `StatusMusicTrack`.
     */
    fun unpackStatusFields(
        rawMediaData: String?,
        rawVideoThumbnailData: String?
    ): Triple<String?, String?, StatusMusicTrack?> {
        var cleanMedia = rawMediaData
        var cleanThumb = rawVideoThumbnailData
        var extracted: StatusMusicTrack? = null

        if (rawMediaData != null && rawMediaData.contains(SEP)) {
            cleanMedia = rawMediaData.substringBefore(SEP)
            extracted = decodeTrack(rawMediaData.substringAfter(SEP))
        }

        if (rawVideoThumbnailData != null) {
            when {
                rawVideoThumbnailData.startsWith(PREFIX) -> {
                    cleanThumb = null
                    if (extracted == null) {
                        extracted = decodeTrack(rawVideoThumbnailData.removePrefix(PREFIX))
                    }
                }
                rawVideoThumbnailData.contains(SEP) -> {
                    cleanThumb = rawVideoThumbnailData.substringBefore(SEP)
                    if (extracted == null) {
                        extracted = decodeTrack(rawVideoThumbnailData.substringAfter(SEP))
                    }
                }
            }
        }

        return Triple(cleanMedia, cleanThumb, extracted)
    }
}

object StatusMusicCatalog {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val queryCache = ConcurrentHashMap<String, List<StatusMusicTrack>>()

    suspend fun searchTracks(query: String): Result<List<StatusMusicTrack>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().ifBlank { MusicBrowseCategory.SUGGESTIONS.defaultQuery }
        val cacheKey = cleanQuery.lowercase()
        queryCache[cacheKey]?.let { cached ->
            if (cached.isNotEmpty()) return@withContext Result.success(cached)
        }

        runCatching {
            val encodedTerm = URLEncoder.encode(cleanQuery, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encodedTerm&media=music&entity=song&limit=25&country=MX"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("No se pudo consultar el catálogo (${response.code}).")
                }
                val bodyStr = response.body?.string().orEmpty()
                val parsed = parseItunesSearchJson(bodyStr)
                if (parsed.isNotEmpty()) {
                    queryCache[cacheKey] = parsed
                }
                parsed
            }
        }
    }

    internal fun parseItunesSearchJson(jsonText: String): List<StatusMusicTrack> {
        if (jsonText.isBlank()) return emptyList()
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("results") ?: return emptyList()
        val seenIds = HashSet<String>()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val title = item.optString("trackName", "").trim()
                val artist = item.optString("artistName", "").trim()
                val rawPreview = item.optString("previewUrl", "").trim()
                if (title.isBlank() || rawPreview.isBlank()) continue
                val previewUrl = if (rawPreview.startsWith("http://")) {
                    "https://${rawPreview.removePrefix("http://")}"
                } else {
                    rawPreview
                }
                if (!previewUrl.startsWith("https://")) continue

                val rawArtwork = item.optString("artworkUrl100", "").trim()
                val artworkUrl = when {
                    rawArtwork.startsWith("http://") -> "https://${rawArtwork.removePrefix("http://")}"
                    rawArtwork.startsWith("https://") -> rawArtwork
                    else -> ""
                }
                val idVal = item.optLong("trackId", 0L)
                val trackId = if (idVal > 0L) idVal.toString() else previewUrl.hashCode().toString()
                if (!seenIds.add(trackId)) continue

                add(
                    StatusMusicTrack(
                        trackId = trackId,
                        title = title,
                        artist = artist.ifBlank { "Artista" },
                        artworkUrl = artworkUrl,
                        previewUrl = previewUrl,
                        durationSec = 30
                    )
                )
            }
        }
    }
}
