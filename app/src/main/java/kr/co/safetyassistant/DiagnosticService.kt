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
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.*

class DiagnosticService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var run: Job? = null
    internal val automationRunning get() = run?.isActive == true
    override fun onServiceConnected() {
        instance = this
    }

    fun log(message: String) = recordHistory(message)

    internal suspend fun awaitAutomation() {
        run?.join()
    }

    private var wakeId = 0L
    private var wakeResult: CompletableDeferred<Boolean>? = null
    internal var wakeActivity: WakeActivity? = null

    internal fun isWakePending(id: Long) = id != 0L && id == wakeId && wakeResult?.isActive == true

    internal fun completeWake(id: Long, ready: Boolean) {
        if (isWakePending(id)) wakeResult?.complete(ready)
    }

    private suspend fun scheduledLaunchFailure(launch: Intent): String? {
        if (deviceReady() && targetIsActive()) return null
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        val wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:scheduledLaunch")
        val id = SystemClock.elapsedRealtimeNanos()
        val result = CompletableDeferred<Boolean>()
        wakeId = id
        wakeResult = result
        return try {
            wakeLock.acquire(45_000)
            if (deviceReady()) {
                startActivity(launch)
                val opened = withTimeoutOrNull(3_000) {
                    while (deviceReady() && !targetIsActive()) delay(150)
                    deviceReady() && targetIsActive()
                } == true
                if (opened) return null
            }
            startActivity(Intent(this, WakeActivity::class.java).apply {
                putExtra("wakeRequest", id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            })
            val ready = withTimeoutOrNull(30_000) {
                if (!result.await()) return@withTimeoutOrNull false
                while (!deviceReady()) delay(100)
                true
            } == true
            if (!ready) return when {
                !power.isInteractive -> "화면을 켜지 못했어요."
                keyguard.isKeyguardLocked && keyguard.isKeyguardSecure ->
                    "기기의 잠금 인증이 완료되지 않았어요."
                keyguard.isKeyguardLocked -> "스와이프 잠금 화면을 해제하지 못했어요."
                else -> "예약 실행 준비 화면을 열지 못했어요."
            }
            val activity = wakeActivity ?: return "예약 실행 준비 화면이 닫혔어요."
            activity.startActivity(launch)
            val opened = withTimeoutOrNull(12_000) {
                while (!deviceReady() || !targetIsActive()) delay(150)
                true
            } == true
            if (opened) null else "대상 앱이 제한 시간 안에 열리지 않았어요."
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            wakeId = 0L
            wakeResult = null
            wakeActivity?.finishAndRemoveTask()
            wakeActivity = null
        }
    }

    private fun deviceReady() = getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun targetIsActive(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            root.packageName?.toString() == TARGET_PACKAGE
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    fun start(tasks: List<Task>, scheduledKey: String? = null): Boolean {
        val scheduled = scheduledKey != null
        if (tasks.isEmpty() || automationRunning) return false
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
            val results = mutableMapOf<Task, TaskResult>()
            var unfinishedReason = "실행을 완료하지 못했어요."
            try {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                if (scheduled) scheduledLaunchFailure(launch)?.let { reason ->
                    log("예약 실행 실패 · $reason")
                    unfinishedReason = reason
                    return@launch
                }
                if (scheduledKey != null) {
                    getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                        .putString("lastScheduledStart", scheduledKey).apply()
                } else {
                    startActivity(launch)
                }
                opened = true
                log("실행 시작")
                for (task in Task.entries.filter { it in tasks }) {
                    runTask(task, TARGET_PACKAGE, onNoticeBlocked = { noticeBlocked = true }) {
                        results[task] = it
                    }
                    if (noticeBlocked) {
                        unfinishedReason = "공지를 닫지 못해 실행을 중단했어요."
                        break
                    }
                }
            } catch (e: CancellationException) {
                unfinishedReason = "실행이 중지됐어요."
                throw e
            } catch (e: Exception) {
                log("실행 실패 · ${e.javaClass.simpleName}")
                unfinishedReason = "대상 앱 실행 중 오류가 발생했어요."
            } finally {
                removeCurtain()
                notifyRunResult(tasks, results, unfinishedReason)
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

    private var curtain: View? = null
    private val windows get() = getSystemService(WindowManager::class.java)

    internal fun showCurtain() {
        if (curtain != null || !getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("privacyCurtain", false)) return
        val panel = View(this).apply {
            setBackgroundColor(Color.BLACK)
            contentDescription = "프라이버시 커튼. 실행 중지"
            setOnClickListener { stop() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        ).apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
            buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
            if (Build.VERSION.SDK_INT >= 30) {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
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
