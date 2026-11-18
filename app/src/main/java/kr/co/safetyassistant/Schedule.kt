package kr.co.safetyassistant

import java.time.LocalDateTime
import java.time.Duration

fun delayUntilNextRun(now: LocalDateTime, hour: Int, minute: Int): Duration {
    require(hour in 0..23 && minute in 0..59)
    var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return Duration.between(now, next)
}
