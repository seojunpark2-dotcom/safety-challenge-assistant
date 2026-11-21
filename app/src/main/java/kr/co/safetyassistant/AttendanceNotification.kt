package kr.co.safetyassistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

internal data class TaskResult(val message: String, val success: Boolean = false)

internal fun summarizeAutomation(
    tasks: List<Task>,
    results: Map<Task, TaskResult>,
    unfinishedReason: String
) = Task.entries.joinToString("\n") { task ->
    val result = results[task] ?: TaskResult(unfinishedReason)
    when {
        task !in tasks -> "${task.title} 미실행"
        result.success -> "${task.title} 성공 · ${result.message}"
        else -> "${task.title} 실패 · ${result.message}"
    }
}

internal fun Context.notifyRunResult(
    tasks: List<Task>,
    results: Map<Task, TaskResult> = emptyMap(),
    unfinishedReason: String = "실행을 완료하지 못했어요."
) {
    val message = summarizeAutomation(tasks, results, unfinishedReason)
    val successful = tasks.count { results[it]?.success == true }
    val manager = getSystemService(NotificationManager::class.java)
    val channelId = "attendance_popup"
    manager.createNotificationChannel(NotificationChannel(channelId, "출석 결과 팝업", NotificationManager.IMPORTANCE_HIGH))
    val tap = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val notification = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(when {
            successful == Task.entries.size -> "자동화 3개 모두 성공"
            tasks.isNotEmpty() && successful == tasks.size -> "선택한 작업 완료"
            else -> "자동화 결과 확인 필요"
        })
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setContentIntent(tap)
        .setAutoCancel(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .build()
    try { manager.notify(2, notification) } catch (_: SecurityException) { }
}
