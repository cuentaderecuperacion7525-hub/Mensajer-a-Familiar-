package com.example.util

import android.content.Context
import android.provider.Settings
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Pure-Kotlin helpers for detecting emoji-only chat messages (1 to 3 grapheme clusters),
 * mapping emoji graphemes to Google Noto Animated Emoji CDN URLs, and lazily caching
 * animated WebP assets on disk without blocking the UI thread.
 */
object EmojiMessageHelper {

    const val MAX_EMOJI_ONLY_COUNT = 3
    private const val NOTO_ANIMATED_BASE_URL = "https://fonts.gstatic.com/s/e/notoemoji/latest"

    private const val CP_ZWJ = 0x200D
    private const val CP_VS16 = 0xFE0F
    private const val CP_VS15 = 0xFE0E
    private const val CP_KEYCAP = 0x20E3
    private const val CP_TAG_START = 0xE0020
    private const val CP_TAG_END = 0xE007E
    private const val CP_TAG_TERM = 0xE007F
    private const val CP_BLACK_FLAG = 0x1F3F4

    /**
     * Returns the list of 1..3 emoji grapheme clusters if [rawText] contains ONLY
     * 1 to 3 emojis (ignoring whitespace), or an empty list otherwise.
     *
     * Handles:
     * - Standard pictographic emojis and Variation Selector-16 (U+FE0F)
     * - Fitzpatrick skin tone modifiers (U+1F3FB..U+1F3FF)
     * - Regional indicator flag pairs (e.g. 🇲🇽, 🇺🇸)
     * - Keycap sequences (e.g. 1️⃣, #️⃣, *️⃣) while rejecting plain digits/symbols
     * - Subdivision flag tag sequences (e.g. 🏴󠁧󠁢󠁥󠁮󠁧󠁿)
     * - ZWJ compound sequences (e.g. 👨‍👩‍👧‍👦, ❤️‍🔥, 👩🏽‍💻, 🏳️‍🌈)
     */
    fun parseEmojiOnlyMessage(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()

        val codePoints = ArrayList<Int>(rawText.length)
        var i = 0
        while (i < rawText.length) {
            val cp = rawText.codePointAt(i)
            i += Character.charCount(cp)
            codePoints.add(cp)
        }

        val emojis = ArrayList<String>(MAX_EMOJI_ONLY_COUNT)
        var idx = 0
        val n = codePoints.size

        while (idx < n) {
            val cp = codePoints[idx]
            if (isIgnorableWhitespace(cp)) {
                idx++
                continue
            }

            // Text presentation selector explicitly disables emoji presentation.
            if (cp == CP_VS15) return emptyList()

            val nextIdx = consumeEmojiCluster(codePoints, idx)
            if (nextIdx <= idx) {
                // Encountered a non-emoji character (letter, digit, punctuation, etc.).
                return emptyList()
            }

            if (emojis.size >= MAX_EMOJI_ONLY_COUNT) {
                // More than 3 emojis -> render with normal bubble.
                return emptyList()
            }

            val sb = StringBuilder()
            for (k in idx until nextIdx) {
                sb.appendCodePoint(codePoints[k])
            }
            emojis.add(sb.toString())
            idx = nextIdx
        }

        return if (emojis.size in 1..MAX_EMOJI_ONLY_COUNT) emojis else emptyList()
    }

    /**
     * Display size in dp per emoji according to total count in an emoji-only message:
     * 1 emoji = 64dp, 2 emojis = 48dp, 3 emojis = 40dp.
     */
    fun emojiDisplaySizeDp(count: Int): Int = when (count) {
        1 -> 64
        2 -> 48
        else -> 40
    }

