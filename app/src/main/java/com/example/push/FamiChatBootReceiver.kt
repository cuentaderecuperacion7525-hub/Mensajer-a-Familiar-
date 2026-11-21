package com.example.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.PreferencesManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restores real-time background sync after device reboot or app update when the
 * user already has an active FamiChat profile and notifications enabled.
 */
class FamiChatBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
                if (uid.isNullOrBlank()) return@launch
                val prefs = PreferencesManager(appContext)
                val notifEnabled = prefs.notificationsEnabledFlow.first()
                val keepRealtimeService = prefs.realtimeBackgroundServiceEnabledFlow.first()
                val famId = prefs.activeFamilyIdFlow.first()
                val memberId = prefs.activeMemberIdFlow.first()
                if (notifEnabled && !famId.isNullOrBlank() && !memberId.isNullOrBlank()) {
                    PushRegistrationManager.schedulePeriodicBackgroundSync(appContext)
                    if (keepRealtimeService) {
                        FamiChatRealtimeService.start(appContext)
                    } else {
                        FamiChatRealtimeService.stop(appContext)
                    }
                } else {
                    FamiChatRealtimeService.stop(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
