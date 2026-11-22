package com.example.util

import android.content.Context
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StartupTimelineEvent(
    val timestampMs: Long,
    val elapsedMs: Long,
    val label: String,
    val detail: String = ""
)

data class StartupTimelineSnapshot(
    val launchStartedAtMs: Long,
    val onCreateCount: Int,
    val viewModelCreateCount: Int,
    val uidChanged: Boolean,
    val processRestartDetected: Boolean,
    val events: List<StartupTimelineEvent>
) {
    val hasMultipleOnCreate: Boolean
        get() = onCreateCount > 1

    val hasMultipleViewModels: Boolean
        get() = viewModelCreateCount > 1

    fun formatClock(timestampMs: Long): String {
        if (timestampMs <= 0L) return "--:--:--.---"
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return fmt.format(Date(timestampMs))
    }

    fun formatReport(): String = buildString {
        appendLine("Línea de tiempo del último arranque (FamiChat)")
        appendLine("• onCreate: $onCreateCount ${if (onCreateCount == 1) "vez" else "veces"}")
        appendLine(
            "• FamilyViewModel: $viewModelCreateCount ${if (viewModelCreateCount == 1) "vez" else "veces"} · " +
                if (uidChanged) "cambió el UID" else "mismo UID"
        )
        if (processRestartDetected) {
            appendLine("• Aviso: se detectó un reinicio rápido de proceso (< 15 s)")
        }
        events.forEach { ev ->
            val delta = "+${ev.elapsedMs} ms"
            val suffix = if (ev.detail.isNotBlank()) " (${ev.detail})" else ""
            appendLine("${formatClock(ev.timestampMs)} [$delta] ${ev.label}$suffix")
        }
    }.trim()
}

/**
 * Local-only startup diagnostics timeline persisted exclusively in device SharedPreferences.
 * Never touches Firestore or the network.
 */
object StartupTimelineTracker {
    private const val PREFS_NAME = "famichat_startup_timeline"
    private const val KEY_PROCESS_PID = "process_pid"
    private const val KEY_LAUNCH_STARTED_AT = "launch_started_at"
    private const val KEY_LAST_ON_CREATE_AT = "last_on_create_at"
    private const val KEY_ON_CREATE_COUNT = "on_create_count"
    private const val KEY_VM_CREATE_COUNT = "vm_create_count"
    private const val KEY_FIRST_VM_UID = "first_vm_uid"
    private const val KEY_UID_CHANGED = "uid_changed"
    private const val KEY_PROCESS_RESTART_DETECTED = "process_restart_detected"
    private const val KEY_EVENTS_JSON = "events_json"

    private const val RAPID_PROCESS_RESTART_WINDOW_MS = 15_000L
    private const val MAX_EVENTS = 30

    private val lock = Any()

    @Volatile
    private var inMemoryProcessInitialized = false

    @Volatile
    private var firstScreenRecordedInProcess = false

    @Volatile
    private var sessionRestoredRecordedInProcess = false

    @Volatile
    private var firebaseReadyRecordedForUid: String? = null

