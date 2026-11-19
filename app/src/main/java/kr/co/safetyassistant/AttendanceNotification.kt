package kr.co.safetyassistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

internal fun Context.notifyAttendanceResult(message: String) {
    if (!getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("attendanceNotifications", false)) return
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel("attendance", "출석 결과", NotificationManager.IMPORTANCE_DEFAULT))
    val tap = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val notification = NotificationCompat.Builder(this, "attendance")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("안전실천 출첵 · 출석 결과")
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setContentIntent(tap)
        .setAutoCancel(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .build()
    try { manager.notify(2, notification) } catch (_: SecurityException) { }
}
