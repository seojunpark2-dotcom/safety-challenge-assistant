package kr.co.safetyassistant

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.view.accessibility.AccessibilityNodeInfo as Node
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

enum class Task(val title: String) {
    ATTENDANCE("출석"), QUIZ("퀴즈"), POINTS("포인트"), ROULETTE("룰렛")
}

private val costButton = Regex("""(?:3,000|3000)\s*포인트로 룰렛 돌리기""")
private val pointsPattern = Regex("""내\s*포인트\s*([0-9][0-9,]*)""")
private val knownQuestion = "크레인으로 든 물건이 흔들리지 않는다면 그 아래를 잠깐 통과해도 된다."

private fun Node.label() = (text ?: contentDescription)?.toString()
    ?.replace(Regex("\\s+"), " ")?.trim().orEmpty()

private fun Node.nodes(depth: Int = 0): Sequence<Node> = sequence {
    if (depth >= 20) return@sequence
    yield(this@nodes)
    for (i in 0 until childCount) getChild(i)?.let { yieldAll(it.nodes(depth + 1)) }
}

internal suspend fun DiagnosticService.runTask(task: Task, pkg: String, retries: Int) {
    val settings = getSharedPreferences("settings", Context.MODE_PRIVATE)
    val runDate = LocalDate.now()
    val key = "attempt.$runDate.$task"
    fun report(message: String) {
        log("${task.title}: $message")
        if (task == Task.ATTENDANCE) notifyAttendanceResult(message)
    }
    fun reserve(): Boolean = settings.edit().putBoolean(key, true).commit()
    if (task != Task.POINTS && settings.getBoolean(key, false)) {
        report("오늘 이미 시도함 · 대상 앱에서 결과 확인")
        return
    }
    var submitted = false
    var selected = false
    var solvedQuestion: String? = null
    var quizAnswer: Char? = null
    var rouletteOpened = false
    var scrolls = 0
    repeat(12 + retries * 3) {
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
            it.isVisibleToUser && !it.isPassword && !it.isEditable
        }.toList()
        val labels = nodes.map { it.label() }
        val screen = labels.joinToString(" ")
        fun click(test: (String) -> Boolean, before: () -> Boolean = { true }): Boolean {
            val match = nodes.filter { test(it.label()) }.mapNotNull { node ->
                generateSequence(node) { it.parent }.take(4).firstOrNull {
                    it.packageName?.toString() == pkg && it.isVisibleToUser && it.isEnabled && it.isClickable
                }
            }.distinct().singleOrNull() ?: return false
            return before() && match.performAction(Node.ACTION_CLICK)
        }
        fun exact(label: String) = click({ it == label })
        fun scroll(action: Int): Boolean {
            if (scrolls++ >= 7) return false
            val target = nodes.filter { n -> n.actionList.any { it.id == action } }
                .singleOrNull() ?: return false
            return target.performAction(action)
        }
        if ("하루동안 보지 않기" in screen && "닫기" in labels) {
            exact("닫기")
            return@repeat
        }
        if ("네트워크 오류" in screen || "인터넷 연결을 확인" in screen) {
            report("네트워크 오류")
            return
        }
        when (task) {
            Task.ATTENDANCE -> {
                if ("출석체크 완료" in screen) {
                    exact("확인")
                    reserve()
                    report("완료")
                    return
                }
                if (!submitted && "출석체크" in screen && "체크" in labels) {
                    submitted = click({ it == "체크" }, ::reserve)
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
                    } else if (!submitted) {
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
            Task.POINTS, Task.ROULETTE -> {
                if ("금일 응모가 마감되었습니다" in screen) {
                    if (task == Task.ROULETTE) reserve()
                    report("오늘 응모 마감")
                    return
                }
                val balance = pointsPattern.findAll(screen).mapNotNull {
                    it.groupValues[1].replace(",", "").toIntOrNull()
                }.distinct().toList().singleOrNull()
                if ("룰렛 참여" in labels && balance != null) {
                    val clickCost = labels.any(costButton::matches)
                    if (task == Task.POINTS) {
                        report("%,d포인트".format(balance))
                        return
                    }
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
                    if (balance < settings.getInt("threshold", 3000).coerceAtLeast(3000)) {
                        report("잔액 부족 · %,d포인트".format(balance))
                        return
                    }
                    if (!settings.getBoolean("spendConsent", false) ||
                        settings.getInt("spendLimit", 0) < 3000
                    ) {
                        report("3,000포인트 사용 동의와 일일 한도 필요")
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
    report(if (submitted) "결과 확인 필요 · 오늘 재시도 차단" else "화면 요소 찾기 시간 초과")
}
