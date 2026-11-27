package com.example.util

import android.net.Uri
import com.example.data.model.FamilyStatusEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class StatusTextStyle {
    NORMAL,
    BOLD,
    ITALIC,
    MONOSPACE
}

enum class StatusTextAlign {
    LEFT,
    CENTER,
    RIGHT
}

data class StatusTextOverlay(
    val id: String,
    val text: String,
    val xNorm: Float = 0.5f,
    val yNorm: Float = 0.45f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val colorArgb: Long = 0xFFFFFFFFL,
    val style: StatusTextStyle = StatusTextStyle.BOLD,
    val align: StatusTextAlign = StatusTextAlign.CENTER,
    val hasBackground: Boolean = false,
    val zOrder: Int = 1
)

enum class StatusStickerKind {
    EMOJI,
    TIME_BADGE,
    DATE_BADGE,
    FAMILY_BADGE
}

data class StatusStickerOverlay(
    val id: String,
    val kind: StatusStickerKind,
    val value: String,
    val xNorm: Float = 0.5f,
    val yNorm: Float = 0.48f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val zOrder: Int = 1
)

data class StatusDrawingPoint(
    val xNorm: Float,
    val yNorm: Float
)

data class StatusDrawingStroke(
    val id: String,
    val points: List<StatusDrawingPoint>,
    val colorArgb: Long = 0xFFFFFFFFL,
    val strokeWidthNorm: Float = 0.011f,
    val isEraser: Boolean = false
)

data class StatusOverlayBundle(
    val texts: List<StatusTextOverlay> = emptyList(),
    val stickers: List<StatusStickerOverlay> = emptyList(),
    val strokes: List<StatusDrawingStroke> = emptyList(),
    val audienceMemberIds: List<String>? = null
) {
    fun isEmptyEdits(): Boolean = texts.isEmpty() && stickers.isEmpty() && strokes.isEmpty()
}

data class StatusStudioDraftItem(
    val id: String,
    val isVideo: Boolean,
    val uri: Uri = Uri.EMPTY,
    val isTextStatus: Boolean = false,
    val backgroundHex: String = "#0E7C66",
    val caption: String = "",
    val overlays: StatusOverlayBundle = StatusOverlayBundle(),
    val musicTrack: StatusMusicTrack? = null,
    val videoPreviewInfo: VideoPreviewInfo? = null,
    val viewportAspectRatio: Float = 0f,
    val useContainWithBlur: Boolean = false
) {
    fun hasEdits(): Boolean =
        caption.isNotBlank() || !overlays.isEmptyEdits() || musicTrack != null
}

object StatusEditorPalette {
    val eightColors: List<Long> = listOf(
        0xFFFFFFFFL, // Blanco
        0xFF111827L, // Negro carbón
        0xFFEF4444L, // Rojo
        0xFFF97316L, // Naranja
        0xFFFACC15L, // Amarillo
        0xFF22C55EL, // Verde
        0xFF2F7FE0L, // Azul
        0xFFA855F7L  // Morado
    )

    val brushThicknessNorms: List<Pair<String, Float>> = listOf(
        "Fino" to 0.006f,
        "Medio" to 0.012f,
        "Grueso" to 0.024f
    )
}

data class EmojiStickerCategory(
    val id: String,
    val title: String,
    val emojis: List<Pair<String, String>> // emoji -> search keywords
)

object StatusStickerCatalog {
    val categories: List<EmojiStickerCategory> = listOf(
        EmojiStickerCategory(
            id = "faces",
            title = "Caritas y gestos",
            emojis = listOf(
                "😀" to "feliz sonrisa cara",
                "😂" to "risa jajaja carcajada",
                "😍" to "enamorado amor ojos corazones",
                "🥰" to "carino tierno amor",
                "😎" to "cool lentes sol genial",
                "🥳" to "fiesta cumpleanos celebrar",
                "😭" to "llorar emocion tristeza",
                "🙏" to "gracias por favor orar",
                "👏" to "aplausos bravo",
                "🙌" to "celebrar manos arriba",
                "👍" to "bien ok pulgar",
                "🫶" to "corazon manos amor"
            )
        ),
        EmojiStickerCategory(
            id = "family",
            title = "Amor y familia",
            emojis = listOf(
                "❤️" to "corazon rojo amor",
                "💖" to "corazon brillo amor",
                "👨‍👩‍👧‍👦" to "familia casa hogar",
                "🏡" to "casa hogar familia",
                "👶" to "bebe familia",
                "🐶" to "perro mascota",
                "🐱" to "gato mascota",
                "🤝" to "union equipo familia",
                "🎉" to "fiesta confeti",
                "🎂" to "pastel cumpleanos",
                "🎈" to "globo fiesta",
                "✨" to "brillo estrellas magia"
            )
        ),
        EmojiStickerCategory(
            id = "nature",
            title = "Comida y día",
            emojis = listOf(
                "☕" to "cafe manana buenos dias",
                "🍕" to "pizza comida cena",
                "🌮" to "taco comida mexico",
                "🍔" to "hamburguesa comida",
                "🍰" to "pastel postre",
                "🌞" to "sol dia caluroso",
                "🌙" to "luna noche descanso",
                "🌈" to "arcoiris lluvia",
                "🌸" to "flor primavera",
                "🔥" to "fuego genial top",
                "⭐" to "estrella favorito",
                "🎵" to "musica cancion nota"
            )
        ),
        EmojiStickerCategory(
            id = "objects",
            title = "Actividades y viaje",
            emojis = listOf(
                "✈️" to "avion viaje vacaciones",
                "🚗" to "carro coche paseo",
                "🏖️" to "playa mar vacaciones",
                "⚽" to "futbol deporte gol",
                "🎮" to "juegos videojuego",
                "📸" to "camara foto recuerdo",
                "🎬" to "video cine pelicula",
                "💯" to "cien perfecto",
                "📍" to "ubicacion lugar aqui",
                "🎁" to "regalo sorpresa"
            )
        )
    )

