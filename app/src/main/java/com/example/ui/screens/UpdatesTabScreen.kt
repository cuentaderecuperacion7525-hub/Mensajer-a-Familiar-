package com.example.ui.screens

import android.graphics.PathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.FamilyNoteEntity
import com.example.data.model.FamilyStatusEntity
import com.example.data.model.JsonHelpers
import com.example.ui.components.MemberAvatar
import com.example.ui.components.MemberStatusGroup
import com.example.ui.theme.InterFontFamily
import com.example.util.ImageCompressionHelper
import com.example.util.TimeFormatters
import com.example.util.parseHexColor

private val UpdatesBrandBlue = Color(0xFF1A5FD4)
private val UpdatesInkPrimary = Color(0xFF1B1F2A)
private val UpdatesInkSecondary = Color(0xFF5B6270)
private val UpdatesViewedRingGray = Color(0xFFC9CED8)
private val UpdatesNoticeCardBgLight = Color(0xFFF5F7FB)

/**
 * Returns true if any other family member has at least one active status not yet viewed
 * by [activeMemberId]. Uses the exact same viewedByMemberIdsJson data already loaded in memory.
 */
fun hasUnviewedFamilyStatuses(
    statuses: List<FamilyStatusEntity>,
    activeMemberId: String
): Boolean {
    return statuses.any { st ->
        st.memberId != activeMemberId &&
            !JsonHelpers.decodeStringList(st.viewedByMemberIdsJson).contains(activeMemberId)
    }
}

/**
 * Custom bottom navigation icon for "Novedades": a circle with a dotted/dashed ring
 * and a crisp central dot.
 */
@Composable
fun UpdatesDottedRingIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    selected: Boolean = false
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val strokePx = (if (selected) 2.2.dp else 1.9.dp).toPx()
        val inset = strokePx / 2f + 2.dp.toPx()
        val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
        val topLeft = Offset(inset, inset)
        val segments = 8
        val step = 360f / segments
        val sweep = step * 0.56f
        for (i in 0 until segments) {
            drawArc(
                color = tint,
                startAngle = -90f + i * step + (step - sweep) / 2f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        drawCircle(
            color = tint,
            radius = (if (selected) 3.1.dp else 2.6.dp).toPx(),
            center = center
        )
    }
}

