package kr.co.safetyassistant

import java.time.LocalTime
import java.time.ZonedDateTime

fun nextScheduledRun(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
    val time = LocalTime.of(hour, minute)
    val today = now.toLocalDate()
    val next = today.atTime(time).atZone(now.zone)
    return if (next.isAfter(now)) next else today.plusDays(1).atTime(time).atZone(now.zone)
}
