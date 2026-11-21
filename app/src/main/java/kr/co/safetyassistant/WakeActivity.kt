package kr.co.safetyassistant

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

internal class WakeActivity : Activity() {
    private var requested = false
    private val requestId get() = intent.getLongExtra("wakeRequest", 0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = DiagnosticService.instance
        if (service == null || !service.isWakePending(requestId)) {
            finishAndRemoveTask()
            return
        }
        service.wakeActivity = this
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        setContentView(TextView(this).apply {
            val keyguard = getSystemService(KeyguardManager::class.java)
            text = if (keyguard.isKeyguardLocked && keyguard.isKeyguardSecure) {
                "안전실천 출첵\n잠금을 해제하면 예약 출석을 이어서 진행해요."
            } else {
                "안전실천 출첵\n예약 출석을 시작하고 있어요."
            }
            gravity = Gravity.CENTER
            textSize = 18f
        })
    }

    override fun onPostResume() {
        super.onPostResume()
        window.decorView.post { if (!isFinishing && !isDestroyed) prepareLaunch() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) prepareLaunch()
    }

    private fun prepareLaunch() {
        val service = DiagnosticService.instance ?: return finishAndRemoveTask()
        if (requested) return
        if (!service.isWakePending(requestId)) return finishAndRemoveTask()
        requested = true
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            service.completeWake(requestId, true)
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { service.completeWake(requestId, true) }
            override fun onDismissCancelled() { service.completeWake(requestId, false) }
            override fun onDismissError() {
                if (hasWindowFocus()) service.completeWake(requestId, false)
                else requested = false // Retry when the window takes focus.
            }
        })
    }

    override fun onDestroy() {
        DiagnosticService.instance?.let { service ->
            if (service.wakeActivity === this) {
                service.wakeActivity = null
                service.completeWake(requestId, false)
            }
        }
        super.onDestroy()
    }
}
