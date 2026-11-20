package kr.co.safetyassistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context
import android.content.Intent
import android.app.KeyguardManager
import android.os.PowerManager
import android.os.Build
import android.os.SystemClock
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*
import java.time.LocalDateTime

class DiagnosticService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var run: Job? = null
    override fun onServiceConnected() {
        instance = this
    }

    fun log(message: String) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val row = "${LocalDateTime.now().withNano(0)} · $message"
        val previous = prefs.getString("history", "").orEmpty()
        prefs.edit().putString("history", (listOf(row) + previous.lines()).take(30).joinToString("\n")).apply()
    }

    private var wakeId = 0L
    private var wakeResult: CompletableDeferred<Boolean>? = null
    internal var wakeActivity: WakeActivity? = null

    internal fun isWakePending(id: Long) = id != 0L && id == wakeId && wakeResult?.isActive == true

    internal fun completeWake(id: Long, ready: Boolean) {
        if (isWakePending(id)) wakeResult?.complete(ready)
    }

    private suspend fun wakeForSchedule(): Boolean {
        val id = SystemClock.elapsedRealtimeNanos()
        val result = CompletableDeferred<Boolean>()
        wakeId = id
        wakeResult = result
        return try {
            startActivity(Intent(this, WakeActivity::class.java).apply {
                putExtra("wakeRequest", id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            })
            withTimeoutOrNull(30_000) {
                if (!result.await()) return@withTimeoutOrNull false
                while (!deviceReady()) delay(100)
                true
            } == true
        } finally {
            wakeId = 0L
            wakeResult = null
            wakeActivity?.finishAndRemoveTask()
            wakeActivity = null
        }
    }

    private fun deviceReady() = getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    fun start(tasks: List<Task>, scheduled: Boolean = false): Boolean {
        if (tasks.isEmpty() || run?.isActive == true) return false
        val launch = packageManager.getLaunchIntentForPackage(TARGET_PACKAGE) ?: return false
        val screenWasOn = getSystemService(PowerManager::class.java).isInteractive
        val ready = deviceReady()
        if (!ready && !scheduled) return false
        val previousPackage = if (ready) rootInActiveWindow?.packageName?.toString() else null
        val returnApp = previousPackage?.takeUnless {
            it == TARGET_PACKAGE || it == packageName || it == "com.android.systemui"
        }
        run = scope.launch {
            var opened = false
            var noticeBlocked = false
            try {
                if (!ready && !wakeForSchedule()) {
                    val message = "예약 실행을 시작하지 못했어요 · 화면 깨우기 또는 잠금 해제 필요"
                    log(message)
                    notifyAutomationStatus(message)
                    return@launch
                }
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                startActivity(launch)
                opened = true
                log("실행 시작")
                for (task in Task.entries.filter { it in tasks }) {
                    activeTask = task
                    runTask(task, TARGET_PACKAGE) { noticeBlocked = true }
                    if (noticeBlocked) break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("실행 실패 · ${e.javaClass.simpleName}")
                if (Task.ATTENDANCE in tasks && (activeTask == null || activeTask == Task.ATTENDANCE)) {
                    notifyAttendanceResult("출석 실행 실패 · 대상 앱과 접근성 설정을 확인하세요.")
                }
            } finally {
                removeCurtain()
                activeTask = null
                if (opened && deviceReady() && (scheduled || !noticeBlocked)) {
                    restoreDisplay(returnApp, screenWasOn)
                }
            }
        }
        return true
    }

    private fun restoreDisplay(previous: String?, screenWasOn: Boolean) {
        // Do not steal focus again if the user has already switched to another app.
        if (rootInActiveWindow?.packageName?.toString() != TARGET_PACKAGE) return
        MainActivity.removeOwnTask()
        if (previous != null) {
            val restored = runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    packageManager.getLaunchIntentSenderForPackage(previous).sendIntent(this, 0, null, null, null)
                } else {
                    val previousIntent = packageManager.getLaunchIntentForPackage(previous) ?: return@runCatching false
                    startActivity(previousIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                true
            }.getOrDefault(false)
            if (restored) return
        }
        performGlobalAction(GLOBAL_ACTION_HOME)
        if (!screenWasOn && Build.VERSION.SDK_INT >= 28) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    private var activeTask: Task? = null
    private var curtain: View? = null
    private val windows get() = getSystemService(WindowManager::class.java)

    internal fun showCurtain() {
        if (curtain != null || !getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("privacyCurtain", false)) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            setBackgroundColor(Color.rgb(13, 40, 51))
            addView(TextView(context).apply {
                text = "프라이버시 커튼"
                textSize = 22f
                setTextColor(Color.WHITE)
            })
            addView(Button(context).apply {
                text = "실행 중지"
                setOnClickListener { stop() }
            })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE
        )
        runCatching { windows.addView(panel, params) }
            .onSuccess { curtain = panel }
            .onFailure { log("프라이버시 커튼을 표시하지 못했어요.") }
    }

    internal fun removeCurtain() {
        val panel = curtain ?: return
        curtain = null
        runCatching { windows.removeViewImmediate(panel) }
    }

    fun stop() {
        run?.cancel()
        removeCurtain()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != TARGET_PACKAGE) {
            if (pkg != packageName && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) removeCurtain()
            return
        }
        lastPackage = pkg
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val lines = mutableListOf<String>()
        val labels = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (lines.size >= 150 || depth > 15) return
            if (node.isVisibleToUser && !node.isEditable && !node.isPassword) {
                (node.text ?: node.contentDescription)?.toString()?.trim()?.take(500)?.let(labels::add)
            }
            lines.add("${"  ".repeat(depth)}${node.className} id=${node.viewIdResourceName ?: "-"} clickable=${node.isClickable} scrollable=${node.isScrollable}")
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, depth + 1); it.recycle() }
        }
        visit(root, 0)
        root.recycle()
        structure = describeScreen(labels) + "\n\n" + lines.joinToString("\n")
    }
    override fun onInterrupt() { stop() }
    override fun onDestroy() { stop(); scope.cancel(); instance = null; super.onDestroy() }
    companion object {
        @Volatile var instance: DiagnosticService? = null
        const val TARGET_PACKAGE = "kr.or.kosha.safetychallenge"
        @Volatile var lastPackage: String = "아직 감지된 앱 없음"
        @Volatile var structure: String = "화면을 확인하지 않았습니다."
    }
}
