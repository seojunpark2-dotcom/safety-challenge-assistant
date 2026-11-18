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
        val launched = withContext(Dispatchers.Main) { DiagnosticService.instance?.start(tasks) == true }
        schedule(applicationContext)
        if (launched) return Result.success()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("reminder", "자동화 안내", NotificationManager.IMPORTANCE_DEFAULT))
        val pending = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(applicationContext, "reminder").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("안전실천 도우미 확인 필요").setContentText("기기를 잠금 해제하고 접근성 연결과 실행 설정을 확인하세요.")
            .setContentIntent(pending).setAutoCancel(true).build()
        try { manager.notify(1, notification) } catch (_: SecurityException) { }
        return Result.success()
    }
}
fun schedule(context: Context) {
    val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val manager = WorkManager.getInstance(context)
    if (!p.getBoolean("enabled", false)) { manager.cancelUniqueWork("daily"); return }
    val delay = delayUntilNextRun(LocalDateTime.now(), p.getInt("hour", 9), p.getInt("minute", 0)).toMillis()
    val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
    manager.enqueueUniqueWork("daily", ExistingWorkPolicy.REPLACE, request)
}
