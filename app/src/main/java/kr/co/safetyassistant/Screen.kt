package kr.co.safetyassistant

private val balance = Regex("""내\s*포인트\s*([0-9]+(?:,[0-9]{3})*)(?![0-9,])""")

private val noticeOptOut = Regex("""하루\s*동안\s*보지\s*않기""")
private val attendanceSection = Regex("""출석\s*체크""")
private val attendanceComplete = Regex("""출석\s*체크\s*완료""")
private val attendanceButton = Regex("""체크|출석\s*(?:하기|체크\s*하기)""")

internal fun isNoticeOptOut(label: String) = noticeOptOut.matches(label)
internal fun hasAttendanceSection(screen: String) = attendanceSection.containsMatchIn(screen)
internal fun isAttendanceComplete(screen: String) = attendanceComplete.containsMatchIn(screen)
internal fun isAttendanceButton(label: String) = attendanceButton.matches(label)

fun describeScreen(labels: List<String>): String {
    val text = labels.joinToString(" ").replace(Regex("\\s+"), " ")
    return when {
        "금일 응모가 마감되었습니다" in text -> "룰렛: 오늘 응모 마감"
        isAttendanceComplete(text) -> "출석: 완료"
        "오늘의 퀴즈 정답" in text -> "퀴즈: 정답"
        "퀴즈 참여 완료" in text -> "퀴즈: 참여 완료"
        labels.any(::isNoticeOptOut) && "닫기" in labels -> "공지: 닫기 버튼 확인"
        "내 포인트" in text && "룰렛 참여" in text -> {
            val points = balance.findAll(text).mapNotNull {
                it.groupValues[1].replace(",", "").toIntOrNull()
            }.distinct().toList().singleOrNull()
            when {
                points == null -> "룰렛: 잔액 확인 필요"
                points < 3000 -> "룰렛: %,d포인트 · 잔액 부족".format(points)
                !Regex("""(?:3000|3,000)\s*포인트로 룰렛 돌리기""").containsMatchIn(text) ->
                    "룰렛: %,d포인트 · 비용 또는 참여 상태 확인 필요".format(points)
                else -> "룰렛: %,d포인트 · 3,000포인트 버튼 확인".format(points)
            }
        }
        "오늘의 퀴즈" in text && "제출하기" in text -> "퀴즈: 답과 제출 버튼 확인"
        hasAttendanceSection(text) && labels.any(::isAttendanceButton) -> "출석: 체크 버튼 확인"
        else -> "알 수 없는 화면"
    }
}
