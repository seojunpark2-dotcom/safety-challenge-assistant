package kr.co.safetyassistant

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class QuizAiTest {
    @Test fun extractsWholeQuestionOnlyBetweenTitleAndChoices() {
        val question = "안전 장비는 작업 전에 항상 점검해야 한다."
        val labels = listOf("홈", "오늘의 퀴즈", question, "O 맞아요", "X 틀려요", "제출하기")
        assertEquals(question, quizQuestion(labels))
        assertNull(quizQuestion(labels - "X 틀려요"))
        assertNull(quizQuestion(labels.toMutableList().apply { add(3, "다른 문구") }))
        assertTrue(isChoice("O 맞아요", 'O'))
        assertFalse(isChoice("X 틀려요", 'O'))
    }

    @Test fun rejectsUnofficialCitationHosts() {
        assertTrue(officialQuizSource(URI("https://www.kosha.or.kr/example")))
        assertFalse(officialQuizSource(URI("https://kosha.or.kr.example.com/")))
        assertFalse(officialQuizSource(URI("https://example.com@kosha.or.kr/")))
        assertFalse(officialQuizSource(URI("http://law.go.kr/")))
    }
}
