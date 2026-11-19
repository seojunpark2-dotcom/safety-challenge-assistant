package kr.co.safetyassistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

fun enabledTasks(prefs: SharedPreferences) = Task.entries.filter {
    prefs.getBoolean("auto.$it", false)
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val p = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!p.getBoolean("enabled", false)) return Result.success()
        val tasks = enabledTasks(p)
        val launched = withContext(Dispatchers.Main) { DiagnosticService.instance?.start(tasks, scheduled = true) == true }
        schedule(applicationContext, append = true)
        if (launched) return Result.success()
        if (Task.ATTENDANCE in tasks && p.getBoolean("attendanceNotifications", false)) {
            applicationContext.notifyAttendanceResult("예약 출석을 시작하지 못했어요. 대상 앱 설치와 접근성 설정을 확인하세요.")
            return Result.success()
        }
        applicationContext.notifyAutomationStatus("접근성 연결과 실행 설정을 확인하세요.")
        return Result.success()
    }
}

internal fun Context.notifyAutomationStatus(message: String) {
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel("reminder", "자동화 안내", NotificationManager.IMPORTANCE_DEFAULT))
    val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val notification = NotificationCompat.Builder(this, "reminder").setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("안전실천 출첵 확인 필요").setContentText(message)
        .setContentIntent(pending).setAutoCancel(true).build()
    try { manager.notify(1, notification) } catch (_: SecurityException) { }
}

fun schedule(context: Context, append: Boolean = false) {
    val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val manager = WorkManager.getInstance(context)
    if (!p.getBoolean("enabled", false)) { manager.cancelUniqueWork("daily"); return }
    val delay = delayUntilNextRun(LocalDateTime.now(), p.getInt("hour", 9), p.getInt("minute", 0)).toMillis()
    val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
    val policy = if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
    manager.enqueueUniqueWork("daily", policy, request)
}