    fun currentTimeBadgeText(nowMs: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return fmt.format(Date(nowMs))
    }

    fun currentDateBadgeText(nowMs: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("d MMM yyyy", Locale("es", "MX"))
        return fmt.format(Date(nowMs)).replaceFirstChar { it.uppercase() }
    }
}

object StatusEditorCodec {
    fun encodeBundle(bundle: StatusOverlayBundle): String {
        val root = JSONObject()
        if (bundle.texts.isNotEmpty()) {
            val arr = JSONArray()
            bundle.texts.take(15).forEach { t ->
                val o = JSONObject()
                o.put("id", t.id.take(24))
                o.put("tx", t.text.take(240))
                o.put("x", round3(t.xNorm.coerceIn(0f, 1f)))
                o.put("y", round3(t.yNorm.coerceIn(0f, 1f)))
                o.put("s", round3(t.scale.coerceIn(0.35f, 4f)))
                o.put("r", round2(t.rotationDeg))
                o.put("c", t.colorArgb)
                o.put("st", t.style.name)
                o.put("al", t.align.name)
                if (t.hasBackground) o.put("bg", true)
                o.put("z", t.zOrder)
                arr.put(o)
            }
            root.put("t", arr)
        }
        if (bundle.stickers.isNotEmpty()) {
            val arr = JSONArray()
            bundle.stickers.take(20).forEach { st ->
                val o = JSONObject()
                o.put("id", st.id.take(24))
                o.put("k", st.kind.name)
                o.put("v", st.value.take(64))
                o.put("x", round3(st.xNorm.coerceIn(0f, 1f)))
                o.put("y", round3(st.yNorm.coerceIn(0f, 1f)))
                o.put("s", round3(st.scale.coerceIn(0.35f, 4f)))
                o.put("r", round2(st.rotationDeg))
                o.put("z", st.zOrder)
                arr.put(o)
            }
            root.put("k", arr)
        }
        if (bundle.strokes.isNotEmpty()) {
            val arr = JSONArray()
            bundle.strokes.take(40).forEach { stroke ->
                val simplified = simplifyStrokePoints(stroke.points, maxPoints = 64)
                if (simplified.isEmpty()) return@forEach
                val o = JSONObject()
                o.put("id", stroke.id.take(24))
                o.put("c", stroke.colorArgb)
                o.put("w", round4(stroke.strokeWidthNorm.coerceIn(0.002f, 0.06f)))
                if (stroke.isEraser) o.put("e", true)
                val pts = JSONArray()
                simplified.forEach { p ->
                    pts.put(round3(p.xNorm.coerceIn(0f, 1f)))
                    pts.put(round3(p.yNorm.coerceIn(0f, 1f)))
                }
                o.put("p", pts)
                arr.put(o)
            }
            root.put("d", arr)
        }
        bundle.audienceMemberIds?.let { ids ->
            val arr = JSONArray()
            ids.take(30).forEach { arr.put(it) }
            root.put("au", arr)
        }
        return root.toString()
    }

