package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object FamiChatNotificationHelper {
    const val CHANNEL_MESSAGES = "famichat_messages"
    const val CHANNEL_CALLS = "famichat_calls"
    const val EXTRA_OPEN_CHAT_ID = "open_chat_id"

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val msgChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Mensajes familiares",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones de mensajes nuevos en FamiChat"
            enableVibration(true)
        }

        val callChannel = NotificationChannel(
            CHANNEL_CALLS,
            "Llamadas familiares",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas de llamadas de voz y videollamadas entrantes"
            enableVibration(true)
        }

        manager.createNotificationChannel(msgChannel)
        manager.createNotificationChannel(callChannel)
    }

    @SuppressLint("MissingPermission")
    fun notifyNewMessage(
        context: Context,
        chatId: String,
        chatTitle: String,
        senderName: String,
        body: String,
        isGroup: Boolean
    ) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CHAT_ID, chatId)
        }
        val requestCode = chatId.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isGroup) "$senderName en $chatTitle" else senderName
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyIncomingCall(
        context: Context,
        callId: String,
        chatId: String,
        callerName: String,
        isVideo: Boolean
    ) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CHAT_ID, chatId)
        }
        val requestCode = ("call_$callId").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isVideo) "Videollamada entrante" else "Llamada entrante"
        val body = "$callerName te está llamando en FamiChat"
        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        }
    }

    fun cancelChatNotifications(context: Context, chatId: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(chatId.hashCode())
        }
    }

    fun cancelCallNotification(context: Context, callId: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(("call_$callId").hashCode())
        }
    }
}
