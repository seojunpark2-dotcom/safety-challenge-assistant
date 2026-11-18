package kr.co.safetyassistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class DiagnosticService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
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
    override fun onDestroy() { instance = null; super.onDestroy() }
    companion object {
        @Volatile var instance: DiagnosticService? = null
        @Volatile var targetPackage: String = ""
        @Volatile var lastPackage: String = "아직 감지된 앱 없음"
        @Volatile var structure: String = "화면을 확인하지 않았습니다."
    }
}

