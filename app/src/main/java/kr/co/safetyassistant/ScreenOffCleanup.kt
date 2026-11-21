package kr.co.safetyassistant

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

private val closeLabels = setOf("close", "close app", "닫기", "앱 닫기")

internal fun isRecentTaskCloseLabel(label: CharSequence?) =
    label?.toString()?.trim()?.lowercase() in closeLabels

internal suspend fun DiagnosticService.finishScreenOffRun() {
    currentCoroutineContext().ensureActive()
    val target = DiagnosticService.TARGET_PACKAGE
    // Stop if the user has already switched away from this run.
    if (rootInActiveWindow?.packageName?.toString() != target) return
    val home = packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        PackageManager.MATCH_DEFAULT_ONLY
    )?.activityInfo?.packageName
    fun outsideCleanup(packageName: CharSequence?) =
        packageName != null && packageName != target && packageName != home && packageName != "com.android.systemui"
    val label = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
    MainActivity.removeOwnTask()
    if (label != null && performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)) {
        val dismissed = withTimeoutOrNull(1_800) {
            var handled = false
            while (!handled) {
                val root = rootInActiveWindow
                if (outsideCleanup(root?.packageName)) return@withTimeoutOrNull false
                val card = if (root != null && (root.packageName == home || root.packageName == "com.android.systemui")) {
                    root.matchingCards(label).singleOrNull()
                } else null
                handled = card != null && card.first.performAction(card.second)
                if (!handled) delay(100)
            }
            true
        } == true
        val cardGone = dismissed && withTimeoutOrNull(1_200) {
            delay(350)
            var root = rootInActiveWindow
            while (!outsideCleanup(root?.packageName) && (root == null || root.matchingCards(label).any())) {
                delay(100)
                root = rootInActiveWindow
            }
            !outsideCleanup(root?.packageName)
        } == true
        if (!cardGone) log("최근 앱에서 안전실천 챌린지 카드를 닫았는지 확인하지 못했어요.")
    } else {
        log("최근 앱 목록을 열지 못했어요.")
    }
    if (outsideCleanup(rootInActiveWindow?.packageName)) return
    if (!performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) {
        log("홈 화면으로 돌아가지 못했어요.")
        return
    }
    val homeVisible = withTimeoutOrNull(2_000) {
        delay(400)
        var foreground = rootInActiveWindow?.packageName
        while (!outsideCleanup(foreground) && (home == null || foreground != home)) {
            delay(100)
            foreground = rootInActiveWindow?.packageName
        }
        !outsideCleanup(foreground)
    } == true
    if (!homeVisible) {
        log("홈 화면 전환을 확인하지 못해 화면 끄기를 건너뛰었어요.")
        return
    }
    if (Build.VERSION.SDK_INT >= 28 && !performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) {
        log("화면을 끄지 못했어요.")
    }
}

private fun AccessibilityNodeInfo.matchingCards(label: String): Sequence<Pair<AccessibilityNodeInfo, Int>> =
    walkNodes(12).drop(1).take(300).filter { it.isVisibleToUser }.mapNotNull { node ->
        val action = node.actionList.firstOrNull {
            it.id == AccessibilityNodeInfo.ACTION_DISMISS || isRecentTaskCloseLabel(it.label)
        } ?: return@mapNotNull null
        val matches = node.walkNodes(3).any {
            it.isVisibleToUser && (it.text?.toString()?.trim() == label || it.contentDescription?.toString()?.trim() == label)
        }
        if (matches) node to action.id else null
    }.take(2)

private fun AccessibilityNodeInfo.walkNodes(depth: Int): Sequence<AccessibilityNodeInfo> = sequence {
    yield(this@walkNodes)
    if (depth > 0) {
        for (index in 0 until childCount) getChild(index)?.let { yieldAll(it.walkNodes(depth - 1)) }
    }
}