@Composable
fun UpdatesTabScreen(
    activeMember: FamilyMemberEntity,
    allMembers: List<FamilyMemberEntity>,
    statuses: List<FamilyStatusEntity>,
    notes: List<FamilyNoteEntity>,
    isDarkTheme: Boolean,
    mediaTransferProgress: Map<String, Float> = emptyMap(),
    onAddStatus: () -> Unit,
    onOpenMemberStatuses: (MemberStatusGroup) -> Unit
) {
    val membersMap = remember(allMembers) { allMembers.associateBy { it.id } }

    val visibleStatuses = remember(statuses, activeMember.id) {
        statuses.filter { com.example.util.StatusEditorCodec.isStatusVisibleToMember(it, activeMember.id) }
    }

    val myStatuses = remember(visibleStatuses, activeMember.id) {
        visibleStatuses
            .filter { it.memberId == activeMember.id }
            .sortedBy { it.createdAt }
    }

    val otherGroups = remember(visibleStatuses, membersMap, activeMember.id) {
        visibleStatuses
            .filter { it.memberId != activeMember.id }
            .groupBy { it.memberId }
            .mapNotNull { (memberId, list) ->
                val author = membersMap[memberId] ?: return@mapNotNull null
                val sorted = list.sortedBy { it.createdAt }
                val hasUnviewed = sorted.any { st ->
                    !JsonHelpers.decodeStringList(st.viewedByMemberIdsJson).contains(activeMember.id)
                }
                MemberStatusGroup(author, sorted, hasUnviewed)
            }
    }

    val recentGroups = remember(otherGroups) {
        otherGroups
            .filter { it.hasUnviewed }
            .sortedByDescending { it.items.last().createdAt }
    }

    val viewedGroups = remember(otherGroups) {
        otherGroups
            .filter { !it.hasUnviewed }
            .sortedByDescending { it.items.last().createdAt }
    }

    val primaryText = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else UpdatesInkPrimary
    val secondaryText = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else UpdatesInkSecondary
    val brandBlue = if (isDarkTheme) MaterialTheme.colorScheme.primary else UpdatesBrandBlue
    val viewedRingColor = if (isDarkTheme) MaterialTheme.colorScheme.outline else UpdatesViewedRingGray
    val statusUploadEntry = mediaTransferProgress.entries.firstOrNull { it.key.startsWith("st_") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("updates_tab_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Optional pinned family notices card ONLY when published family notes exist
        if (notes.isNotEmpty()) {
            item(key = "family_notices_pinned_card") {
                FamilyNoticesPinnedCard(
                    notes = notes,
                    membersMap = membersMap,
                    isDarkTheme = isDarkTheme,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    brandBlue = brandBlue
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // Video status upload progress indicator (shared with Chats tab)
        if (statusUploadEntry != null) {
            item(key = "status_upload_progress") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎬 Subiendo estado en video...",
                            style = MaterialTheme.typography.labelSmall,
                            color = brandBlue
                        )
                        val pctInt = (statusUploadEntry.value * 100f).toInt().coerceIn(5, 100)
                        Text(
                            text = "$pctInt%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = brandBlue
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { statusUploadEntry.value.coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Section: Estados
        item(key = "section_estados_header") {
            Text(
                text = "Estados",
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = secondaryText,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }

        // My Status / Add Status row
        item(key = "my_status_row") {
            if (myStatuses.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 60.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onAddStatus() }
                        .padding(vertical = 6.dp)
                        .testTag("updates_my_status_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        MemberAvatar(
                            emoji = activeMember.avatarEmoji,
                            colorHex = activeMember.avatarColorHex,
                            photoData = activeMember.avatarPhotoData,
                            nameForInitials = activeMember.name,
                            size = 52.dp,
                            cornerRadius = 17.dp
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(21.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE3E6EC), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Añadir estado",
                                tint = UpdatesBrandBlue,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Añadir estado",
                            fontFamily = InterFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tu día en una foto",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                val latestMine = myStatuses.last()
                val myGroup = MemberStatusGroup(
                    member = activeMember,
                    items = myStatuses,
                    hasUnviewed = false
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 62.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenMemberStatuses(myGroup) }
                        .padding(vertical = 5.dp)
                        .testTag("updates_my_status_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusRingThumbnail(
                        status = latestMine,
                        segmentCount = myStatuses.size,
                        ringColor = brandBlue
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mi estado",
                            fontFamily = InterFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = TimeFormatters.formatStatusTime(latestMine.createdAt),
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onAddStatus,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("updates_add_another_status_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(brandBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Añadir otro estado",
                                tint = brandBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Empty state when no other family members have active statuses
        if (recentGroups.isEmpty() && viewedGroups.isEmpty()) {
            item(key = "updates_empty_state") {
                Text(
                    text = "Aún no hay estados. Toca la cámara para compartir tu día.",
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = secondaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 32.dp)
                        .testTag("updates_empty_message")
                )
            }
        }

        // Section: Recientes (unseen statuses, newest to oldest)
        if (recentGroups.isNotEmpty()) {
            item(key = "section_recientes_header") {
                Text(
                    text = "Recientes",
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = secondaryText,
                    modifier = Modifier
                        .padding(top = 18.dp, bottom = 6.dp)
                        .testTag("updates_section_recent")
                )
            }
            items(recentGroups, key = { "recent_${it.member.id}" }) { group ->
                StatusUpdateRow(
                    group = group,
                    ringColor = brandBlue,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    tag = "updates_recent_row_${group.member.id}",
                    onClick = { onOpenMemberStatuses(group) }
                )
            }
        }

        // Section: Vistos (seen statuses, newest to oldest)
        if (viewedGroups.isNotEmpty()) {
            item(key = "section_vistos_header") {
                Text(
                    text = "Vistos",
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = secondaryText,
                    modifier = Modifier
                        .padding(top = 18.dp, bottom = 6.dp)
                        .testTag("updates_section_viewed")
                )
            }
            items(viewedGroups, key = { "viewed_${it.member.id}" }) { group ->
                StatusUpdateRow(
                    group = group,
                    ringColor = viewedRingColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    tag = "updates_viewed_row_${group.member.id}",
                    onClick = { onOpenMemberStatuses(group) }
                )
            }
        }
    }
}

@Composable
private fun FamilyNoticesPinnedCard(
    notes: List<FamilyNoteEntity>,
    membersMap: Map<String, FamilyMemberEntity>,
    isDarkTheme: Boolean,
    primaryText: Color,
    secondaryText: Color,
    brandBlue: Color
) {
    val cardBg = if (isDarkTheme) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    } else {
        UpdatesNoticeCardBgLight
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("updates_family_notices_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = null,
                    tint = brandBlue,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Avisos de la familia",
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText
                )
            }
            notes.forEach { note ->
                val author = membersMap[note.authorId]
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (note.title.isNotBlank()) {
                        Text(
                            text = note.title,
                            fontFamily = InterFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (note.content.isNotBlank()) {
                        Text(
                            text = note.content,
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = secondaryText,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (author != null) {
                        Text(
                            text = author.name,
                            fontFamily = InterFontFamily,
                            fontSize = 11.sp,
                            color = secondaryText.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusUpdateRow(
    group: MemberStatusGroup,
    ringColor: Color,
    primaryText: Color,
    secondaryText: Color,
    tag: String,
    onClick: () -> Unit
) {
    val latestStatus = group.items.last()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 62.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 5.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusRingThumbnail(
            status = latestStatus,
            segmentCount = group.items.size,
            ringColor = ringColor
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.member.name,
                fontFamily = InterFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = TimeFormatters.formatStatusTime(latestStatus.createdAt),
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                color = secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 46dp rounded-square status thumbnail surrounded by a 3dp ring with 3dp separation.
 * When [segmentCount] > 1, the rounded-square perimeter is divided into [segmentCount] equal segments.
 */
@Composable
private fun StatusRingThumbnail(
    status: FamilyStatusEntity,
    segmentCount: Int,
    ringColor: Color,
    thumbnailSize: Dp = 46.dp,
    ringStroke: Dp = 3.dp,
    ringGap: Dp = 3.dp
) {
    val outerSize = thumbnailSize + (ringGap + ringStroke) * 2
    val thumbCorner = 14.dp
    val ringCorner = thumbCorner + ringGap + ringStroke / 2

    Box(
        modifier = Modifier.size(outerSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = ringStroke.toPx()
            val halfStroke = strokePx / 2f
            val cornerPx = ringCorner.toPx()
            val count = segmentCount.coerceAtLeast(1)

            val rectPath = android.graphics.Path().apply {
                addRoundRect(
                    halfStroke,
                    halfStroke,
                    size.width - halfStroke,
                    size.height - halfStroke,
                    cornerPx,
                    cornerPx,
                    android.graphics.Path.Direction.CW
                )
            }

            if (count == 1) {
                drawPath(
                    path = rectPath.asComposePath(),
                    color = ringColor,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            } else {
                val measure = PathMeasure(rectPath, true)
                val totalLength = measure.length
                if (totalLength > 0f) {
                    val desiredGap = 5.5.dp.toPx()
                    val maxTotalGap = totalLength * 0.42f
                    val gapPx = (desiredGap * count).coerceAtMost(maxTotalGap) / count
                    val segLen = ((totalLength - gapPx * count) / count).coerceAtLeast(2f)
                    val step = segLen + gapPx
                    val startOffset = gapPx / 2f

                    for (i in 0 until count) {
                        val startDist = i * step + startOffset
                        val endDist = (startDist + segLen).coerceAtMost(totalLength)
                        val segmentPath = android.graphics.Path()
                        if (measure.getSegment(startDist, endDist, segmentPath, true)) {
                            drawPath(
                                path = segmentPath.asComposePath(),
                                color = ringColor,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }
        }

        // 46dp rounded-square status thumbnail
        Box(
            modifier = Modifier
                .size(thumbnailSize)
                .clip(RoundedCornerShape(thumbCorner))
                .background(
                    if (status.isVideo) Color(0xFF111827)
                    else parseHexColor(status.backgroundHex, Color(0xFF128C7E))
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                status.isVideo -> {
                    val thumbModel = status.videoThumbnailData?.takeIf { it.isNotBlank() }
                        ?.let { ImageCompressionHelper.resolveCoilModel(it) }
                    if (thumbModel != null) {
                        AsyncImage(
                            model = thumbModel,
                            contentDescription = "Miniatura de estado en video",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Semi-transparent dark circle with white play triangle over video thumbnail
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.56f))
                            .testTag("updates_video_play_badge_${status.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                !status.mediaUri.isNullOrBlank() -> {
                    AsyncImage(
                        model = ImageCompressionHelper.resolveCoilModel(status.mediaUri),
                        contentDescription = "Miniatura de estado",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Text(
                        text = status.text.ifBlank { "✨" },
                        color = Color.White,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
    }
}
