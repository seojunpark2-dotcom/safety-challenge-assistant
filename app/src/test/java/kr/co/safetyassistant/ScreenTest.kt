package kr.co.safetyassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTest {
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