    /**
     * Converts a single emoji grapheme into the underscore-delimited lowercase hex codepoint
     * sequence used by Google Noto Animated Emoji CDN (stripping U+FE0F and zero-padding
     * short BMP codepoints like keycap digits to 4 hex characters).
     */
    fun emojiToNotoCodepointSequence(emoji: String): String {
        val parts = ArrayList<String>(4)
        var i = 0
        while (i < emoji.length) {
            val cp = emoji.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == CP_VS16) continue
            parts.add(Integer.toHexString(cp).lowercase(Locale.US).padStart(4, '0'))
        }
        return parts.joinToString("_")
    }

    /**
     * Builds the official Google Noto Animated Emoji WebP URL for [emoji].
     */
    fun buildNotoAnimatedWebpUrl(emoji: String): String {
        val sequence = emojiToNotoCodepointSequence(emoji)
        return "$NOTO_ANIMATED_BASE_URL/$sequence/512.webp"
    }

    /**
     * Checks Android system "Remove animations" / Animator duration scale setting.
     */
    fun isReduceMotionEnabled(context: Context): Boolean {
        return runCatching {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        }.getOrDefault(false)
    }

    private fun isIgnorableWhitespace(cp: Int): Boolean {
        return Character.isWhitespace(cp) || cp == 0x00A0 || cp == 0xFEFF
    }

    /**
     * Consumes a single emoji grapheme cluster starting at [start] and returns the exclusive
     * end index, or [start] if [codePoints[start]] does not start a valid emoji cluster.
     */
    private fun consumeEmojiCluster(codePoints: List<Int>, start: Int): Int {
        val n = codePoints.size
        if (start >= n) return start

        // 1. Regional indicator flag pair (e.g. 🇲🇽 = U+1F1F2 U+1F1FD)
        if (isRegionalIndicator(codePoints[start])) {
            return if (start + 1 < n && isRegionalIndicator(codePoints[start + 1])) {
                start + 2
            } else {
                start
            }
        }

        // 2. Keycap sequence: [0-9#*] + optional U+FE0F + U+20E3
        if (isKeycapBase(codePoints[start])) {
            var pos = start + 1
            if (pos < n && codePoints[pos] == CP_VS16) pos++
            return if (pos < n && codePoints[pos] == CP_KEYCAP) pos + 1 else start
        }

        // 3. Subdivision flag tag sequence: U+1F3F4 + tag_spec+ + U+E007F
        if (codePoints[start] == CP_BLACK_FLAG && start + 2 < n && isTagChar(codePoints[start + 1])) {
            var pos = start + 1
            while (pos < n && isTagChar(codePoints[pos])) {
                pos++
            }
            if (pos < n && codePoints[pos] == CP_TAG_TERM) {
                return pos + 1
            }
        }

        // 4. Standard emoji component, optionally chained with ZWJ (U+200D)
        var pos = consumeSingleEmojiComponent(codePoints, start)
        if (pos <= start) return start

        while (pos < n && codePoints[pos] == CP_ZWJ) {
            val afterZwj = pos + 1
            if (afterZwj >= n) return start // Trailing dangling ZWJ is invalid
            val nextCompEnd = consumeSingleEmojiComponent(codePoints, afterZwj)
            if (nextCompEnd <= afterZwj) return start
            pos = nextCompEnd
        }

        return pos
    }

    /**
     * Consumes one base emoji codepoint + optional U+FE0F and/or Fitzpatrick skin tone modifier.
     */
    private fun consumeSingleEmojiComponent(codePoints: List<Int>, start: Int): Int {
        val n = codePoints.size
        if (start >= n) return start
        val base = codePoints[start]

        val hasNextVs16 = start + 1 < n && codePoints[start + 1] == CP_VS16
        val hasNextSkinTone = start + 1 < n && isSkinToneModifier(codePoints[start + 1])

        // Reject if explicitly followed by text presentation selector U+FE0E
        if (start + 1 < n && codePoints[start + 1] == CP_VS15) return start

        val validBase = isDefaultEmojiPresentation(base) ||
            (isTextDefaultEmojiCapable(base) && (hasNextVs16 || hasNextSkinTone))

        if (!validBase) return start

        var pos = start + 1
        if (pos < n && codePoints[pos] == CP_VS16) pos++
        if (pos < n && isSkinToneModifier(codePoints[pos])) pos++
        if (pos < n && codePoints[pos] == CP_VS16) pos++
        return pos
    }

    private fun isRegionalIndicator(cp: Int): Boolean = cp in 0x1F1E6..0x1F1FF

    private fun isSkinToneModifier(cp: Int): Boolean = cp in 0x1F3FB..0x1F3FF

    private fun isKeycapBase(cp: Int): Boolean =
        cp in '0'.code..'9'.code || cp == '#'.code || cp == '*'.code

    private fun isTagChar(cp: Int): Boolean = cp in CP_TAG_START..CP_TAG_END

    /**
     * Codepoints whose default presentation in Unicode is Emoji (Extended_Pictographic /
     * Emoji_Presentation=Yes), plus common heart/symbol pictographs.
     */
    private fun isDefaultEmojiPresentation(cp: Int): Boolean {
        return when (cp) {
            // Core SMP emoji blocks
            in 0x1F004..0x1F004,
            in 0x1F0CF..0x1F0CF,
            in 0x1F18E..0x1F18E,
            in 0x1F191..0x1F19A,
            in 0x1F201..0x1F201,
            in 0x1F21A..0x1F21A,
            in 0x1F22F..0x1F22F,
            in 0x1F232..0x1F236,
            in 0x1F238..0x1F23A,
            in 0x1F250..0x1F251,
            in 0x1F300..0x1F3FA,
            in 0x1F400..0x1F64F,
            in 0x1F680..0x1F6FF,
            in 0x1F7E0..0x1F7EB,
            in 0x1F7F0..0x1F7F0,
            in 0x1F900..0x1FAFF,
            // Common BMP emojis with Emoji_Presentation=Yes
            0x231A, 0x231B,
            in 0x23E9..0x23EC,
            0x23F0, 0x23F3,
            0x25FD, 0x25FE,
            0x2614, 0x2615,
            in 0x2648..0x2653,
            0x267F, 0x2693, 0x26A1,
            0x26AA, 0x26AB,
            0x26BD, 0x26BE,
            0x26C4, 0x26C5,
            0x26CE, 0x26D4, 0x26EA,
            0x26F2, 0x26F3, 0x26F5, 0x26FA, 0x26FD,
            0x2705, 0x270A, 0x270B, 0x2728,
            0x274C, 0x274E,
            in 0x2753..0x2755,
            0x2757,
            in 0x2795..0x2797,
            0x27B0, 0x27BF,
            0x2B1B, 0x2B1C, 0x2B50, 0x2B55 -> true
            else -> false
        }
    }

    /**
     * Codepoints that are valid emojis when accompanied by U+FE0F (or a skin tone modifier),
     * such as ❤️ (U+2764 U+FE0F), ✌️ (U+270C U+FE0F), ☀️ (U+2600 U+FE0F), ❄️ (U+2744 U+FE0F),
     * ✈️ (U+2708 U+FE0F), ❣️ (U+2763 U+FE0F), ⚧️ (U+26A7 U+FE0F), ♀️/♂️, etc.
     */
    private fun isTextDefaultEmojiCapable(cp: Int): Boolean {
        return when (cp) {
            0x00A9, 0x00AE, 0x203C, 0x2049, 0x2122, 0x2139,
            in 0x2194..0x2199,
            0x21A9, 0x21AA,
            0x2328, 0x23CF,
            in 0x23ED..0x23EF,
            0x23F1, 0x23F2,
            in 0x23F8..0x23FA,
            0x24C2,
            0x25AA, 0x25AB, 0x25B6, 0x25C0,
            in 0x25FB..0x25FC,
            in 0x2600..0x26FF,
            in 0x2700..0x27BF,
            0x2934, 0x2935,
            in 0x2B05..0x2B07,
            0x3030, 0x303D, 0x3297, 0x3299,
            in 0x1F170..0x1F171,
            in 0x1F17E..0x1F17F,
            0x1F202, 0x1F237 -> true
            else -> false
        }
    }
}