    fun recordOnCreate(context: Context, hasSavedInstanceState: Boolean) {
        synchronized(lock) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val currentPid = Process.myPid()
            val savedPid = prefs.getInt(KEY_PROCESS_PID, -1)
            val prevOnCreateAt = prefs.getLong(KEY_LAST_ON_CREATE_AT, 0L)

            val isNewProcessLaunch = !inMemoryProcessInitialized || savedPid != currentPid
            val onCreateCount: Int
            val launchStartAt: Long
            val events: MutableList<StartupTimelineEvent>
            val processRestartDetected: Boolean

            if (isNewProcessLaunch) {
                inMemoryProcessInitialized = true
                firstScreenRecordedInProcess = false
                sessionRestoredRecordedInProcess = false
                firebaseReadyRecordedForUid = null

                onCreateCount = 1
                launchStartAt = now
                val rapidDelta = if (prevOnCreateAt > 0L) now - prevOnCreateAt else -1L
                processRestartDetected = savedPid != -1 &&
                    savedPid != currentPid &&
                    rapidDelta in 1L..RAPID_PROCESS_RESTART_WINDOW_MS

                events = mutableListOf()
                if (processRestartDetected) {
                    events.add(
                        StartupTimelineEvent(
                            timestampMs = now,
                            elapsedMs = 0L,
                            label = "Reinicio rápido de proceso detectado",
                            detail = "+${rapidDelta} ms desde el arranque anterior"
                        )
                    )
                }
                val detail = if (hasSavedInstanceState) "con estado restaurado" else "arranque en frío"
                events.add(
                    StartupTimelineEvent(
                        timestampMs = now,
                        elapsedMs = 0L,
                        label = "onCreate (#1)",
                        detail = detail
                    )
                )

                prefs.edit()
                    .putInt(KEY_PROCESS_PID, currentPid)
                    .putLong(KEY_LAUNCH_STARTED_AT, launchStartAt)
                    .putLong(KEY_LAST_ON_CREATE_AT, now)
                    .putInt(KEY_ON_CREATE_COUNT, onCreateCount)
                    .putInt(KEY_VM_CREATE_COUNT, 0)
                    .remove(KEY_FIRST_VM_UID)
                    .putBoolean(KEY_UID_CHANGED, false)
                    .putBoolean(KEY_PROCESS_RESTART_DETECTED, processRestartDetected)
                    .putString(KEY_EVENTS_JSON, encodeEvents(events))
                    .apply()
            } else {
                onCreateCount = prefs.getInt(KEY_ON_CREATE_COUNT, 0) + 1
                launchStartAt = prefs.getLong(KEY_LAUNCH_STARTED_AT, now).takeIf { it > 0L } ?: now
                events = decodeEvents(prefs.getString(KEY_EVENTS_JSON, null)).toMutableList()
                val detail = if (hasSavedInstanceState) "recreación de pantalla" else "nueva invocación"
                appendEvent(
                    events = events,
                    timestampMs = now,
                    launchStartAt = launchStartAt,
                    label = "onCreate (#$onCreateCount)",
                    detail = detail
                )
                prefs.edit()
                    .putLong(KEY_LAST_ON_CREATE_AT, now)
                    .putInt(KEY_ON_CREATE_COUNT, onCreateCount)
                    .putString(KEY_EVENTS_JSON, encodeEvents(events))
                    .apply()
            }
        }
    }

    fun recordThemeApplied(
        context: Context,
        themeLabel: String,
        fontScaleLabel: String,
        fromFastCache: Boolean
    ) {
        val source = if (fromFastCache) "caché local inmediata" else "respaldo DataStore"
        recordSimpleEvent(
            context = context,
            label = "Tema aplicado",
            detail = "$themeLabel · Letra $fontScaleLabel ($source)"
        )
    }

    fun recordFirebaseSessionReady(
        context: Context,
        uid: String,
        alreadySignedIn: Boolean
    ) {
        synchronized(lock) {
            if (uid.isBlank() || firebaseReadyRecordedForUid == uid) return
            firebaseReadyRecordedForUid = uid
        }
        val source = if (alreadySignedIn) "sesión guardada" else "inicio de sesión"
        recordSimpleEvent(
            context = context,
            label = "Sesión de Firebase lista",
            detail = "$source · UID ${maskUid(uid)}"
        )
    }

    fun recordViewModelCreated(context: Context, uid: String) {
        synchronized(lock) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val launchStartAt = prefs.getLong(KEY_LAUNCH_STARTED_AT, now).takeIf { it > 0L } ?: now
            val nextCount = prefs.getInt(KEY_VM_CREATE_COUNT, 0) + 1
            val firstUid = prefs.getString(KEY_FIRST_VM_UID, null)
            val uidChangedNow = !firstUid.isNullOrBlank() && uid.isNotBlank() && firstUid != uid
            val uidChanged = prefs.getBoolean(KEY_UID_CHANGED, false) || uidChangedNow
            val events = decodeEvents(prefs.getString(KEY_EVENTS_JSON, null)).toMutableList()
            val uidNote = when {
                uidChangedNow -> "cambió el UID (${maskUid(firstUid.orEmpty())} → ${maskUid(uid)})"
                nextCount == 1 -> "UID ${maskUid(uid)}"
                else -> "mismo UID ${maskUid(uid)}"
            }
            appendEvent(
                events = events,
                timestampMs = now,
                launchStartAt = launchStartAt,
                label = "FamilyViewModel creado (#$nextCount)",
                detail = uidNote
            )
            prefs.edit()
                .putInt(KEY_VM_CREATE_COUNT, nextCount)
                .apply {
                    if (firstUid.isNullOrBlank() && uid.isNotBlank()) {
                        putString(KEY_FIRST_VM_UID, uid)
                    }
                    putBoolean(KEY_UID_CHANGED, uidChanged)
                    putString(KEY_EVENTS_JSON, encodeEvents(events))
                }
                .apply()
        }
    }

    fun recordSessionRestored(context: Context, hasActiveProfile: Boolean) {
        synchronized(lock) {
            if (sessionRestoredRecordedInProcess) return
            sessionRestoredRecordedInProcess = true
        }
        val detail = if (hasActiveProfile) "perfil guardado restaurado" else "sin perfil guardado"
        recordSimpleEvent(
            context = context,
            label = "Sesión restaurada",
            detail = detail
        )
    }

    fun recordFirstScreenVisible(context: Context, screenName: String) {
        synchronized(lock) {
            if (firstScreenRecordedInProcess) return
            firstScreenRecordedInProcess = true
        }
        recordSimpleEvent(
            context = context,
            label = "Primera pantalla visible",
            detail = screenName
        )
    }

    fun readSnapshot(context: Context): StartupTimelineSnapshot {
        synchronized(lock) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return StartupTimelineSnapshot(
                launchStartedAtMs = prefs.getLong(KEY_LAUNCH_STARTED_AT, 0L),
                onCreateCount = prefs.getInt(KEY_ON_CREATE_COUNT, 0),
                viewModelCreateCount = prefs.getInt(KEY_VM_CREATE_COUNT, 0),
                uidChanged = prefs.getBoolean(KEY_UID_CHANGED, false),
                processRestartDetected = prefs.getBoolean(KEY_PROCESS_RESTART_DETECTED, false),
                events = decodeEvents(prefs.getString(KEY_EVENTS_JSON, null))
            )
        }
    }

    internal fun resetForTest(context: Context) {
        synchronized(lock) {
            inMemoryProcessInitialized = false
            firstScreenRecordedInProcess = false
            sessionRestoredRecordedInProcess = false
            firebaseReadyRecordedForUid = null
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }

    private fun recordSimpleEvent(context: Context, label: String, detail: String) {
        synchronized(lock) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val launchStartAt = prefs.getLong(KEY_LAUNCH_STARTED_AT, now).takeIf { it > 0L } ?: now
            val events = decodeEvents(prefs.getString(KEY_EVENTS_JSON, null)).toMutableList()
            appendEvent(events, now, launchStartAt, label, detail)
            prefs.edit()
                .putString(KEY_EVENTS_JSON, encodeEvents(events))
                .apply()
        }
    }

    private fun appendEvent(
        events: MutableList<StartupTimelineEvent>,
        timestampMs: Long,
        launchStartAt: Long,
        label: String,
        detail: String
    ) {
        val elapsed = (timestampMs - launchStartAt).coerceAtLeast(0L)
        events.add(
            StartupTimelineEvent(
                timestampMs = timestampMs,
                elapsedMs = elapsed,
                label = label,
                detail = detail
            )
        )
        while (events.size > MAX_EVENTS) {
            events.removeAt(0)
        }
    }

    private fun maskUid(uid: String): String {
        val trimmed = uid.trim()
        return when {
            trimmed.isEmpty() -> "vacío"
            trimmed.length <= 6 -> trimmed
            else -> "${trimmed.take(4)}…${trimmed.takeLast(2)}"
        }
    }

    private fun encodeEvents(events: List<StartupTimelineEvent>): String {
        val arr = JSONArray()
        events.forEach { ev ->
            val obj = JSONObject()
            obj.put("ts", ev.timestampMs)
            obj.put("el", ev.elapsedMs)
            obj.put("lb", ev.label)
            obj.put("dt", ev.detail)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeEvents(raw: String?): List<StartupTimelineEvent> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val label = obj.optString("lb", "").trim()
                    if (label.isBlank()) continue
                    add(
                        StartupTimelineEvent(
                            timestampMs = obj.optLong("ts", 0L),
                            elapsedMs = obj.optLong("el", 0L),
                            label = label,
                            detail = obj.optString("dt", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