    fun decodeBundle(rawJson: String?): StatusOverlayBundle? {
        if (rawJson.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(rawJson)
            val texts = buildList {
                val arr = root.optJSONArray("t") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val tx = o.optString("tx", "")
                    if (tx.isBlank()) continue
                    add(
                        StatusTextOverlay(
                            id = o.optString("id", "txt_$i"),
                            text = tx,
                            xNorm = o.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                            yNorm = o.optDouble("y", 0.45).toFloat().coerceIn(0f, 1f),
                            scale = o.optDouble("s", 1.0).toFloat().coerceIn(0.35f, 4f),
                            rotationDeg = o.optDouble("r", 0.0).toFloat(),
                            colorArgb = o.optLong("c", 0xFFFFFFFFL),
                            style = runCatching {
                                StatusTextStyle.valueOf(o.optString("st", StatusTextStyle.BOLD.name))
                            }.getOrDefault(StatusTextStyle.BOLD),
                            align = runCatching {
                                StatusTextAlign.valueOf(o.optString("al", StatusTextAlign.CENTER.name))
                            }.getOrDefault(StatusTextAlign.CENTER),
                            hasBackground = o.optBoolean("bg", false),
                            zOrder = o.optInt("z", i + 1)
                        )
                    )
                }
            }
            val stickers = buildList {
                val arr = root.optJSONArray("k") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val v = o.optString("v", "")
                    if (v.isBlank()) continue
                    add(
                        StatusStickerOverlay(
                            id = o.optString("id", "stk_$i"),
                            kind = runCatching {
                                StatusStickerKind.valueOf(o.optString("k", StatusStickerKind.EMOJI.name))
                            }.getOrDefault(StatusStickerKind.EMOJI),
                            value = v,
                            xNorm = o.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                            yNorm = o.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                            scale = o.optDouble("s", 1.0).toFloat().coerceIn(0.35f, 4f),
                            rotationDeg = o.optDouble("r", 0.0).toFloat(),
                            zOrder = o.optInt("z", i + 1)
                        )
                    )
                }
            }
            val strokes = buildList {
                val arr = root.optJSONArray("d") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val pArr = o.optJSONArray("p") ?: continue
                    val pts = buildList {
                        var idx = 0
                        while (idx + 1 < pArr.length()) {
                            add(
                                StatusDrawingPoint(
                                    xNorm = pArr.optDouble(idx, 0.5).toFloat().coerceIn(0f, 1f),
                                    yNorm = pArr.optDouble(idx + 1, 0.5).toFloat().coerceIn(0f, 1f)
                                )
                            )
                            idx += 2
                        }
                    }
                    if (pts.isEmpty()) continue
                    add(
                        StatusDrawingStroke(
                            id = o.optString("id", "drw_$i"),
                            points = pts,
                            colorArgb = o.optLong("c", 0xFFFFFFFFL),
                            strokeWidthNorm = o.optDouble("w", 0.011).toFloat().coerceIn(0.002f, 0.06f),
                            isEraser = o.optBoolean("e", false)
                        )
                    )
                }
            }
            val audience: List<String>? = if (root.has("au")) {
                val arr = root.optJSONArray("au") ?: JSONArray()
                buildList<String> {
                    for (i in 0 until arr.length()) {
                        val id = arr.optString(i, "").trim()
                        if (id.isNotEmpty()) add(id)
                    }
                }
            } else {
                null
            }
            StatusOverlayBundle(
                texts = texts,
                stickers = stickers,
                strokes = strokes,
                audienceMemberIds = audience
            )
        }.getOrNull()
    }

    fun isStatusVisibleToMember(status: FamilyStatusEntity, activeMemberId: String): Boolean {
        if (status.memberId == activeMemberId) return true
        val allowed = status.visibleToMemberIds ?: return true
        return allowed.contains(activeMemberId)
    }

    private fun simplifyStrokePoints(
        points: List<StatusDrawingPoint>,
        maxPoints: Int
    ): List<StatusDrawingPoint> {
        if (points.size <= 2) return points
        val filtered = ArrayList<StatusDrawingPoint>(points.size)
        filtered.add(points.first())
        for (i in 1 until points.lastIndex) {
            val prev = filtered.last()
            val cur = points[i]
            if (abs(cur.xNorm - prev.xNorm) + abs(cur.yNorm - prev.yNorm) >= 0.004f) {
                filtered.add(cur)
            }
        }
        filtered.add(points.last())
        if (filtered.size <= maxPoints) return filtered
        val step = (filtered.size - 1).toFloat() / (maxPoints - 1).toFloat()
        return buildList {
            for (i in 0 until maxPoints - 1) {
                add(filtered[(i * step).roundToInt().coerceIn(0, filtered.lastIndex)])
            }
            add(filtered.last())
        }
    }

    private fun round2(v: Float): Double = (v * 100.0).roundToInt() / 100.0
    private fun round3(v: Float): Double = (v * 1000.0).roundToInt() / 1000.0
    private fun round4(v: Float): Double = (v * 10000.0).roundToInt() / 10000.0
}
