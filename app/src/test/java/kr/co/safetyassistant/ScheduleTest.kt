package kr.co.safetyassistant

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class ScheduleTest {
    @Test fun delayedFallbackOnlyRunsOnItsScheduledDay() {
        val due = ZonedDateTime.parse("2026-11-18T09:00+09:00[Asia/Seoul]")
        val dueMillis = due.toInstant().toEpochMilli()
        assertEquals(false, isScheduledFallbackDue(due.minusMinutes(1), dueMillis))
        assertEquals(true, isScheduledFallbackDue(due.plusMinutes(3), dueMillis))
        assertEquals(false, isScheduledFallbackDue(due.plusDays(1), dueMillis))
    }
    @Test fun nextDayWhenTimeHasPassed() {
        assertEquals(ZonedDateTime.parse("2026-11-19T09:00+09:00[Asia/Seoul]"),
            nextScheduledRun(ZonedDateTime.parse("2026-11-18T10:00+09:00[Asia/Seoul]"), 9, 0))
    }
    @Test fun sameDayBeforeTime() {
        assertEquals(ZonedDateTime.parse("2026-11-18T09:00+09:00[Asia/Seoul]"),
            nextScheduledRun(ZonedDateTime.parse("2026-11-18T08:00+09:00[Asia/Seoul]"), 9, 0))
    }
    @Test fun keepsLocalTimeAcrossDaylightSavingChange() {
        assertEquals(ZonedDateTime.parse("2026-03-08T09:00-04:00[America/New_York]"),
            nextScheduledRun(ZonedDateTime.parse("2026-03-07T10:00-05:00[America/New_York]"), 9, 0))
    }
}
