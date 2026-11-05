package kr.co.nuni.gildam.ui.screens

import androidx.compose.ui.res.stringResource
import kr.co.nuni.gildam.ui.R
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
enum class RecordingIssue(val shortRes: Int, val titleRes: Int, val messageRes: Int, val severe: Boolean) {
    LOCATION_OFF(
        R.string.health_location_off_short, R.string.health_location_off_title,
        R.string.health_location_off_desc, severe = true
    ),
    LOCATION_DENIED(
        R.string.health_no_permission_short, R.string.health_no_permission_title,
        R.string.health_no_permission_desc, severe = true
    ),
    BACKGROUND_LOCATION(
        R.string.health_not_always_short, R.string.health_not_always_title,
        R.string.health_not_always_desc, severe = false
    ),
    APPROXIMATE_LOCATION(
        R.string.health_precise_off_short, R.string.health_precise_off_title,
        R.string.health_precise_off_desc, severe = false
    ),
    GPS_WEAK(
        R.string.health_gps_weak_short, R.string.health_gps_weak_title,
        R.string.health_gps_weak_desc, severe = false
    ),
    BATTERY_OPTIMIZATION(
        R.string.health_battery_opt_short, R.string.health_battery_opt_title,
        R.string.health_battery_opt_desc, severe = false
    ),
    POWER_SAVER(
        R.string.health_power_save_short, R.string.health_power_save_title,
        R.string.health_power_save_desc, severe = false
    ),
    NOTIFICATIONS_OFF(
        R.string.health_notif_off_short, R.string.health_notif_off_title,
        R.string.health_notif_off_desc, severe = false
    );


    val short: String @Composable get() = stringResource(shortRes)
    val title: String @Composable get() = stringResource(titleRes)
    val message: String @Composable get() = stringResource(messageRes)
    /** GPS 약함은 설정으로 고칠 수 없음 */
    val hasSettings: Boolean get() = this != GPS_WEAK
}

/** 상태 점 옆 짧은 글: 첫 항목 + '외 N' */
@Composable
fun List<RecordingIssue>.shortSummary(): String? = when (size) {
    0 -> null
    1 -> first().short
    else -> stringResource(R.string.health_more, first().short, size - 1)
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
