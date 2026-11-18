package kr.co.safetyassistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context
import android.content.Intent
import android.app.KeyguardManager
import android.os.PowerManager
import kotlinx.coroutines.*
import java.time.LocalDateTime

class DiagnosticService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var run: Job? = null
    override fun onServiceConnected() {
        targetPackage = getSharedPreferences("settings", Context.MODE_PRIVATE).getString("target", "").orEmpty()
        instance = this
    }

    fun log(message: String) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val row = "${LocalDateTime.now().withNano(0)} · $message"
        val previous = prefs.getString("history", "").orEmpty()
        prefs.edit().putString("history", (listOf(row) + previous.lines()).take(30).joinToString("\n")).apply()
    }

    fun start(tasks: List<Task>): Boolean {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(PowerManager::class.java).isInteractive) return false
        val pkg = targetPackage
        if (tasks.isEmpty() || run?.isActive == true || !prefs.getBoolean("policyConfirmed", false) || pkg.isBlank()) return false
        val launch = if (android.os.Build.VERSION.SDK_INT >= 33) {
            packageManager.getLaunchIntentSenderForPackage(pkg)
        } else null
        run = scope.launch {
            try {
                if (launch != null) launch.sendIntent(this@DiagnosticService, 0, null, null, null)
                else {
                    val intent = packageManager.getLaunchIntentForPackage(pkg)
                    if (intent == null) error("대상 앱 실행 불가")
                    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                log("실행 시작")
                for (task in tasks) runTask(task, pkg, prefs.getInt("retries", 2).coerceIn(0, 5))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("실행 실패 · ${e.javaClass.simpleName}")
            }
        }
        return true
    }

    fun stop() { run?.cancel() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != targetPackage || pkg == packageName) return
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
    override fun onInterrupt() { }
    override fun onDestroy() { scope.cancel(); instance = null; super.onDestroy() }
    companion object {
        @Volatile var instance: DiagnosticService? = null
        @Volatile var targetPackage: String = ""
        @Volatile var lastPackage: String = "아직 감지된 앱 없음"
        @Volatile var structure: String = "화면을 확인하지 않았습니다."
    }
}
