package kr.co.safetyassistant

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

private val Blue = Color(0xFF005F82)
private val Green = Color(0xFF376E38)
private val Ink = Color(0xFF113345)
private val Canvas = Color(0xFFF1F8FA)
private val SafetyColors = lightColorScheme(
    primary = Blue,
    secondary = Green,
    secondaryContainer = Color(0xFFDFF3E4),
    onSecondaryContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE4F1F5),
    onSurfaceVariant = Color(0xFF435D68)
)
private enum class Page(val title: String) { HOME("홈"), SETTINGS("설정"), HISTORY("기록") }

class DashboardModel : ViewModel() {
    var structure by mutableStateOf("")
    var history by mutableStateOf("")
}

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var connected by mutableStateOf(false)
    private var targetInstalled by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        connected = DiagnosticService.instance != null
        targetInstalled = packageManager.getLaunchIntentForPackage(DiagnosticService.TARGET_PACKAGE) != null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = SafetyColors) {
                Dashboard(prefs, viewModel())
            }
        }
    }

    @Composable
    private fun Dashboard(prefs: SharedPreferences, model: DashboardModel) {
        val snackbar = remember { SnackbarHostState() }
        var message by remember { mutableStateOf("") }
        LaunchedEffect(message) {
            if (message.isNotEmpty()) {
                snackbar.showSnackbar(message)
                message = ""
            }
        }
        var page by rememberSaveable { mutableStateOf(Page.HOME) }
        var curtain by remember { mutableStateOf(prefs.getBoolean("privacyCurtain", false)) }
        var attendanceNotifications by remember { mutableStateOf(prefs.getBoolean("attendanceNotifications", false)) }
        var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
        var hour by remember { mutableIntStateOf(prefs.getInt("hour", 9)) }
        var minute by remember { mutableIntStateOf(prefs.getInt("minute", 0)) }
        var consent by remember { mutableStateOf(prefs.getBoolean("spendConsent", false)) }
        var limit by remember { mutableStateOf(prefs.getInt("spendLimit", 0).toString()) }
        var threshold by remember { mutableStateOf(prefs.getInt("threshold", 3000).toString()) }
        var retries by remember { mutableStateOf(prefs.getInt("retries", 2).toString()) }
        var scheduled by remember { mutableStateOf(enabledTasks(prefs)) }
        var apiKey by remember { mutableStateOf("") }

        DisposableEffect(prefs) {
            model.history = prefs.getString("history", "").orEmpty()
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { store, key ->
                if (key == "history") model.history = store.getString(key, "").orEmpty()
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }

        fun run(tasks: List<Task>) {
            when {
                tasks.isEmpty() -> message = "설정에서 실행할 작업을 선택하세요."
                !targetInstalled || !connected -> {
                    page = Page.SETTINGS
                    message = "대상 앱 설치와 접근성 권한을 확인하세요."
                }
                DiagnosticService.instance?.start(tasks) != true ->
                    message = "잠금 상태나 이미 실행 중인 작업을 확인하세요."
                else -> Unit
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = Canvas,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    Page.entries.forEach { item ->
                        NavigationBarItem(
                            selected = item == page,
                            onClick = { page = item; apiKey = "" },
                            icon = { Text(when (item) {
                                Page.HOME -> "⌂"
                                Page.SETTINGS -> "⚙"
                                Page.HISTORY -> "≡"
                            }, style = MaterialTheme.typography.titleLarge) },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        ) { padding ->
            key(page) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (page) {
                        Page.HOME -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().background(
                                    Brush.linearGradient(listOf(Color(0xFF005E84), Color(0xFF276C45))),
                                    RoundedCornerShape(26.dp)
                                ).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Image(painterResource(R.drawable.ic_safety), null, Modifier.size(48.dp))
                                    Text("안전실천 출첵", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                }
                                Text(if (enabled) "매일 %02d:%02d 예약 · %d개 작업".format(hour, minute, scheduled.size)
                                    else "자동 실행 예약 꺼짐", color = Color.White)
                                Button(
                                    onClick = { run(scheduled) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Blue)
                                ) { Text("수동 실행") }
                            }
                            Panel("최근 실행") {
                                Text(model.history.lineSequence().firstOrNull { it.isNotBlank() } ?: "아직 실행 기록이 없어요.")
                                TextButton(onClick = { page = Page.HISTORY }) { Text("실행 기록 보기") }
                            }
                            Panel("개별 실행") {
                                Task.entries.forEach { task ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(taskLabel(task), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                        OutlinedButton(onClick = { run(listOf(task)) }) { Text("실행") }
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    DiagnosticService.instance?.stop()
                                    enabled = false
                                    prefs.edit().putBoolean("enabled", false).apply()
                                    schedule(this@MainActivity)
                                    message = "실행과 예약을 중지했어요."
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("긴급 중지") }
                        }
                        Page.SETTINGS -> {
                            PageHeading("설정")
                            Panel("연결 및 권한") {
                                Text(if (connected) "접근성 연결됨" else "접근성 연결 필요", color = if (connected) Green else Blue)
                                OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                                    Text("접근성 설정 열기")
                                }
                                TextButton(onClick = {
                                    model.structure = "${DiagnosticService.lastPackage}\n${DiagnosticService.structure}"
                                }) { Text("최근 화면 진단 보기") }
                                if (model.structure.isNotBlank()) Text(model.structure, style = MaterialTheme.typography.bodySmall)
                            }
                            Panel("자동 실행 예약") {
                                ToggleRow("매일 예약 실행", enabled) {
                                    enabled = it
                                    prefs.edit().putBoolean("enabled", it).apply()
                                    if (it && Build.VERSION.SDK_INT >= 33) {
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    schedule(this@MainActivity)
                                }
                                OutlinedButton(onClick = {
                                    TimePickerDialog(this@MainActivity, { _, h, m ->
                                        hour = h
                                        minute = m
                                        prefs.edit().putInt("hour", h).putInt("minute", m).apply()
                                        schedule(this@MainActivity)
                                    }, hour, minute, true).show()
                                }) { Text("실행 시간 %02d:%02d".format(hour, minute)) }
                                Text("휴대폰이 잠겼거나 Android에서 백그라운드 실행을 제한하면 지연될 수 있어요.",
                                    style = MaterialTheme.typography.bodySmall)
                                HorizontalDivider()
                                Task.entries.forEach { task ->
                                    ToggleRow(taskLabel(task), task in scheduled) {
                                        prefs.edit().putBoolean("auto.$task", it).apply()
                                        scheduled = enabledTasks(prefs)
                                    }
                                }
                                NumberField("화면 탐색 재시도 (0–5)", retries) {
                                    retries = it
                                    prefs.edit().putInt("retries", it.toIntOrNull()?.coerceIn(0, 5) ?: 0).apply()
                                }
                            }
                            Panel("프라이버시 및 알림") {
                                ToggleRow("프라이버시 커튼", curtain) {
                                    curtain = it
                                    prefs.edit().putBoolean("privacyCurtain", it).apply()
                                    if (!it) DiagnosticService.instance?.removeCurtain()
                                }
                                Text("자동화 중 대상 앱을 가립니다. 커튼의 실행 중지 버튼으로 멈출 수 있어요.",
                                    style = MaterialTheme.typography.bodySmall)
                                HorizontalDivider()
                                ToggleRow("출석 결과 알림", attendanceNotifications) {
                                    attendanceNotifications = it
                                    prefs.edit().putBoolean("attendanceNotifications", it).apply()
                                    if (it && Build.VERSION.SDK_INT >= 33) {
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                Text("수동·예약 출석의 성공과 실패를 알림으로 확인할 수 있어요.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Panel("AI 퀴즈") {
                                Text("새 문항과 관련 참고 자료를 Gemini에 전송합니다.", style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    apiKey, { apiKey = it.trim() }, label = { Text("Gemini API 키") },
                                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(enabled = apiKey.matches(Regex("[A-Za-z0-9_-]{20,200}")), onClick = {
                                        message = runCatching { QuizKey.save(this@MainActivity, apiKey) }
                                            .fold({ "키를 저장했어요." }, { "키를 저장하지 못했어요." })
                                        apiKey = ""
                                    }) { Text("키 저장") }
                                    TextButton(onClick = {
                                        message = runCatching { QuizKey.clear(this@MainActivity) }
                                            .fold({ "키를 삭제했어요." }, { "키를 삭제하지 못했어요." })
                                        apiKey = ""
                                    }) { Text("키 삭제") }
                                }
                            }
                            Panel("룰렛 포인트") {
                                ToggleRow("룰렛 3,000포인트 사용 동의", consent) {
                                    consent = it
                                    prefs.edit().putBoolean("spendConsent", it).apply()
                                }
                                NumberField("일일 사용 한도", limit) {
                                    limit = it
                                    prefs.edit().putInt("spendLimit", it.toIntOrNull() ?: 0).apply()
                                }
                                NumberField("룰렛 기준 포인트", threshold) {
                                    threshold = it
                                    prefs.edit().putInt("threshold", it.toIntOrNull() ?: Int.MAX_VALUE).apply()
                                }
                            }
                        }
                        Page.HISTORY -> {
                            PageHeading("실행 기록")
                            val entries = model.history.lines().filter(String::isNotBlank)
                            if (entries.isEmpty()) Panel("최근 실행") { Text("아직 실행 기록이 없어요.") }
                            entries.forEach { entry ->
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        if (" · " in entry) {
                                            Text(entry.substringBefore(" · ").replace('T', ' '),
                                                color = Blue, style = MaterialTheme.typography.labelMedium)
                                        }
                                        Text(entry.substringAfter(" · "), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun taskLabel(task: Task) = when (task) {
    Task.ATTENDANCE -> "출석체크"
    Task.QUIZ -> "오늘의 O/X 퀴즈"
    Task.POINTS -> "보유 포인트 확인"
    Task.ROULETTE -> "룰렛 참여"
}

@Composable
private fun PageHeading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Blue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = change)
    }
}

@Composable
private fun NumberField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(
        value, { if (it.all { digit -> digit in '0'..'9' } && it.length <= 10) change(it) },
        modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
