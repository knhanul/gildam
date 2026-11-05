package kr.co.nuni.gildam.ui.screens

import kr.co.nuni.gildam.ui.l10n.*
import androidx.compose.ui.res.stringResource
import kr.co.nuni.gildam.ui.R
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kr.co.nuni.gildam.ui.theme.WarningAmber

/** 설정 첫 화면 '화면 꺼짐 중 기록' 항목 오른쪽에 표시하는 상태. */
internal enum class ScreenOffStatus(val labelKoRes: Int, val warn: Boolean) {
    /** 기록에 필요한 위치 권한이 실제로 부족함 */
    LOCATION_NEEDED(R.string.screen_off_location_needed, true),
    /** 기록에 영향을 줄 수 있는 배터리 제한이 확인됨 */
    CHECK_SETTINGS(R.string.screen_off_check_settings, true),
    /** 확인된 문제 없음 → 상세 화면으로 안내 */
    VIEW(R.string.screen_off_view, false),
    /** 제조사 설정을 앱에서 조회할 수 없음 */
    GUIDE(R.string.screen_off_guide, false);

    val label: String @Composable get() = stringResource(labelKoRes)
}

private fun isForegroundLocationGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** null 이면 배터리 상태를 조회할 수 없음 */
private fun isBatteryOptimizationIgnored(context: Context): Boolean? = try {
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName)
} catch (t: Throwable) {
    null // 일부 기기·미리보기 환경에서는 조회 불가
}

private fun screenOffStatus(context: Context): ScreenOffStatus {
    if (!isForegroundLocationGranted(context)) return ScreenOffStatus.LOCATION_NEEDED
    if (Build.MANUFACTURER.isNullOrBlank() || Build.MANUFACTURER.equals("unknown", ignoreCase = true)) {
        return ScreenOffStatus.GUIDE
    }
    return when (isBatteryOptimizationIgnored(context)) {
        null -> ScreenOffStatus.GUIDE
        false -> ScreenOffStatus.CHECK_SETTINGS
        true -> ScreenOffStatus.VIEW
    }
}

/**
 * 앱이 포그라운드로 돌아올 때마다 상태를 다시 조회하는 헬퍼.
 * 시스템 설정을 열기만 하고 완료로 처리하지 않습니다.
 */
@Composable
internal fun <T> rememberDeviceState(probe: (Context) -> T): T {
    val context = LocalContext.current
    var value by remember { mutableStateOf(probe(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) value = probe(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return value
}

/** 설정 첫 화면 목록에 쓰는 상태 (ON_RESUME 마다 재조회) */
@Composable
internal fun rememberScreenOffStatus(): ScreenOffStatus =
    rememberDeviceState(::screenOffStatus)

@androidx.annotation.StringRes
private fun manufacturerTip(): Int {
    val m = (Build.MANUFACTURER ?: "").lowercase()
    return when {
        "samsung" in m -> R.string.oem_samsung
        "xiaomi" in m || "redmi" in m || "poco" in m -> R.string.oem_xiaomi
        "huawei" in m || "honor" in m -> R.string.oem_huawei
        "oppo" in m || "realme" in m || "oneplus" in m -> R.string.oem_oppo
        "vivo" in m -> R.string.oem_vivo
        "lge" in m || "lg" == m -> R.string.oem_lg
        else -> R.string.oem_other
    }
}

/** 설정 > 기록 설정 > 화면 꺼짐 중 기록 상세 화면 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingStatusScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val locationGranted = rememberDeviceState(::isForegroundLocationGranted)
    val batteryIgnored = rememberDeviceState(::isBatteryOptimizationIgnored)

    val openAppDetails = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        )
    }
    val batteryIntent = remember {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .takeIf { it.resolveActivity(context.packageManager) != null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_screen_off_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(2.dp))

            // 1. 위치 권한 — 실제 허용 상태만 표시
            StatusCard(
                icon = Icons.Outlined.LocationOn,
                title = stringResource(R.string.screen_off_location_title),
                ok = locationGranted,
                statusText = if (locationGranted) stringResource(R.string.screen_off_location_granted) else stringResource(R.string.screen_off_location_denied),
                body = stringResource(R.string.screen_off_location_body)
            ) {
                OutlinedButton(onClick = openAppDetails) { Text(stringResource(R.string.screen_off_location_settings), style = MaterialTheme.typography.labelMedium) }
            }

            // 2. 배터리 사용 — 앱이 확인 가능한 상태만 표시
            StatusCard(
                icon = Icons.Outlined.BatteryAlert,
                title = stringResource(R.string.screen_off_battery_title),
                ok = batteryIgnored == true,
                statusText = when (batteryIgnored) {
                    true -> stringResource(R.string.screen_off_battery_exempt)
                    false -> stringResource(R.string.screen_off_battery_optimized)
                    null -> stringResource(R.string.screen_off_battery_unknown)
                },
                body = stringResource(R.string.screen_off_battery_body)
            ) {
                if (batteryIntent != null) {
                    OutlinedButton(onClick = { context.startActivity(batteryIntent) }) {
                        Text(stringResource(R.string.screen_off_battery_open), style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = openAppDetails) { Text(stringResource(R.string.screen_off_app_open), style = MaterialTheme.typography.labelMedium) }
                        Text(
                            stringResource(R.string.screen_off_app_battery_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. 기기별 절전 안내
            StatusCard(
                icon = Icons.Outlined.PhoneAndroid,
                title = stringResource(R.string.screen_off_oem_title),
                ok = null,
                statusText = "${Build.MANUFACTURER ?: stringResource(R.string.unknown_manufacturer)} ${Build.MODEL ?: ""} · Android ${Build.VERSION.RELEASE}",
                body = stringResource(manufacturerTip()) + stringResource(R.string.screen_off_oem_footer)
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun StatusCard(
    icon: ImageVector,
    title: String,
    /** true=확인된 문제 없음, false=조치 필요, null=상태 단정 불가 */
    ok: Boolean?,
    statusText: String,
    body: String,
    action: (@Composable () -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsItemIcon(icon)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusIcon: ImageVector = when (ok) {
                            true -> Icons.Outlined.CheckCircle
                            false -> Icons.Outlined.ErrorOutline
                            null -> Icons.Outlined.Info
                        }
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = when (ok) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> WarningAmber
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (ok) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> WarningAmber
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (action != null) {
                Spacer(Modifier.height(10.dp))
                action()
            }
        }
    }
}
