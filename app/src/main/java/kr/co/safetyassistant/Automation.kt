package kr.co.safetyassistant

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.PowerManager
import android.view.accessibility.AccessibilityNodeInfo as Node
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

enum class Task(val title: String) {
    ATTENDANCE("출석"), QUIZ("퀴즈"), ROULETTE("룰렛")
}

private const val ROULETTE_COST_POINTS = 3000
private val costButton = Regex("""(?:3,000|3000)\s*포인트로 룰렛 돌리기""")
private val knownQuestion = "크레인으로 든 물건이 흔들리지 않는다면 그 아래를 잠깐 통과해도 된다."

private fun Node.label() = (text?.toString()?.takeIf(String::isNotBlank) ?: contentDescription?.toString())
    ?.replace(Regex("\\s+"), " ")?.trim().orEmpty()

private fun Node.nodes(depth: Int = 0): Sequence<Node> = sequence {
    if (depth >= 20) return@sequence
    yield(this@nodes)
    for (i in 0 until childCount) getChild(i)?.let { yieldAll(it.nodes(depth + 1)) }
}

private const val SCREEN_SEARCH_RETRIES = 2

internal suspend fun DiagnosticService.runTask(task: Task, pkg: String, onNoticeBlocked: () -> Unit) {
    val settings = getSharedPreferences("settings", Context.MODE_PRIVATE)
    val runDate = LocalDate.now()
    val key = "attempt.$runDate.$task"
    fun report(message: String) {
        log("${task.title}: $message")
        if (task == Task.ATTENDANCE) notifyAttendanceResult(message)
    }
    fun reserve(): Boolean = settings.edit().putBoolean(key, true).commit()
    if (settings.getBoolean(key, false)) {
        report("오늘 이미 시도함 · 대상 앱에서 결과 확인")
        return
    }
    var submitted = false
    var noticeVisible = false
    var noticeSelectionRequested = false
    var noticesClosed = 0
    var lastClosedCheckbox: Node? = null
    var selected = false
    var solvedQuestion: String? = null
    var quizAnswer: Char? = null
    var rouletteOpened = false
    var scrolls = 0
    repeat(12 + SCREEN_SEARCH_RETRIES * 3) {
        delay(650)
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(PowerManager::class.java).isInteractive ||
            LocalDate.now() != runDate
        ) {
            report("잠금 또는 날짜 변경으로 중지")
            return
        }
        val root = rootInActiveWindow
        if (root?.packageName?.toString() != pkg) {
            removeCurtain()
            return@repeat
        }
        showCurtain()
        val nodes = root.nodes().take(500).filter {
            it.isVisibleToUser && !it.isPassword && !it.isEditable &&
                !Rect().also(it::getBoundsInScreen).isEmpty
        }.toList()
        val labels = nodes.map { it.label() }
        val screen = labels.joinToString(" ")
        fun Node.usable() = packageName?.toString() == pkg && isVisibleToUser && isEnabled &&
            !isPassword && !isEditable
        fun Node.clickTarget() = generateSequence(this) { it.parent }.take(4).firstOrNull {
            it.usable() && (it.isClickable || it.actionList.any { action -> action.id == Node.ACTION_CLICK })
        }
        fun click(test: (String) -> Boolean, before: () -> Boolean = { true }): Boolean {
            val match = nodes.filter { test(it.label()) }.mapNotNull { it.clickTarget() }
                .distinct().singleOrNull() ?: return false
            return before() && match.performAction(Node.ACTION_CLICK)
        }
        fun exact(label: String) = click({ it == label })
        fun Node.bounds() = Rect().also { getBoundsInScreen(it) }
        val canTap = !settings.getBoolean("privacyCurtain", false) &&
            (serviceInfo.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0
        fun tapControl(control: Node): Boolean {
            if (!canTap || !control.refresh() || !control.usable() ||
                rootInActiveWindow?.let { it.packageName?.toString() == pkg && it.windowId == control.windowId } != true
            ) return false
            val bounds = control.bounds()
            if (!bounds.intersect(root.bounds())) return false
            val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 80)
            return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        }
        fun scroll(action: Int): Boolean {
            if (scrolls++ >= 7) return false
            val target = nodes.filter { n -> n.actionList.any { it.id == action } }
                .singleOrNull() ?: return false
            return target.performAction(action)
        }
        val noticeLabels = nodes.filter {
            it.packageName?.toString() == pkg &&
                (isNoticeOptOut(it.label()) || isNoticeOptOut(it.contentDescription?.toString()?.trim().orEmpty()))
        }
        noticeVisible = noticeLabels.isNotEmpty()
        if (noticeVisible) {
            if (noticesClosed >= 2) return@repeat
            val closing = nodes.filter { it.usable() && it.label() == "닫기" }
                .filter { close -> noticeLabels.any { close.bounds().centerY() > it.bounds().centerY() } }
                .distinct().singleOrNull() ?: return@repeat
            val checkbox = noticeLabels.mapNotNull { label ->
                generateSequence(label) { it.parent }.take(3)
                    .firstOrNull { it.usable() && it.isCheckable }
                    ?: label.parent?.let { row ->
                        row.nodes().take(24).filter { candidate ->
                            if (!candidate.usable()) return@filter false
                            if (candidate.isCheckable) return@filter true
                            val control = candidate.bounds()
                            val text = label.bounds()
                            candidate.className?.toString() == "android.widget.Image" &&
                                candidate.label().isEmpty() && !control.isEmpty &&
                                control.height() in (text.height() / 2)..(text.height() * 2) &&
                                isBesideNoticeLabel(text.left until text.right, text.top until text.bottom,
                                    control.left until control.right, control.centerY())
                        }.singleOrNull()
                    }
                    ?: nodes.singleOrNull { candidate ->
                        if (!candidate.usable() || !candidate.isCheckable) return@singleOrNull false
                        val control = candidate.bounds()
                        val text = label.bounds()
                        !control.isEmpty && !text.isEmpty &&
                            isBesideNoticeLabel(
                                text.left until text.right, text.top until text.bottom,
                                control.left until control.right, control.centerY()
                            )
                    }
            }.distinct().singleOrNull()
            val checkboxBounds = checkbox?.bounds()
            val observedChecked = checkbox?.let {
                when {
                    it.isCheckable -> it.isChecked
                    settings.getBoolean("privacyCurtain", false) -> null
                    else -> readNoticeImage(it.bounds(), pkg, it.windowId)
                }
            }
            if (observedChecked == null || checkbox == null || !checkbox.refresh() || !closing.refresh() ||
                !checkbox.usable() || !closing.usable() || closing.label() != "닫기" ||
                closing.windowId != checkbox.windowId || checkbox.bounds() != checkboxBounds ||
                rootInActiveWindow?.let { it.packageName?.toString() == pkg && it.windowId == checkbox.windowId } != true
            ) return@repeat
            val checked = if (checkbox.isCheckable) checkbox.isChecked else observedChecked
            if (lastClosedCheckbox != null) {
                if (checkbox == lastClosedCheckbox && checked) return@repeat
                noticesClosed++
                lastClosedCheckbox = null
                if (noticesClosed >= 2) return@repeat
            }
            val closeTarget = closing.clickTarget()
            val checkboxTarget = checkbox.clickTarget()
            if (checkboxTarget != null && checkboxTarget == closeTarget) return@repeat
            if (checked) {
                if (closeTarget?.performAction(Node.ACTION_CLICK) == true || tapControl(closing)) {
                    noticeSelectionRequested = false
                    lastClosedCheckbox = checkbox
                }
            } else if (!noticeSelectionRequested) {
                noticeSelectionRequested = (checkbox.isCheckable &&
                    checkboxTarget?.performAction(Node.ACTION_CLICK) == true) ||
                    tapControl(checkbox)
            }
            return@repeat
        }
        noticeSelectionRequested = false
        if (lastClosedCheckbox != null) noticesClosed++
        lastClosedCheckbox = null
        if ("네트워크 오류" in screen || "인터넷 연결을 확인" in screen) {
            report("네트워크 오류")
            return
        }
        when (task) {
            Task.ATTENDANCE -> {
                if (isAttendanceComplete(screen)) {
                    exact("확인")
                    reserve()
                    report("완료")
                    return
                }
                if (!submitted && hasAttendanceSection(screen) && labels.any(::isAttendanceButton)) {
                    val label = nodes.singleOrNull { it.usable() && isAttendanceButton(it.label()) }
                    val target = label?.let { text ->
                        val area = text.bounds()
                        val card = text.parent
                        val button = card?.let {
                            (0 until it.childCount).mapNotNull(it::getChild).singleOrNull { child ->
                                val bounds = child.bounds()
                                child.usable() && child.isClickable && child.label().isEmpty() &&
                                    child.className?.toString() == "android.widget.Button" &&
                                    !bounds.isEmpty && bounds.bottom <= area.top &&
                                    area.centerX() in bounds.left until bounds.right
                            }
                        }
                        button ?: text.clickTarget()
                    }
                    val control = target ?: label?.takeIf { canTap }
                    if (control != null && control.refresh() && control.usable() &&
                        rootInActiveWindow?.let {
                            it.packageName?.toString() == pkg && it.windowId == control.windowId
                        } == true && reserve()
                    ) {
                        submitted = target?.performAction(Node.ACTION_CLICK) == true || tapControl(control)
                    }
                    if (!submitted && settings.getBoolean(key, false)) {
                        report("클릭 결과 확인 필요")
                        return
                    }
                } else if (!submitted && "오늘의 퀴즈" in screen) {
                    scroll(Node.ACTION_SCROLL_BACKWARD)
                }
            }
            Task.QUIZ -> {
                if ("오늘의 퀴즈 정답" in screen || "퀴즈 참여 완료" in screen) {
                    if ("오늘의 퀴즈 정답" in screen) exact("확인")
                    reserve()
                    report("참여 완료")
                    return
                }
                if ("오늘의 퀴즈" in screen && "제출하기" in labels) {
                    if (submitted) return@repeat
                    val question = quizQuestion(labels)
                    if (question == null || (solvedQuestion != null && question != solvedQuestion)) {
                        report("문장 전체를 확인할 수 없거나 문항이 바뀜 · 수동 확인 필요")
                        return
                    }
                    if (solvedQuestion == null) {
                        solvedQuestion = question
                        if (question == knownQuestion) quizAnswer = 'X'
                        else {
                            val result = try {
                                val apiKey = QuizKey.load(this)
                                if (apiKey == null) QuizVerdict(null, "Gemini 키 설정 필요")
                                else solveQuiz(question, apiKey)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                QuizVerdict(null, "Gemini 연결 또는 응답 처리 실패")
                            }
                            if (result.answer == null) {
                                report("수동 확인 필요 · $question · ${result.note}")
                                return
                            }
                            quizAnswer = result.answer
                            report("AI 판단 · ${result.note}")
                            return@repeat
                        }
                    }
                    if (!selected) {
                        val answer = quizAnswer ?: return
                        selected = click({ isChoice(it, answer) })
                    } else if (!submitted && nodes.any { it.usable() && it.label() == "제출하기" }) {
                        submitted = click({ it == "제출하기" }, ::reserve)
                        if (!submitted && settings.getBoolean(key, false)) {
                            report("제출 결과 확인 필요")
                            return
                        }
                    }
                } else if ("출석체크" in screen || "이번 주차 챌린지" in screen) {
                    if (!submitted) scroll(Node.ACTION_SCROLL_FORWARD)
                }
            }
            Task.ROULETTE -> {
                if ("금일 응모가 마감되었습니다" in screen) {
                    reserve()
                    report("오늘 응모 마감")
                    return
                }
                val balance = rouletteBalance(screen)
                if ("룰렛 참여" in labels && balance != null) {
                    val clickCost = labels.any(costButton::matches)
                    if (submitted) {
                        if ("당첨을 축하" in screen) {
                            report("당첨 확인 · 화면 잔액 %,d포인트".format(balance))
                            return
                        }
                        return@repeat
                    }
                    if (!clickCost) {
                        report("룰렛 비용 또는 참여 상태 확인 필요")
                        return
                    }
                    if (balance < ROULETTE_COST_POINTS) {
                        report("잔액 부족 · %,d포인트".format(balance))
                        return
                    }
                    submitted = click(costButton::matches, ::reserve)
                    if (!submitted && settings.getBoolean(key, false)) {
                        report("룰렛 요청 결과 확인 필요")
                        return
                    }
                } else if (!rouletteOpened && "출석체크" in screen) {
                    rouletteOpened = click({ it == "룰렛 참여" || it == "돌리면 당첨! 룰렛 참여" })
                } else if (!rouletteOpened && "오늘의 퀴즈" in screen) {
                    scroll(Node.ACTION_SCROLL_BACKWARD)
                }
            }
        }
    }
    if (noticeVisible) onNoticeBlocked()
    report(when {
        submitted -> "결과 확인 필요 · 오늘 재시도 차단"
        noticeVisible -> "공지 체크 또는 닫기 결과 확인 필요"
        else -> "화면 요소 찾기 시간 초과"
    })
}
