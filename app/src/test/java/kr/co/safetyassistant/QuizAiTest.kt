package kr.co.safetyassistant

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

    @Test fun permitsOnlyCompletedVerdictsCitingProvidedMaterial() {
        val refs = referencesFor("크레인으로 인양 중인 작업물 아래를 지나도 된다.")
        assertEquals(listOf(146), refs.map { it.article })
        val verdict = parseQuizVerdict("X | 146 | 인양 하물 아래 출입을 통제해야 한다.", "STOP", refs)
        assertEquals('X', verdict.answer)
        assertTrue(verdict.note.contains(refs.single().url))
        assertNull(parseQuizVerdict("X | 32 | 인양 하물 아래 출입을 통제해야 한다.", "STOP", refs).answer)
        assertNull(parseQuizVerdict("X | 146 | 인양 하물 아래 출입을 통제해야 한다.", "MAX_TOKENS", refs).answer)
        assertNull(parseQuizVerdict("UNKNOWN", "STOP", refs).answer)
        assertTrue(referencesFor("사무실 비상구 표지의 색을 고르시오.").isEmpty())
    }

    @Test fun acceptsInferenceOnlyWhenNoReferencesWereSent() {
        val reason = "중요한 문구를 가리면 표지를 읽을 수 없다."
        val question = "비상구 표지를 가려도 그 내용을 읽을 수 있다."
        val refs = referencesFor(question)
        assertTrue(refs.isEmpty())
        val verdict = parseQuizVerdict("X | NONE | $reason", "STOP", refs)
        assertEquals('X', verdict.answer)
        assertTrue(verdict.note.contains("공식 근거 없음"))
        assertNull(parseQuizVerdict("X | 32 | $reason", "STOP", refs).answer)
        assertNull(parseQuizVerdict("UNKNOWN", "STOP", refs).answer)
        assertNull(parseQuizVerdict("X | NONE | $reason", "MAX_TOKENS", refs).answer)
        assertNull(parseQuizVerdict("X | NONE | $reason", "STOP", referencesFor("안전모")).answer)
    }

    @Test fun quotaErrorNeverReturnsAnAnswer() {
        val verdict = quizHttpFailure(429)
        assertNull(verdict.answer)
        assertTrue(verdict.note.contains("한도"))
    }
}
