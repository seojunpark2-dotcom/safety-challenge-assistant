package kr.co.safetyassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class RouletteResultTest {
    @Test fun classifiesOnlyNewResultMessages() {
        val before = setOf("룰렛 참여", "3,000포인트로 룰렛 돌리기")
        val messages = mapOf(
            "금일 응모가 마감되었습니다" to "오늘 응모 마감",
            "이미 룰렛을 돌렸습니다" to "이미 참여함",
            "당첨을 축하합니다" to "당첨 확인",
            "미당첨되었습니다" to "미당첨",
            "다시 시도해 주세요" to "룰렛 결과 창 확인"
        )
        for ((message, expected) in messages) {
            assertEquals(expected, rouletteResult(before, before.toList() + listOf(message, "확인")))
        }
    }

    @Test fun recordsAcknowledgementWhenThereIsNoNewMessage() {
        val unchanged = setOf("금일 응모가 마감되었습니다")
        assertEquals("룰렛 결과 창 확인", rouletteResult(unchanged, unchanged.toList() + "확인"))
    }
}
