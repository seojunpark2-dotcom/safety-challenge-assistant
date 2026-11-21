package kr.co.safetyassistant

import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

internal fun isScheduledFallbackDue(now: ZonedDateTime, dueMillis: Long): Boolean {
    if (dueMillis <= 0L) return false
    val due = Instant.ofEpochMilli(dueMillis).atZone(now.zone)
    return !now.isBefore(due) && now.toLocalDate() == due.toLocalDate()
}

fun nextScheduledRun(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
    val time = LocalTime.of(hour, minute)
    val today = now.toLocalDate()
    val next = today.atTime(time).atZone(now.zone)
    return if (next.isAfter(now)) next else today.plusDays(1).atTime(time).atZone(now.zone)
}
