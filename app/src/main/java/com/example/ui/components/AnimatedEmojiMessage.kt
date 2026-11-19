package com.example.ui.components

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.ChatMessageEntity
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.MessageType
import com.example.ui.theme.LocalDarkTheme
import com.example.util.EmojiMessageHelper
import com.example.util.MessageFormatter
import com.example.util.NotoAnimatedEmojiLoader
import com.example.util.NotoAnimatedEmojiSession
import com.example.util.NotoEmojiLoadResult
import com.example.util.TimeFormatters
import com.example.util.parseHexColor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Optional override for system reduce-motion setting (used in UI tests).
 */
val LocalReduceMotionOverride = staticCompositionLocalOf<Boolean?> { null }

/**
 * Optional override for loading Noto animated WebP assets (used in UI tests).
 */
val LocalNotoEmojiLoaderOverride =
    staticCompositionLocalOf<(suspend (Context, String) -> NotoEmojiLoadResult)?> { null }

private val EmojiTimestampLight = Color(0xFF5B6270)
private val EmojiTimestampDark = Color(0xFFC4CAD6)

/**
 * Bubble-less presentation for emoji-only chat messages (1 to 3 emojis).
 * Draws emojis directly on the chat wallpaper with Google Noto Animated Emoji WebP support,
 * bounce entrance fallback when an emoji has no animated asset, silent static fallback offline,
 * and strict respect for Android's reduce-motion setting.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmojiOnlyMessageContent(
    message: ChatMessageEntity,
    emojis: List<String>,
    isMine: Boolean,
    isGroupChat: Boolean,
    sender: FamilyMemberEntity?,
    repliedMessage: ChatMessageEntity?,
    repliedSenderName: String?,
    isMultiSelectMode: Boolean,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
    onReplyClick: (String) -> Unit
) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val reduceMotionOverride = LocalReduceMotionOverride.current
    val reduceMotion = remember(context, reduceMotionOverride) {
        reduceMotionOverride ?: EmojiMessageHelper.isReduceMotionEnabled(context)
    }

    val shouldAutoPlayOnce = remember(message.id) {
        NotoAnimatedEmojiSession.consumeShouldAutoPlay(message.id)
    }
    var replayTrigger by remember(message.id) { mutableIntStateOf(0) }

    val emojiSizeDp = EmojiMessageHelper.emojiDisplaySizeDp(emojis.size).dp
    val contentAlign = if (isMine) Alignment.End else Alignment.Start
    val metaPillBg = if (dark) Color.Black.copy(alpha = 0.36f) else Color.White.copy(alpha = 0.65f)
    val metaTextColor = if (dark) EmojiTimestampDark else EmojiTimestampLight

    Column(
        horizontalAlignment = contentAlign,
        modifier = Modifier
            .widthIn(max = 310.dp)
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) {
                        onToggleSelect()
                    } else if (!reduceMotion) {
                        replayTrigger++
                    }
                },
                onLongClick = onLongPress
            )
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .testTag("emoji_only_message_${message.id}")
    ) {
        if (!message.forwardedFromName.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = metaPillBg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.padding(bottom = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Forward,
                        contentDescription = null,
                        tint = metaTextColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Reenviado de ${message.forwardedFromName}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic
                        ),
                        color = metaTextColor
                    )
                }
            }
        }

        if (isGroupChat && !isMine && sender != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = metaPillBg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.padding(bottom = 3.dp)
            ) {
                Text(
                    text = "${sender.avatarEmoji} ${sender.name}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = parseHexColor(sender.avatarColorHex),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }

        if (repliedMessage != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (dark) Color(0xFF1F2430).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.88f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 260.dp)
                    .padding(bottom = 5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onReplyClick(repliedMessage.id) }
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = repliedSenderName ?: "Mensaje",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val snippet = when (repliedMessage.type) {
                            MessageType.IMAGE -> "📷 Foto"
                            MessageType.VIDEO -> "🎬 Video"
                            MessageType.VOICE -> "🎤 Nota de voz"
                            MessageType.LOCATION -> "📍 ${repliedMessage.locationName ?: "Ubicación"}"
                            MessageType.DOCUMENT -> "📄 ${repliedMessage.fileName ?: "Documento"}"
                            MessageType.POLL -> "📊 ${repliedMessage.pollQuestion ?: "Encuesta"}"
                            MessageType.DELETED -> "Mensaje eliminado"
                            MessageType.TEXT -> MessageFormatter.stripMarkdownForPreview(repliedMessage.text)
                        }
                        Text(
                            text = snippet,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = 2.dp)
                .testTag("emoji_only_row_${message.id}")
        ) {
            emojis.forEachIndexed { index, emoji ->
                AnimatedNotoEmojiGlyph(
                    messageId = message.id,
                    emoji = emoji,
                    sizeDp = emojiSizeDp,
                    reduceMotion = reduceMotion,
                    shouldAutoPlay = shouldAutoPlayOnce,
                    replayTrigger = replayTrigger,
                    modifier = Modifier.testTag("emoji_only_glyph_${message.id}_$index")
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(metaPillBg)
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .testTag("emoji_only_meta_${message.id}")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (message.isEdited) {
                    Text(
                        text = "editado",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic
                        ),
                        color = metaTextColor
                    )
                }
                Text(
                    text = TimeFormatters.formatHourMinute(message.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = metaTextColor
                )
                if (isMine) {
                    MessageStatusIcon(status = message.status)
                }
            }
        }
    }
}

private class WebpPlaybackTracker {
    var startedInitial: Boolean = false
    var lastHandledReplayTrigger: Int = 0
}

@Composable
private fun AnimatedNotoEmojiGlyph(
    messageId: String,
    emoji: String,
    sizeDp: Dp,
    reduceMotion: Boolean,
    shouldAutoPlay: Boolean,
    replayTrigger: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customLoader = LocalNotoEmojiLoaderOverride.current

    var loadResult by remember(messageId, emoji, reduceMotion) {
        mutableStateOf<NotoEmojiLoadResult?>(null)
    }
    var decodedDrawable by remember(messageId, emoji, reduceMotion) {
        mutableStateOf<Drawable?>(null)
    }
    val playbackTracker = remember(messageId, emoji, reduceMotion) { WebpPlaybackTracker() }

    // Bounce scale animatable used ONLY when an emoji has no animated WebP (NotAnimated / API < 28)
    val bounceScale = remember(messageId, emoji) { Animatable(1f) }
    var hasPlayedInitialBounce by remember(messageId, emoji) { mutableStateOf(false) }

    val supportsAnimatedImageDrawable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    // Lazy background fetch + decode (never runs when reduceMotion is active)
    LaunchedEffect(messageId, emoji, reduceMotion, supportsAnimatedImageDrawable) {
        if (reduceMotion) {
            loadResult = null
            decodedDrawable = null
            return@LaunchedEffect
        }
        if (!supportsAnimatedImageDrawable) {
            loadResult = NotoEmojiLoadResult.NotAnimated
            decodedDrawable = null
            return@LaunchedEffect
        }

        val fetched = if (customLoader != null) {
            customLoader(context, emoji)
        } else {
            NotoAnimatedEmojiLoader.loadAnimatedWebp(context, emoji)
        }
        loadResult = fetched

        if (fetched is NotoEmojiLoadResult.Ready) {
            val drawable = decodeAnimatedWebp(fetched.file)
            if (drawable != null) {
                decodedDrawable = drawable
            } else {
                // Corrupted or undecodable asset -> remove file & show static native emoji cleanly
                runCatching { fetched.file.delete() }
                loadResult = NotoEmojiLoadResult.NetworkError
                decodedDrawable = null
            }
        } else {
            decodedDrawable = null
        }
    }

    // Bounce entrance & tap-replay for emojis without Noto animated version (or API < 28)
    LaunchedEffect(loadResult, reduceMotion, replayTrigger) {
        if (reduceMotion) {
            bounceScale.snapTo(1f)
            return@LaunchedEffect
        }
        if (loadResult is NotoEmojiLoadResult.NotAnimated) {
            val isTapReplay = replayTrigger > 0
            val shouldRunInitial = !hasPlayedInitialBounce && shouldAutoPlay
            if (isTapReplay || shouldRunInitial) {
                hasPlayedInitialBounce = true
                bounceScale.snapTo(0.6f)
                bounceScale.animateTo(
                    targetValue = 1.1f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                )
                bounceScale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    // Native font size tuned to fit cleanly inside 64dp / 48dp / 40dp bounds without clipping
    val nativeFontSp = when {
        sizeDp >= 64.dp -> 50.sp
        sizeDp >= 48.dp -> 38.sp
        else -> 31.sp
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp)
            .semantics { contentDescription = emoji }
    ) {
        val activeDrawable = if (!reduceMotion) decodedDrawable else null
        if (activeDrawable != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        isClickable = false
                        isLongClickable = false
                        isFocusable = false
                        importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                },
                update = { imageView ->
                    if (imageView.drawable !== activeDrawable) {
                        imageView.setImageDrawable(activeDrawable)
                    }
                    if (activeDrawable is AnimatedImageDrawable) {
                        activeDrawable.repeatCount = 0
                        if (!playbackTracker.startedInitial) {
                            playbackTracker.startedInitial = true
                            if (shouldAutoPlay) {
                                activeDrawable.start()
                            }
                        }
                        if (replayTrigger > playbackTracker.lastHandledReplayTrigger) {
                            playbackTracker.lastHandledReplayTrigger = replayTrigger
                            activeDrawable.stop()
                            activeDrawable.start()
                        }
                    }
                },
                modifier = Modifier.size(sizeDp)
            )
        } else {
            Text(
                text = emoji,
                fontSize = nativeFontSp,
                lineHeight = nativeFontSp * 1.1f,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displayMedium.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                modifier = Modifier.graphicsLayer {
                    val s = if (reduceMotion) 1f else bounceScale.value
                    scaleX = s
                    scaleY = s
                }
            )
        }
    }
}

private suspend fun decodeAnimatedWebp(file: File): Drawable? = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@withContext null
    runCatching {
        val source = ImageDecoder.createSource(file)
        ImageDecoder.decodeDrawable(source).also { drawable ->
            if (drawable is AnimatedImageDrawable) {
                drawable.repeatCount = 0
            }
        }
    }.getOrNull()
}
