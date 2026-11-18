package kr.co.safetyassistant

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ScheduleTest {
    @Test fun nextDayWhenTimeHasPassed() {
        assertEquals(23L, delayUntilNextRun(LocalDateTime.of(2026, 11, 18, 10, 0), 9, 0).toHours())
    }
    @Test fun sameDayBeforeTime() {
        assertEquals(60L, delayUntilNextRun(LocalDateTime.of(2026, 11, 18, 8, 0), 9, 0).toMinutes())
    }
}
