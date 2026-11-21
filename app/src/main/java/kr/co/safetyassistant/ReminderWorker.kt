package kr.co.safetyassistant

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

fun enabledTasks(prefs: SharedPreferences): List<Task> {
    val hasSavedTasks = Task.entries.any { prefs.contains("auto.$it") }
    return Task.entries.filter { prefs.getBoolean("auto.$it", !hasSavedTasks && it == Task.ATTENDANCE) }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RUN_ALARM) {
            val revision = intent.getLongExtra(SCHEDULE_REVISION, 0L)
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            if (revision != prefs.getLong(SCHEDULE_REVISION, 0L)) return
            val now = ZonedDateTime.now()
            if (isScheduledFallbackDue(now, intent.getLongExtra(DUE_AT, 0L))) {
                runScheduledTasks(context, scheduledRunKey(now.toLocalDate(), revision))
            }
        }
        schedule(
            context,
            keepFallback = intent.action == RUN_ALARM,
            includeCurrentMinute = intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}

private const val RUN_ALARM = "kr.co.safetyassistant.RUN_ALARM"
private const val DUE_AT = "scheduledAtMillis"

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.Main) {
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return@withContext Result.success()
        val revision = inputData.getLong(SCHEDULE_REVISION, 0L)
        if (revision != prefs.getLong(SCHEDULE_REVISION, 0L)) return@withContext Result.success()
        val now = ZonedDateTime.now()
        if (!isScheduledFallbackDue(now, inputData.getLong(DUE_AT, 0L))) {
            schedule(applicationContext, append = true)
            return@withContext Result.success()
        }
        val runKey = scheduledRunKey(now.toLocalDate(), revision)
        runScheduledTasks(applicationContext, runKey)
        DiagnosticService.instance?.awaitAutomation()
        if (!prefs.getBoolean("enabled", false) || revision != prefs.getLong(SCHEDULE_REVISION, 0L)) {
            return@withContext Result.success()
        }
        val started = prefs.getString("lastScheduledStart", null) == runKey
        if (!started && enabledTasks(prefs).isNotEmpty() && runAttemptCount == 0) {
            return@withContext Result.retry()
        }
        schedule(applicationContext, append = true)
        Result.success()
    }
}

private fun runScheduledTasks(context: Context, runKey: String) {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    if (!prefs.getBoolean("enabled", false)) return
    if (prefs.getString("lastScheduledStart", null) == runKey) return
    val tasks = enabledTasks(prefs)
    val service = DiagnosticService.instance
    if (service?.automationRunning == true || service?.start(tasks, scheduledKey = runKey) == true) return
    val reason = when {
        tasks.isEmpty() -> "예약 실행할 작업이 선택되지 않았어요."
        service == null -> "접근성 서비스가 연결되지 않았어요."
        else -> "안전실천 챌린지 앱을 열 수 없어요. 설치 여부를 확인하세요."
    }
    context.recordHistory("예약 실행 실패 · $reason")
    if (tasks.isEmpty()) context.notifyAutomationStatus(reason)
    else context.notifyRunResult(tasks, unfinishedReason = reason)
}

internal fun Context.recordHistory(message: String) {
    val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
    val row = "${LocalDateTime.now().withNano(0)} · $message"
    val previous = prefs.getString("history", "").orEmpty()
    prefs.edit().putString("history", (listOf(row) + previous.lines()).take(30).joinToString("\n")).apply()
}

internal fun Context.canScheduleExact() = Build.VERSION.SDK_INT < 31 ||
    getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

internal fun Context.notifyAutomationStatus(message: String) {
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel("reminder", "자동화 안내", NotificationManager.IMPORTANCE_DEFAULT))
    val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val notification = NotificationCompat.Builder(this, "reminder").setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("안전실천 출첵 확인 필요").setContentText(message)
        .setContentIntent(pending).setAutoCancel(true).build()
    try { manager.notify(1, notification) } catch (_: SecurityException) { }
}

fun schedule(
    context: Context,
    append: Boolean = false,
    keepFallback: Boolean = false,
    includeCurrentMinute: Boolean = false
) {
    val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val manager = WorkManager.getInstance(context)
    val alarms = context.getSystemService(AlarmManager::class.java)
    val alarmIntent = Intent(context, ReminderReceiver::class.java).setAction(RUN_ALARM)
    if (!p.getBoolean("enabled", false)) {
        PendingIntent.getBroadcast(context, 0, alarmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)?.let(alarms::cancel)
        manager.cancelUniqueWork("daily")
        return
    }
    val now = ZonedDateTime.now()
    val revision = p.getLong(SCHEDULE_REVISION, 0L)
    val eligibleThisMinute = includeCurrentMinute &&
        p.getString("lastScheduledStart", null) != scheduledRunKey(now.toLocalDate(), revision)
    val next = nextScheduledRun(now, p.getInt("hour", 9), p.getInt("minute", 0), eligibleThisMinute)
    val pending = PendingIntent.getBroadcast(context, 0,
        alarmIntent.putExtra(DUE_AT, next.toInstant().toEpochMilli()).putExtra(SCHEDULE_REVISION, revision),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val exactRegistered = if (context.canScheduleExact()) {
        try {
            val settings = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE)
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(next.toInstant().toEpochMilli(), settings), pending)
            true
        } catch (_: SecurityException) {
            // The user can revoke exact alarm access between the check and registration.
            false
        }
    } else false
    if (!exactRegistered) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
    }
    // Keep WorkManager scheduled because revocation removes exact alarms without warning.
    val fallback = if (exactRegistered) next.plusSeconds(45) else next
    val delay = Duration.between(now, fallback).toMillis()
    val request = OneTimeWorkRequestBuilder<ReminderWorker>()
        .setInputData(workDataOf(DUE_AT to next.toInstant().toEpochMilli(), SCHEDULE_REVISION to revision))
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .build()
    val policy = when {
        append -> ExistingWorkPolicy.APPEND_OR_REPLACE
        keepFallback -> ExistingWorkPolicy.KEEP
        else -> ExistingWorkPolicy.REPLACE
    }
    manager.enqueueUniqueWork("daily", policy, request)
}
