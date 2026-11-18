package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MessageFormatter {
    private val tokenRegex = Regex("""(\*[^*\n]+\*|_[^_\n]+_|~[^~\n]+~)""")

    fun formatRichText(
        raw: String,
        highlightQuery: String = "",
        highlightBackground: Color = Color(0xFFFDE047),
        highlightText: Color = Color(0xFF111827)
    ): AnnotatedString {
        return buildAnnotatedString {
            val plainBuffer = StringBuilder()
            var lastIndex = 0
            for (match in tokenRegex.findAll(raw)) {
                if (match.range.first > lastIndex) {
                    val segment = raw.substring(lastIndex, match.range.first)
                    append(segment)
                    plainBuffer.append(segment)
                }
                val token = match.value
                val inner = token.substring(1, token.length - 1)
                val style = when (token.first()) {
                    '*' -> SpanStyle(fontWeight = FontWeight.Bold)
                    '_' -> SpanStyle(fontStyle = FontStyle.Italic)
                    '~' -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    else -> SpanStyle()
                }
                val start = length
                append(inner)
                plainBuffer.append(inner)
                addStyle(style, start, length)
                lastIndex = match.range.last + 1
            }
            if (lastIndex < raw.length) {
                val tail = raw.substring(lastIndex)
                append(tail)
                plainBuffer.append(tail)
            }

            if (highlightQuery.isNotBlank()) {
                val plain = plainBuffer.toString()
                val query = highlightQuery.trim()
                var searchFrom = 0
                while (true) {
                    val idx = plain.indexOf(query, startIndex = searchFrom, ignoreCase = true)
                    if (idx < 0) break
                    addStyle(
                        SpanStyle(background = highlightBackground, color = highlightText),
                        idx,
                        idx + query.length
                    )
                    searchFrom = idx + query.length
                }
            }
        }
    }

    fun stripMarkdownForPreview(raw: String): String {
        return raw.replace(tokenRegex) { match ->
            match.value.substring(1, match.value.length - 1)
        }
    }
}

object TimeFormatters {
    fun formatHourMinute(timestamp: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

    fun formatDateTime(timestamp: Long): String =
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))

    fun formatShortDayMonth(timestamp: Long, nowMs: Long = System.currentTimeMillis()): String {
        val spanish = Locale.forLanguageTag("es-ES")
        val calNow = Calendar.getInstance().apply { timeInMillis = nowMs }
        val calThen = Calendar.getInstance().apply { timeInMillis = timestamp }
        val monthShort = SimpleDateFormat("MMM", spanish).format(Date(timestamp))
            .lowercase(spanish)
            .replace(".", "")
            .trim()
        val day = calThen.get(Calendar.DAY_OF_MONTH)
        return if (calNow.get(Calendar.YEAR) == calThen.get(Calendar.YEAR)) {
            "$day $monthShort ${formatHourMinute(timestamp)}"
        } else {
            "$day $monthShort ${calThen.get(Calendar.YEAR)}"
        }
    }

    fun formatLastSeen(timestamp: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return "No se ha conectado"
        val diffMs = (nowMs - timestamp).coerceAtLeast(0L)
        if (diffMs < 60_000L) {
            return "Últ. vez hace un momento"
        }
        if (diffMs < 3_600_000L) {
            val mins = (diffMs / 60_000L).coerceAtLeast(1L)
            return "Últ. vez hace $mins min"
        }
        val calNow = Calendar.getInstance().apply { timeInMillis = nowMs }
        val calThen = Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameDay = calNow.get(Calendar.YEAR) == calThen.get(Calendar.YEAR) &&
            calNow.get(Calendar.DAY_OF_YEAR) == calThen.get(Calendar.DAY_OF_YEAR)
        if (sameDay) {
            return "Últ. vez hoy ${formatHourMinute(timestamp)}"
        }
        calNow.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday = calNow.get(Calendar.YEAR) == calThen.get(Calendar.YEAR) &&
            calNow.get(Calendar.DAY_OF_YEAR) == calThen.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) {
            return "Últ. vez ayer ${formatHourMinute(timestamp)}"
        }
        return "Últ. vez ${formatShortDayMonth(timestamp, nowMs)}"
    }

    fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF1A73E8)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}
