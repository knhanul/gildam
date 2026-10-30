package kr.co.nuni.gildam.ui.screens

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
internal enum class ScreenOffStatus(val labelKo: String, val warn: Boolean) {
    /** 기록에 필요한 위치 권한이 실제로 부족함 */
    LOCATION_NEEDED("위치 권한 필요", true),
    /** 기록에 영향을 줄 수 있는 배터리 제한이 확인됨 */
    CHECK_SETTINGS("설정 확인", true),
    /** 확인된 문제 없음 → 상세 화면으로 안내 */
    VIEW("설정 보기", false),
    /** 제조사 설정을 앱에서 조회할 수 없음 */
    GUIDE("기기 설정 안내", false)
}

private fun isForegroundLocationGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** null 이면 배터리 상태를 조회할 수 없음 */
private fun isBatteryOptimizationIgnored(context: Context): Boolean? =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName)

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

private fun manufacturerTip(): String {
    val m = (Build.MANUFACTURER ?: "").lowercase()
    return when {
        "samsung" in m -> "삼성: 설정 > 배터리 > 백그라운드 사용 제한에서 길담이 절전 대상으로 잡히지 않았는지 확인해 보세요."
        "xiaomi" in m || "redmi" in m || "poco" in m -> "샤오미: 설정 > 앱 > 앱 관리 > 길담 > 배터리 세이버에서 '제한 없음'을 고를 수 있어요."
        "huawei" in m || "honor" in m -> "화웨이/아너: 설정 > 배터리 > 앱 실행 관리에서 길담을 수동 관리로 바꾸고 백그라운드 동작을 허용할 수 있어요."
        "oppo" in m || "realme" in m || "oneplus" in m -> "오포/리얼미/원플러스: 설정 > 배터리 > 앱별 배터리 관리에서 길담의 백그라운드 제한을 확인해 보세요."
        "vivo" in m -> "비보: 설정 > 배터리 > 백그라운드 전력 사용에서 길담을 허용할 수 있어요."
        "lge" in m || "lg" == m -> "LG: 설정 > 배터리 > 배터리 사용량 최적화에서 길담을 제외할 수 있어요."
        else -> "이 기기 제조사의 절전 설정은 앱에서 확인할 수 없어요. 기기 설정의 배터리·절전 메뉴에서 길담을 찾아 확인해 주세요."
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
                title = { Text("화면 꺼짐 중 기록") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
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
                title = "위치 권한",
                ok = locationGranted,
                statusText = if (locationGranted) "앱 사용 중 허용됨" else "허용되지 않음",
                body = "기록을 시작할 때 위치 권한을 요청해요. 이 권한만 있으면 화면이 꺼져도 기록이 이어져요."
            ) {
                OutlinedButton(onClick = openAppDetails) { Text("위치 권한 설정", style = MaterialTheme.typography.labelMedium) }
            }

            // 2. 배터리 사용 — 앱이 확인 가능한 상태만 표시
            StatusCard(
                icon = Icons.Outlined.BatteryAlert,
                title = "배터리 사용",
                ok = batteryIgnored == true,
                statusText = when (batteryIgnored) {
                    true -> "길담을 절전 대상에서 제외됨"
                    false -> "배터리 최적화 적용 중"
                    null -> "이 기기에서 확인할 수 없음"
                },
                body = "화면을 끈 뒤 기록이 끊긴다면 길담의 배터리 사용 제한을 확인해 주세요. 제한을 해제하면 배터리 사용량이 늘 수 있어요."
            ) {
                if (batteryIntent != null) {
                    OutlinedButton(onClick = { context.startActivity(batteryIntent) }) {
                        Text("배터리 설정 열기", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = openAppDetails) { Text("앱 설정 열기", style = MaterialTheme.typography.labelMedium) }
                        Text(
                            "앱 정보 화면에서 '배터리' 항목을 열어 주세요.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. 기기별 절전 안내
            StatusCard(
                icon = Icons.Outlined.PhoneAndroid,
                title = "기기별 절전 안내",
                ok = null,
                statusText = "${Build.MANUFACTURER ?: "알 수 없는 제조사"} ${Build.MODEL ?: ""} · Android ${Build.VERSION.RELEASE}",
                body = manufacturerTip() + "\n제조사 설정 화면은 앱에서 직접 확인할 수 없어요."
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
