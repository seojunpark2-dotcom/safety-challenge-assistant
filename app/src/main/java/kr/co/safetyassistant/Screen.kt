package kr.co.safetyassistant

private val balance = Regex("""내\s*포인트\s*([0-9]+(?:,[0-9]{3})*)(?![0-9,])""")

internal fun rouletteBalance(screen: String): Int? = balance.findAll(screen).mapNotNull {
    it.groupValues[1].replace(",", "").toIntOrNull()
}.distinct().toList().singleOrNull()

private val noticeOptOut = Regex("""(?:오늘\s*)?하루\s*(?:동안\s*)?보지\s*않기""")
private val attendanceSection = Regex("""출석\s*체크""")
private val attendanceComplete = Regex("""출석\s*체크\s*완료""")
private val attendanceButton = Regex("""체크|출석\s*(?:하기|체크\s*하기)""")

internal fun isNoticeOptOut(label: String) = noticeOptOut.matches(label)

internal fun isBesideNoticeLabel(labelX: IntRange, labelY: IntRange, checkboxX: IntRange, checkboxY: Int): Boolean {
    val margin = (labelY.last - labelY.first + 1) * 2
    return checkboxY in labelY &&
        (checkboxX.last in (labelX.first - margin) until labelX.first ||
            checkboxX.first in (labelX.last + 1)..(labelX.last + margin))
}

internal fun hasAttendanceSection(screen: String) = attendanceSection.containsMatchIn(screen)
internal fun isAttendanceComplete(screen: String) = attendanceComplete.containsMatchIn(screen)
internal fun isAttendanceButton(label: String) = attendanceButton.matches(label)

private val rouletteCooldown = Regex("""룰렛.*참여\s*후\s*3\s*일\s*(?:뒤|후)에?\s*다시\s*참여""")
private val rouletteAlreadyPlayed = Regex("""이미\s*(?:룰렛|응모)(?:에|을|를)?\s*(?:참여했|참여하셨|참여 완료|돌렸)""")

internal fun isRouletteAlreadyPlayed(screen: String): Boolean =
    rouletteCooldown.containsMatchIn(screen) || rouletteAlreadyPlayed.containsMatchIn(screen)

internal fun isQuizComplete(labels: List<String>): Boolean {
    val screen = labels.joinToString(" ")
    if ("오늘의 퀴즈 정답" in screen || "퀴즈 참여 완료" in screen) return true
    val heading = labels.indexOf("오늘의 퀴즈")
    return heading >= 0 && labels.drop(heading + 1).any { it == "해설 보기" }
}

fun describeScreen(labels: List<String>): String {
    val text = labels.joinToString(" ").replace(Regex("\\s+"), " ")
    return when {
        "금일 응모가 마감되었습니다" in text -> "룰렛: 오늘 응모 마감"
        isRouletteAlreadyPlayed(text) -> "룰렛: 이미 참여함"
        isAttendanceComplete(text) -> "출석: 완료"
        "오늘의 퀴즈 정답" in text -> "퀴즈: 정답"
        isQuizComplete(labels) -> "퀴즈: 참여 완료"
        labels.any(::isNoticeOptOut) && "닫기" in labels -> "공지: 닫기 버튼 확인"
        "내 포인트" in text && "룰렛 참여" in text -> {
            val points = rouletteBalance(text)
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
