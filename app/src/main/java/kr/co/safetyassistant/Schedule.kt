package kr.co.safetyassistant

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

internal const val SCHEDULE_REVISION = "scheduleRevision"

internal fun scheduledRunKey(date: LocalDate, revision: Long): String =
    if (revision == 0L) date.toString() else "$date:$revision"

internal fun isScheduledFallbackDue(now: ZonedDateTime, dueMillis: Long): Boolean {
    if (dueMillis <= 0L) return false
    val due = Instant.ofEpochMilli(dueMillis).atZone(now.zone)
    return !now.isBefore(due) && now.toLocalDate() == due.toLocalDate()
}

fun nextScheduledRun(now: ZonedDateTime, hour: Int, minute: Int, includeCurrentMinute: Boolean = false): ZonedDateTime {
    val time = LocalTime.of(hour, minute)
    val today = now.toLocalDate()
    val next = today.atTime(time).atZone(now.zone)
    if (includeCurrentMinute && !now.isBefore(next) && now.isBefore(next.plusMinutes(1))) return now
    return if (next.isAfter(now)) next else today.plusDays(1).atTime(time).atZone(now.zone)
}
