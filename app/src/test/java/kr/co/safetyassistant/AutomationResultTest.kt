package kr.co.safetyassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationResultTest {
    @Test fun listsFailedAndUnselectedTasksSeparately() {
        val summary = summarizeAutomation(
            listOf(Task.ATTENDANCE, Task.QUIZ),
            mapOf(Task.ATTENDANCE to TaskResult("오늘 이미 출석했어요.", true)),
            "실행 중지"
        )
        assertEquals(
            "출석 성공 · 오늘 이미 출석했어요.\n퀴즈 실패 · 실행 중지\n룰렛 미실행",
            summary
        )
    }
}
