package kr.co.safetyassistant

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class DashboardModel : ViewModel() {
    var enabled by mutableStateOf(false)
    var hour by mutableIntStateOf(9)
    var minute by mutableIntStateOf(0)
    var pkg by mutableStateOf("")
    var target by mutableStateOf("")
    var structure by mutableStateOf("")
}
class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = getSharedPreferences("settings", MODE_PRIVATE)
        setContent {
            val model: DashboardModel = viewModel()
            LaunchedEffect(Unit) { model.target = p.getString("target", "").orEmpty(); DiagnosticService.targetPackage = model.target; model.enabled = p.getBoolean("enabled", false); model.hour = p.getInt("hour", 9); model.minute = p.getInt("minute", 0) }
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("안전실천 도우미", style = MaterialTheme.typography.headlineMedium)
                    Text("진단 단계: 출석·퀴즈·포인트·룰렛 자동 실행은 대상 앱과 정책 확인 전까지 중지됩니다.")
                    Text("접근성 서비스: ${if (DiagnosticService.instance != null) "연결됨" else "연결 필요"}")
                    Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("접근성 설정 열기") }
                    OutlinedTextField(value = model.target, onValueChange = { model.target = it.trim() }, label = { Text("대상 앱 패키지명") }, singleLine = true)
                    Button(enabled = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(model.target), onClick = {
                        DiagnosticService.targetPackage = model.target
                        DiagnosticService.structure = "화면을 확인하지 않았습니다."
                        DiagnosticService.lastPackage = model.target
                        model.pkg = ""
                        model.structure = ""
                        p.edit().putString("target", model.target).apply()
                    }) { Text("진단 대상 저장") }
                    Text("대상 앱을 열고 돌아온 뒤 결과를 불러오세요. 화면 문구는 진단에만 사용합니다.")
                    Button(onClick = { model.pkg = DiagnosticService.lastPackage; model.structure = DiagnosticService.structure }) { Text("최근 화면 진단 결과 보기") }
                    Text("패키지: ${model.pkg}")
                    Text(model.structure, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Row { Text("매일 확인 알림"); Switch(checked = model.enabled, onCheckedChange = { model.enabled = it; p.edit().putBoolean("enabled", it).apply(); if (it && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS); schedule(this@MainActivity) }) }
                    Button(onClick = { TimePickerDialog(this@MainActivity, { _, h, m -> model.hour = h; model.minute = m; p.edit().putInt("hour", h).putInt("minute", m).apply(); schedule(this@MainActivity) }, model.hour, model.minute, true).show() }) { Text("알림 시간: %02d:%02d".format(model.hour, model.minute)) }
                    Text("알림은 WorkManager의 시스템 제약에 따라 지연될 수 있습니다. 잠금 해제 및 접근성 권한이 필요합니다.")
                    OutlinedButton(onClick = { model.enabled = false; p.edit().putBoolean("enabled", false).apply(); schedule(this@MainActivity) }) { Text("긴급 중지 / 알림 해제") }
                }
            }
        }
    }
}

