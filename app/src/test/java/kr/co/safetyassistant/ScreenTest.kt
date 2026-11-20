package kr.co.safetyassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenTest {
    @Test fun retainsTheVerifiedQuizQuestionWhenScrollingClipsItsHeading() {
        val question = "쌓인 물건이 불안정해 보이면 먼저 무너지지 않도록 조치해야 한다."
        assertEquals(question, quizQuestion(listOf(question, "제출하기"), question))
        assertNull(quizQuestion(listOf("제출하기"), question))
        assertNull(quizQuestion(listOf("오늘의 퀴즈", question, "제출하기"), question))
    }

    @Test fun distinguishesCompletedAttendanceFromAnEmptyCalendarSlot() {
        val white = 0xffffffff.toInt()
        val green = 0xff91dc46.toInt()
        val gray = 0xffededed.toInt()
        assertTrue(attendanceMarkChecked(IntArray(100) { if (it < 52) green else white }))
        assertFalse(attendanceMarkChecked(IntArray(100) { if (it < 52) gray else white }))
        assertFalse(attendanceMarkChecked(IntArray(100) { if (it < 20) green else white }))
    }

    @Test fun recognizesTheCompletedQuizBeforeConsideringSubmission() {
        val quiz = listOf("오늘의 퀴즈", "맞아요", "틀려요", "제출하기")
        assertTrue(isQuizComplete(quiz + "해설 보기"))
        assertFalse(isQuizComplete(quiz))
        assertFalse(isQuizComplete(listOf("해설 보기") + quiz))
    }

    @Test fun recognizesTheRouletteCooldownShownInTheAttachment() {
        val dialog = "룰렛은 한번 참여 후 3일뒤에 다시 참여할 수 있습니다."
        assertTrue(isRouletteAlreadyPlayed(dialog))
        assertTrue(isRouletteAlreadyPlayed("이미 룰렛에 참여했습니다."))
        assertFalse(isRouletteAlreadyPlayed("돌리면 당첨! 룰렛 참여"))
        assertEquals("이미 참여함", rouletteResult(emptySet(), listOf(dialog, "확인")))
    }

    @Test fun readsTheGrayAndBlueNoticeArtwork() {
        val white = 0xffffffff.toInt()
        val gray = 0xffcacaca.toInt()
        val blue = 0xff04b3df.toInt()
        assertEquals(false, noticeImageChecked(IntArray(100) { if (it < 57) gray else white }))
        assertEquals(true, noticeImageChecked(IntArray(100) { if (it < 57) blue else white }))
        assertNull(noticeImageChecked(IntArray(100) { white }))
        assertNull(noticeImageChecked(IntArray(100) { if (it < 50) blue else gray }))
    }

    @Test fun recognizesSeparateRouletteBalanceNodes() {
        val labels = listOf("룰렛 참여", "내 포인트", "4200", "3000포인트로 룰렛 돌리기")
        assertEquals(4200, rouletteBalance(labels.joinToString(" ")))
        assertNull(rouletteBalance("내 포인트 4200 내 포인트 5000"))
        assertEquals("룰렛: 4,200포인트 · 3,000포인트 버튼 확인", describeScreen(labels))
    }

    @Test fun matchesOnlyCheckboxesBesideTheNoticeLabel() {
        val labelX = 100..270
        val labelY = 200..219
        assertTrue(isBesideNoticeLabel(labelX, labelY, 75..99, 210))
        assertTrue(isBesideNoticeLabel(labelX, labelY, 271..295, 210))
        assertFalse(isBesideNoticeLabel(labelX, labelY, 75..99, 240))
        assertFalse(isBesideNoticeLabel(labelX, labelY, 330..355, 210))
        assertFalse(isBesideNoticeLabel(labelX, labelY, 98..110, 210))
    }

    @Test fun recognizesTheNoticeLabelShownOnThePhone() {
        assertEquals("공지: 닫기 버튼 확인", describeScreen(listOf("오늘 하루 보지 않기", "닫기")))
        assertEquals("공지: 닫기 버튼 확인", describeScreen(listOf("오늘하루보지않기", "닫기")))
        assertEquals("알 수 없는 화면", describeScreen(listOf("오늘 하루 소식 보기", "닫기")))
    }

    @Test fun recognizesSpacedNoticeAndAttendanceControls() {
        assertEquals("공지: 닫기 버튼 확인", describeScreen(listOf("하루 동안 보지 않기", "닫기")))
        assertEquals("출석: 체크 버튼 확인", describeScreen(listOf("출석 체크", "출석하기")))
        assertEquals("알 수 없는 화면", describeScreen(listOf("출석 체크", "내용 확인")))
    }
}