/**
 * Tracks which emoji-only messages have already auto-played once during the current app session
 * so scrolling away and back does not replay animations automatically.
 */
object NotoAnimatedEmojiSession {
    private val playedMessageIds = ConcurrentHashMap.newKeySet<String>()

    /**
     * Marks [messageId] as played in this session and returns `true` ONLY the first time
     * it is called for that [messageId].
     */
    fun consumeShouldAutoPlay(messageId: String): Boolean {
        return playedMessageIds.add(messageId)
    }

    fun hasPlayed(messageId: String): Boolean = playedMessageIds.contains(messageId)

    fun clearForTest() {
        playedMessageIds.clear()
    }
}

sealed class NotoEmojiLoadResult {
    data class Ready(val file: File) : NotoEmojiLoadResult()
    data object NotAnimated : NotoEmojiLoadResult()
    data object NetworkError : NotoEmojiLoadResult()
}

/**
 * Lazily fetches and caches Google Noto Animated Emoji WebP files on disk (`cacheDir/noto_animated_emoji`).
 * Runs entirely off the main thread (`Dispatchers.IO`) and negatively caches 404 responses in memory.
 */
object NotoAnimatedEmojiLoader {
    private const val CACHE_DIR_NAME = "noto_animated_emoji"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val notFoundSequences = ConcurrentHashMap.newKeySet<String>()

    suspend fun loadAnimatedWebp(context: Context, emoji: String): NotoEmojiLoadResult =
        withContext(Dispatchers.IO) {
            val sequence = EmojiMessageHelper.emojiToNotoCodepointSequence(emoji)
            if (sequence.isBlank()) return@withContext NotoEmojiLoadResult.NotAnimated

            if (notFoundSequences.contains(sequence)) {
                return@withContext NotoEmojiLoadResult.NotAnimated
            }

            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(cacheDir, "$sequence.webp")
            if (targetFile.exists() && targetFile.length() >= 12L) {
                return@withContext NotoEmojiLoadResult.Ready(targetFile)
            }

            val url = EmojiMessageHelper.buildNotoAnimatedWebpUrl(emoji)
            try {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 404 || response.code == 410 -> {
                            notFoundSequences.add(sequence)
                            NotoEmojiLoadResult.NotAnimated
                        }
                        response.isSuccessful -> {
                            val bytes = response.body?.bytes()
                            if (bytes != null && isValidWebpHeader(bytes)) {
                                val tmpFile = File(cacheDir, "$sequence.${Thread.currentThread().id}.tmp")
                                tmpFile.writeBytes(bytes)
                                if (!tmpFile.renameTo(targetFile)) {
                                    tmpFile.copyTo(targetFile, overwrite = true)
                                    tmpFile.delete()
                                }
                                NotoEmojiLoadResult.Ready(targetFile)
                            } else {
                                NotoEmojiLoadResult.NetworkError
                            }
                        }
                        else -> NotoEmojiLoadResult.NetworkError
                    }
                }
            } catch (_: Throwable) {
                NotoEmojiLoadResult.NetworkError
            }
        }

    private fun isValidWebpHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        return bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
    }

    fun clearMemoryCacheForTest() {
        notFoundSequences.clear()
    }
}
