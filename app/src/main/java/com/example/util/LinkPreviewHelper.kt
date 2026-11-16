package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class LinkPreviewData(
    val url: String,
    val host: String,
    val title: String,
    val description: String
)

object LinkPreviewHelper {
    private val URL_REGEX = Regex("""(https?://[^\s<>"]+)""", RegexOption.IGNORE_CASE)
    private val cache = ConcurrentHashMap<String, LinkPreviewData>()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun extractFirstUrl(text: String): String? {
        if (!text.contains("http", ignoreCase = true)) return null
        val match = URL_REGEX.find(text)?.value ?: return null
        return match.trimEnd('.', ',', ';', ')', ']', '!', '?')
    }

    fun getCachedPreview(url: String): LinkPreviewData? = cache[url]

    suspend fun fetchPreview(url: String): LinkPreviewData? = withContext(Dispatchers.IO) {
        cache[url]?.let { return@withContext it }
        val host = runCatching {
            URI(url).host?.removePrefix("www.")?.ifBlank { null }
        }.getOrNull() ?: "Enlace web"

        val fallback = LinkPreviewData(
            url = url,
            host = host,
            title = host.replaceFirstChar { it.uppercase() },
            description = url.take(100)
        )

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) FamiChat/2.4")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    cache[url] = fallback
                    return@withContext fallback
                }
                val contentType = response.header("Content-Type").orEmpty()
                if (contentType.isNotEmpty() && !contentType.contains("text/html", ignoreCase = true)) {
                    cache[url] = fallback
                    return@withContext fallback
                }
                val bodySnippet = response.body?.source()?.let { source ->
                    source.request(48_000)
                    source.buffer.clone().readString(
                         minOf(source.buffer.size, 48_000L),
                        Charsets.UTF_8
                    )
                }.orEmpty()

                val ogTitle = extractMetaContent(bodySnippet, "og:title")
                    ?: extractMetaContent(bodySnippet, "twitter:title")
                    ?: extractHtmlTitle(bodySnippet)
                val ogDesc = extractMetaContent(bodySnippet, "og:description")
                    ?: extractMetaContent(bodySnippet, "description")
                    ?: extractMetaContent(bodySnippet, "twitter:description")

                val preview = LinkPreviewData(
                    url = url,
                    host = host,
                    title = decodeHtmlEntities(ogTitle ?: host).trim().take(90).ifBlank { host },
                    description = decodeHtmlEntities(ogDesc ?: url).trim().take(140)
                )
                cache[url] = preview
                preview
            }
        } catch (_: Exception) {
            cache[url] = fallback
            fallback
        }
    }

    private fun extractMetaContent(html: String, key: String): String? {
        val pattern1 = Regex(
            """<meta[^>]+(?:property|name)\s*=\s*["']${Regex.escape(key)}["'][^>]+content\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        pattern1.find(html)?.groupValues?.getOrNull(1)?.let { return it }

        val pattern2 = Regex(
            """<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+(?:property|name)\s*=\s*["']${Regex.escape(key)}["']""",
            RegexOption.IGNORE_CASE
        )
        return pattern2.find(html)?.groupValues?.getOrNull(1)
    }

    private fun extractHtmlTitle(html: String): String? {
        val regex = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return regex.find(html)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")
    }

    private fun decodeHtmlEntities(raw: String): String {
        return raw
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }
}
