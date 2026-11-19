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
        if (DiagnosticService.instance?.isWakePending(requestId) != true) {
            finishAndRemoveTask()
            return
        }
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        setContentView(TextView(this).apply {
            text = "안전실천 출첵\n잠금을 해제하면 예약 출석을 이어서 진행해요."
            gravity = Gravity.CENTER
            textSize = 18f
        })
    }

    override fun onResume() {
        super.onResume()
        val service = DiagnosticService.instance ?: return finishAndRemoveTask()
        if (!service.isWakePending(requestId)) return finishAndRemoveTask()
        service.wakeActivity = this
        if (requested) return
        requested = true
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            service.completeWake(requestId, true)
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { service.completeWake(requestId, true) }
            override fun onDismissCancelled() { service.completeWake(requestId, false) }
            override fun onDismissError() { service.completeWake(requestId, false) }
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
