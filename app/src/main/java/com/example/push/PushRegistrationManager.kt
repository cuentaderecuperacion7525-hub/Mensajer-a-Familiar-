package com.example.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.PreferencesManager
import com.example.data.remote.FamilyCloudRepository
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

object PushRegistrationManager {
    private const val UNIQUE_PERIODIC_WORK = "famichat_periodic_bg_sync"
    private const val UNIQUE_IMMEDIATE_WORK = "famichat_bg_check_once"

    fun topicForFamily(familyId: String): String {
        val sanitized = familyId.replace(Regex("[^a-zA-Z0-9_\\-%.~]"), "_")
        return "famichat_$sanitized"
    }

    @Suppress("DEPRECATION")
    suspend fun syncFcmTokenAndTopic(
        context: Context,
        cloudRepo: FamilyCloudRepository,
        prefs: PreferencesManager,
        familyId: String,
        memberId: String
    ): String? {
        val token = runCatching { Firebase.messaging.token.await() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: prefs.fcmTokenFlow.first()?.takeIf { it.isNotBlank() }

        if (!token.isNullOrBlank()) {
            prefs.setFcmToken(token)
            runCatching {
                cloudRepo.registerMemberFcmToken(familyId, memberId, token)
            }
        }
        runCatching {
            Firebase.messaging.subscribeToTopic(topicForFamily(familyId)).await()
        }
        return token
    }

    @Suppress("DEPRECATION")
    suspend fun unregisterMemberToken(
        cloudRepo: FamilyCloudRepository,
        prefs: PreferencesManager,
        familyId: String,
        memberId: String
    ) {
        val token = prefs.fcmTokenFlow.first()?.takeIf { it.isNotBlank() }
            ?: runCatching { Firebase.messaging.token.await() }.getOrNull()
        if (!token.isNullOrBlank()) {
            runCatching {
                cloudRepo.unregisterMemberFcmToken(familyId, memberId, token)
            }
        }
    }

    suspend fun unsubscribeFromFamilyTopic(familyId: String) {
        runCatching {
            Firebase.messaging.unsubscribeFromTopic(topicForFamily(familyId)).await()
        }
    }

    fun schedulePeriodicBackgroundSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<FamiChatBackgroundSyncWorker>(
            15,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_WORK,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }

    fun scheduleBackgroundCheckOnStop(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<FamiChatBackgroundSyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(30, TimeUnit.SECONDS)
            .build()

        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    UNIQUE_IMMEDIATE_WORK,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }

    fun cancelBackgroundSync(context: Context) {
        runCatching {
            val wm = WorkManager.getInstance(context.applicationContext)
            wm.cancelUniqueWork(UNIQUE_PERIODIC_WORK)
            wm.cancelUniqueWork(UNIQUE_IMMEDIATE_WORK)
        }
    }
}
