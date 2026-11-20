package kr.co.safetyassistant

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal fun noticeImageChecked(pixels: IntArray): Boolean? {
    if (pixels.isEmpty()) return null
    var blue = 0
    var gray = 0
    for (pixel in pixels) {
        val r = pixel shr 16 and 255
        val g = pixel shr 8 and 255
        val b = pixel and 255
        if (r < 100 && g > 110 && b > 150 && b - r > 85) blue++
        if (r in 140..225 && kotlin.math.abs(r - g) < 12 && kotlin.math.abs(r - b) < 12) gray++
    }
    return when {
        blue > pixels.size / 3 && gray < pixels.size / 8 -> true
        gray > pixels.size / 3 && blue < pixels.size / 8 -> false
        else -> null
    }
}

internal fun attendanceMarkChecked(pixels: IntArray): Boolean {
    if (pixels.isEmpty()) return false
    val lime = pixels.count { pixel ->
        val r = pixel shr 16 and 255
        val g = pixel shr 8 and 255
        val b = pixel and 255
        r in 95..180 && g in 185..250 && b in 25..125 && g - r >= 40
    }
    return lime > pixels.size / 3
}

internal suspend fun AccessibilityService.readScreenMark(
    bounds: Rect,
    pkg: String,
    windowId: Int,
    classify: (IntArray) -> Boolean?
): Boolean? {
    if (Build.VERSION.SDK_INT < 30 || bounds.isEmpty) return null
    return withTimeoutOrNull(1500) {
        suspendCancellableCoroutine { continuation ->
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val state = result.hardwareBuffer.use { buffer ->
                        if (!continuation.isActive) return
                        val root = rootInActiveWindow
                        if (root?.packageName?.toString() != pkg || root.windowId != windowId) {
                            continuation.resume(null)
                            return
                        }
                        runCatching {
                            val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                ?: return@runCatching null
                            try {
                                if (!Rect(0, 0, hardware.width, hardware.height).contains(bounds)) {
                                    return@runCatching null
                                }
                                val bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false)
                                    ?: return@runCatching null
                                try {
                                    val pixels = IntArray(bounds.width() * bounds.height())
                                    bitmap.getPixels(pixels, 0, bounds.width(), bounds.left, bounds.top,
                                        bounds.width(), bounds.height())
                                    classify(pixels)
                                } finally {
                                    bitmap.recycle()
                                }
                            } finally {
                                hardware.recycle()
                            }
                        }.getOrNull()
                    }
                    continuation.resume(state)
                }

                override fun onFailure(errorCode: Int) {
                    continuation.resume(null)
                }
            })
        }
    }
}
