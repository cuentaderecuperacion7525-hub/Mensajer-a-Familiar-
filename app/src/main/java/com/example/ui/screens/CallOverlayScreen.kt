package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallType
import com.example.ui.theme.DangerRed
import com.example.ui.viewmodel.ActiveCallUiState
import com.example.util.TimeFormatters
import com.example.util.parseHexColor

@Composable
fun CallOverlayScreen(
    callState: ActiveCallUiState,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onEndCall: () -> Unit
) {
    BackHandler { onEndCall() }
    val context = LocalContext.current

    val pulse = rememberInfiniteTransition(label = "call_pulse")
    val ringScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )
    val accent = parseHexColor(callState.avatarColorHex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (callState.callType == CallType.VIDEO && callState.isCameraOn) {
                        listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0B1120))
                    } else {
                        listOf(Color(0xFF0B1120), Color(0xFF172133), Color(0xFF090D16))
                    }
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Text(
                        text = if (callState.callType == CallType.VIDEO) "VIDEOLLAMADA" else "LLAMADA DE VOZ",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = callState.title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        callState.isConnected -> "Conectado · ${TimeFormatters.formatDuration(callState.durationSec)}"
                        callState.isOutgoing -> "Sonando en dispositivos..."
                        else -> "Conectando..."
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF93C5FD)
                )
            }

            // Center Avatar / Video Preview
            Box(contentAlignment = Alignment.Center) {
                if (!callState.isConnected) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .scale(ringScale)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = callState.avatarEmoji,
                        fontSize = 62.sp
                    )
                }
            }

            if (callState.callType == CallType.VIDEO) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.35f),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (callState.isCameraOn) "📹 Tu cámara activa" else "📷 Cámara pausada",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom Controls
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallControlButton(
                        icon = if (callState.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        label = if (callState.isMuted) "Silencio" else "Mic",
                        active = callState.isMuted,
                        tag = "call_mute_button",
                        onClick = onToggleMute
                    )
                    CallControlButton(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        label = "Altavoz",
                        active = callState.isSpeakerOn,
                        tag = "call_speaker_button",
                        onClick = onToggleSpeaker
                    )
                    if (callState.callType == CallType.VIDEO) {
                        CallControlButton(
                            icon = if (callState.isCameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                            label = "Cámara",
                            active = !callState.isCameraOn,
                            tag = "call_camera_button",
                            onClick = onToggleCamera
                        )
                    }
                    if (callState.targetPhoneNumber.isNotBlank()) {
                        CallControlButton(
                            icon = Icons.Filled.Call,
                            label = "Teléfono",
                            active = false,
                            tag = "call_carrier_button",
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${callState.targetPhoneNumber}"))
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onEndCall,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(DangerRed)
                                .testTag("call_hangup_button")
                        ) {
                            Icon(
                                Icons.Filled.CallEnd,
                                contentDescription = "Colgar llamada",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Colgar", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.16f))
                .testTag(tag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color(0xFF1A73E8) else Color.White
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
