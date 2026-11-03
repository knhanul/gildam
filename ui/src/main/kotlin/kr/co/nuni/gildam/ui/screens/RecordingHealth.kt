package kr.co.nuni.gildam.ui.screens

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/**
 * 기록이 끊기지 않게 확인하는 항목. 순서 = 중요한 순서 (상태 점 옆에는 첫 항목만 짧게).
 * [severe] 이면 상태 점이 빨강, 아니면 주황.
 */
enum class RecordingIssue(val short: String, val title: String, val message: String, val severe: Boolean) {
    LOCATION_OFF(
        "위치 꺼짐", "휴대폰 위치가 꺼져 있어요",
        "휴대폰의 위치(GPS)가 꺼져 있으면 길을 기록할 수 없어요. 위치를 켜 주세요.", severe = true
    ),
    LOCATION_DENIED(
        "위치 권한 없음", "위치 권한이 없어요",
        "길담이 위치를 볼 수 없어서 기록할 수 없어요. 위치 권한을 허용해 주세요.", severe = true
    ),
    BACKGROUND_LOCATION(
        "위치 '항상 허용' 아님", "위치 권한이 '항상 허용'이 아니에요",
        "화면을 끄거나 다른 앱을 쓰면 기록이 멈출 수 있어요. 위치 권한을 '항상 허용'으로 바꿔 주세요.", severe = false
    ),
    APPROXIMATE_LOCATION(
        "정확한 위치 꺼짐", "'정확한 위치'가 꺼져 있어요",
        "대략적인 위치만 쓰면 길이 삐뚤게 그려져요. 위치 권한에서 '정확한 위치 사용'을 켜 주세요.", severe = false
    ),
    GPS_WEAK(
        "GPS 약함", "GPS 신호가 약해요",
        "터널·실내라서 GPS 신호를 찾고 있어요. 마지막 확인 위치부터 이어집니다.", severe = false
    ),
    BATTERY_OPTIMIZATION(
        "배터리 최적화", "배터리 최적화가 켜져 있어요",
        "휴대폰이 배터리를 아끼려고 길담을 멈출 수 있어요. 길담을 '제한 없음(최적화 안 함)'으로 바꿔 주세요.", severe = false
    ),
    POWER_SAVER(
        "절전 모드", "절전 모드가 켜져 있어요",
        "절전 모드에서는 위치 기록이 띄엄띄엄 될 수 있어요. 기록하는 동안 절전 모드를 꺼 주세요.", severe = false
    ),
    NOTIFICATIONS_OFF(
        "알림 꺼짐", "길담 알림이 꺼져 있어요",
        "기록 중 알림이 안 보이면 기록 상태를 확인하거나 바로 멈추기 어려워요. 알림을 허용해 주세요.", severe = false
    );

    /** GPS 약함은 설정으로 고칠 수 없음 */
    val hasSettings: Boolean get() = this != GPS_WEAK
}

/** 상태 점 옆 짧은 글: 첫 항목 + '외 N' */
fun List<RecordingIssue>.shortSummary(): String? = when (size) {
    0 -> null
    1 -> first().short
    else -> "${first().short} 외 ${size - 1}"
}

/** 지금 휴대폰 상태를 점검 (GPS 약함은 기록 상태에서 따로 더함) */
fun checkRecordingHealth(context: Context): List<RecordingIssue> {
    val out = mutableListOf<RecordingIssue>()
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    val locationOn = lm?.let {
        if (Build.VERSION.SDK_INT >= 28) it.isLocationEnabled
        else it.isProviderEnabled(LocationManager.GPS_PROVIDER) || it.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } ?: true
    if (!locationOn) out += RecordingIssue.LOCATION_OFF

    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (!fine && !coarse) {
        out += RecordingIssue.LOCATION_DENIED
    } else {
        if (Build.VERSION.SDK_INT >= 29 && !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            out += RecordingIssue.BACKGROUND_LOCATION
        }
        if (!fine) out += RecordingIssue.APPROXIMATE_LOCATION
    }

    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) out += RecordingIssue.BATTERY_OPTIMIZATION
    if (pm?.isPowerSaveMode == true) out += RecordingIssue.POWER_SAVER

    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) out += RecordingIssue.NOTIFICATIONS_OFF
    return out
}

/** 항목별로 열 설정 화면 */
fun settingsIntentFor(issue: RecordingIssue, context: Context): Intent? {
    val pkg = context.packageName
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
    return when (issue) {
        RecordingIssue.LOCATION_OFF -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        RecordingIssue.LOCATION_DENIED, RecordingIssue.BACKGROUND_LOCATION, RecordingIssue.APPROXIMATE_LOCATION -> appDetails
        // 요청 대화상자(ACTION_REQUEST_IGNORE...)는 별도 권한·스토어 정책이 필요해 목록 화면을 엽니다.
        RecordingIssue.BATTERY_OPTIMIZATION -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        RecordingIssue.POWER_SAVER -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        RecordingIssue.NOTIFICATIONS_OFF ->
            if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            else appDetails
        RecordingIssue.GPS_WEAK -> null
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** 설정 화면을 열고, 못 열면 앱 정보 화면으로 */
fun openIssueSettings(issue: RecordingIssue, context: Context) {
    val intent = settingsIntentFor(issue, context) ?: return
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/**
 * 기록 점검 결과를 상태로. 화면에 돌아올 때(설정에서 돌아온 뒤 포함), 위치·절전 모드가 바뀔 때,
 * 그리고 [periodMs] 마다 다시 점검합니다. 미리보기에서는 빈 목록.
 */
@Composable
fun rememberRecordingHealth(periodMs: Long = 15_000L): State<List<RecordingIssue>> {
    val state = remember { mutableStateOf(emptyList<RecordingIssue>()) }
    if (LocalInspectionMode.current) return state
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val refresh = { state.value = runCatching { checkRecordingHealth(context) }.getOrDefault(emptyList()) }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) = refresh()
        }
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    LaunchedEffect(periodMs) {
        while (true) { refresh(); delay(periodMs) }
    }
    return state
}
