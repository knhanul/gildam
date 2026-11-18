package kr.co.nuni.gildam.ui.l10n

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import kr.co.nuni.gildam.data.gpxcompare.GradeBucket
import kr.co.nuni.gildam.data.gpxcompare.RuggednessGrade
import kr.co.nuni.gildam.data.list.DefaultRecordTitle
import kr.co.nuni.gildam.data.list.RecordGroup
import kr.co.nuni.gildam.data.list.RecordGroupMode
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.model.GaugeMetric
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.model.Visibility
import kr.co.nuni.gildam.data.weather.PrecipType
import kr.co.nuni.gildam.data.weather.SkyCondition
import kr.co.nuni.gildam.data.weather.WindCardinal
import kr.co.nuni.gildam.data.weather.WindStrength
import kr.co.nuni.gildam.map.MapCameraMode
import kr.co.nuni.gildam.ui.R
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * 데이터 모듈의 enum·값을 화면 글자로 바꾸는 곳 (한국어/영어 리소스).
 * 데이터 모듈의 labelKo 같은 한국어 필드는 테스트·내부용으로만 남기고, 화면에서는 여기 함수를 씁니다.
 */

@StringRes fun TransportType.labelRes(): Int = when (this) {
    TransportType.WALK -> R.string.transport_walk
    TransportType.RUN -> R.string.transport_run
    TransportType.BIKE -> R.string.transport_bike
    TransportType.HIKE -> R.string.transport_hike
    TransportType.CAR -> R.string.transport_car
}
val TransportType.label: String @Composable get() = stringResource(labelRes())

val SyncState.label: String @Composable get() = stringResource(
    when (this) {
        SyncState.LOCAL_ONLY -> R.string.sync_state_local_only
        SyncState.PENDING_UPLOAD -> R.string.sync_state_pending_upload
        SyncState.SYNCING -> R.string.sync_state_syncing
        SyncState.SYNCED -> R.string.sync_state_synced
        SyncState.PENDING_DELETE -> R.string.sync_state_pending_delete
        SyncState.SYNC_ERROR -> R.string.sync_state_error
    }
)

val Visibility.label: String @Composable get() = stringResource(
    when (this) {
        Visibility.PRIVATE -> R.string.visibility_private
        Visibility.PUBLIC_LINK -> R.string.visibility_link
        Visibility.PUBLIC -> R.string.visibility_public
    }
)

val GaugeMetric.label: String @Composable get() = stringResource(
    when (this) {
        GaugeMetric.DISTANCE -> R.string.gauge_distance
        GaugeMetric.TOTAL_TIME -> R.string.gauge_total_time
        GaugeMetric.CURRENT_SPEED -> R.string.gauge_current_speed
        GaugeMetric.MOVING_AVG_SPEED -> R.string.gauge_moving_avg_speed
        GaugeMetric.CLOCK -> R.string.gauge_clock
        GaugeMetric.NONE -> R.string.gauge_none
    }
)

val GaugeMetric.desc: String @Composable get() = stringResource(
    when (this) {
        GaugeMetric.DISTANCE -> R.string.gauge_distance_desc
        GaugeMetric.TOTAL_TIME -> R.string.gauge_total_time_desc
        GaugeMetric.CURRENT_SPEED -> R.string.gauge_current_speed_desc
        GaugeMetric.MOVING_AVG_SPEED -> R.string.gauge_moving_avg_speed_desc
        GaugeMetric.CLOCK -> R.string.gauge_clock_desc
        GaugeMetric.NONE -> R.string.gauge_none_desc
    }
)

/** 계기판 라벨: 단위가 있으면 "누적 거리 (km)" 처럼 */
val GaugeMetric.labelWithUnitText: String @Composable get() =
    labelUnit?.let { stringResource(R.string.label_with_unit, label, it) } ?: label

val RuggednessGrade.label: String @Composable get() = stringResource(
    when (this) {
        RuggednessGrade.EASY -> R.string.grade_easy
        RuggednessGrade.MODERATE -> R.string.grade_moderate
        RuggednessGrade.SOMEWHAT_HARD -> R.string.grade_somewhat_hard
        RuggednessGrade.HARD -> R.string.grade_hard
        RuggednessGrade.VERY_HARD -> R.string.grade_very_hard
    }
)

val RuggednessGrade.description: String @Composable get() = stringResource(
    when (this) {
        RuggednessGrade.EASY -> R.string.grade_easy_desc
        RuggednessGrade.MODERATE -> R.string.grade_moderate_desc
        RuggednessGrade.SOMEWHAT_HARD -> R.string.grade_somewhat_hard_desc
        RuggednessGrade.HARD -> R.string.grade_hard_desc
        RuggednessGrade.VERY_HARD -> R.string.grade_very_hard_desc
    }
)

/** 경사 구간 이름: "15% 이상" / "5–10%" */
fun GradeBucket.label(context: Context): String =
    toPct?.let { "${fromPct.toInt()}–${it.toInt()}%" }
        ?: context.getString(R.string.grade_bucket_over, fromPct.toInt())

val WindCardinal.label: String @Composable get() = stringResource(
    when (this) {
        WindCardinal.N -> R.string.weather_dir_n
        WindCardinal.NE -> R.string.weather_dir_ne
        WindCardinal.E -> R.string.weather_dir_e
        WindCardinal.SE -> R.string.weather_dir_se
        WindCardinal.S -> R.string.weather_dir_s
        WindCardinal.SW -> R.string.weather_dir_sw
        WindCardinal.W -> R.string.weather_dir_w
        WindCardinal.NW -> R.string.weather_dir_nw
    }
)

val WindStrength.label: String @Composable get() = stringResource(
    when (this) {
        WindStrength.LIGHT -> R.string.weather_strength_light
        WindStrength.MODERATE -> R.string.weather_strength_moderate
        WindStrength.STRONG -> R.string.weather_strength_strong
        WindStrength.VERY_STRONG -> R.string.weather_strength_very_strong
    }
)

val PrecipType.label: String @Composable get() = stringResource(
    when (this) {
        PrecipType.NONE -> R.string.weather_precip_none
        PrecipType.RAIN -> R.string.weather_precip_rain
        PrecipType.SNOW -> R.string.weather_precip_snow
        PrecipType.SLEET -> R.string.weather_precip_sleet
    }
)

val SkyCondition.label: String @Composable get() = stringResource(
    when (this) {
        SkyCondition.CLEAR -> R.string.weather_sky_clear
        SkyCondition.CLOUDY -> R.string.weather_sky_cloudy
        SkyCondition.OVERCAST -> R.string.weather_sky_overcast
    }
)

val MapCameraMode.label: String @Composable get() = stringResource(

    when (this) {
        MapCameraMode.FOLLOW -> R.string.camera_follow_north
        MapCameraMode.HEADING -> R.string.camera_follow_heading
        MapCameraMode.OVERVIEW -> R.string.map_mode_overview
    }
)

val RecordGroupMode.label: String @Composable get() = stringResource(
    when (this) {
        RecordGroupMode.RECENT -> R.string.group_recent
        RecordGroupMode.MONTH -> R.string.group_month
        RecordGroupMode.REGION -> R.string.group_region
    }
)

/** 묶음 제목: 최근 날짜(오늘/어제/11월 17일 (월)) · 월(기기 언어 형식) · 지역 이름 · 지역 확인 중/없음 */
@Composable
fun RecordGroup.displayTitle(today: LocalDate = LocalDate.now()): String = when {
    key == "region:none" -> stringResource(R.string.group_region_none)
    key == "region:pending" -> stringResource(R.string.group_region_pending)
    key.startsWith("date:") -> {
        val date = LocalDate.parse(key.removePrefix("date:"))
        when (date) {
            today -> stringResource(R.string.group_date_today)
            today.minusDays(1) -> stringResource(R.string.group_date_yesterday)
            else -> {
                val locale = currentLocale()
                val pattern = if (date.year == today.year) {
                    stringResource(R.string.group_date_same_year_pattern)
                } else {
                    stringResource(R.string.group_date_other_year_pattern)
                }
                date.format(DateTimeFormatter.ofPattern(pattern, locale))
            }
        }
    }
    Regex("\\d{4}-\\d{2}").matches(key) -> {
        val locale = currentLocale()
        val ym = YearMonth.of(key.substring(0, 4).toInt(), key.substring(5, 7).toInt())
        ym.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMM"), locale))
    }
    else -> title
}

/** 사용자가 직접 바꾼 제목만 돌려주고, 자동 생성된 기본 제목(`default_record_title`)이면 null */
fun customRecordTitleOrNull(
    context: Context,
    activity: ActivityEntity,
    zoneId: ZoneId = ZoneId.systemDefault()
): String? {
    val locale = context.currentLocale()
    val base = Instant.ofEpochMilli(activity.startedAtEpochMs).atZone(zoneId).toLocalDateTime()
    val fmt = runCatching { shortDateTimeFormatter(locale) }.getOrNull()
    val transportLabel = context.getString(activity.transportType.labelRes())
    val extra = if (fmt != null) {
        (-2L..2L).map { offset ->
            context.getString(R.string.default_record_title, base.plusMinutes(offset).format(fmt), transportLabel)
        }
    } else {
        emptyList()
    }
    return DefaultRecordTitle.customTitleOrNull(activity, zoneId, extra)
}

@Composable
fun currentLocale(): Locale =
    ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()

fun Context.currentLocale(): Locale =
    ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()

/** "11월 5일 14:30" / "Nov 5, 14:30" — 기기 언어에 맞는 짧은 날짜+시각 */
fun shortDateTimeFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMdHHmm"), locale)

/** "오후 3:24" / "3:24 PM" */
fun shortTimeFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "hmm"), locale)

fun formatShortTime(context: Context, ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(ms).atZone(zone).format(shortTimeFormatter(context.currentLocale()))

fun formatHoursMinutes(context: Context, h: Int, m: Int): String = when {
    h == 0 -> context.getString(R.string.duration_m, m)
    m == 0 -> context.getString(R.string.duration_h, h)
    else -> context.getString(R.string.duration_hm, h, m)
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS

fun maxDurationLabel(context: Context, ms: Long?): String =
    if (ms == null) context.getString(R.string.option_off) else formatHoursMinutes(context, (ms / HOUR_MS).toInt(), 0)

fun idleLimitLabel(context: Context, ms: Long?): String =
    if (ms == null) context.getString(R.string.option_off) else formatHoursMinutes(context, 0, (ms / MINUTE_MS).toInt())

fun graceLabel(context: Context, ms: Long): String = formatHoursMinutes(context, 0, (ms / MINUTE_MS).toInt())

/** 사진 위치: "12.3 km 지점" / "12.3 km 지점 · 18 m 옆" */
fun describePhotoPosition(context: Context, alongTrackM: Double?, offsetM: Double?): String? {
    val km = (alongTrackM ?: return null) / 1000.0
    val locale = context.currentLocale()
    val base = context.getString(R.string.photo_position_km, String.format(locale, "%.1f", km))
    return if (offsetM != null) context.getString(R.string.photo_position_offset, base, String.format(locale, "%.0f", offsetM)) else base
}

/** 앱 종료 기록: 종료 이유 설명 */
@StringRes fun kr.co.nuni.gildam.data.diagnostics.ExitKind.labelRes(): Int = when (this) {
    kr.co.nuni.gildam.data.diagnostics.ExitKind.ANR -> R.string.exit_reason_anr
    kr.co.nuni.gildam.data.diagnostics.ExitKind.CRASH -> R.string.exit_reason_crash
    kr.co.nuni.gildam.data.diagnostics.ExitKind.LOW_MEMORY -> R.string.exit_reason_low_memory
    kr.co.nuni.gildam.data.diagnostics.ExitKind.EXCESSIVE_RESOURCE_USAGE -> R.string.exit_reason_excessive_resource_usage
    kr.co.nuni.gildam.data.diagnostics.ExitKind.USER_REQUESTED -> R.string.exit_reason_user_requested
    kr.co.nuni.gildam.data.diagnostics.ExitKind.EXIT_SELF -> R.string.exit_reason_exit_self
    kr.co.nuni.gildam.data.diagnostics.ExitKind.SIGNALED -> R.string.exit_reason_signaled
    kr.co.nuni.gildam.data.diagnostics.ExitKind.APP_UPDATED -> R.string.exit_reason_app_updated
    kr.co.nuni.gildam.data.diagnostics.ExitKind.OTHER -> R.string.exit_reason_other
}

@StringRes fun kr.co.nuni.gildam.data.gpxcompare.GpxParseException.Reason.messageRes(): Int = when (this) {
    kr.co.nuni.gildam.data.gpxcompare.GpxParseException.Reason.OPEN_FAILED -> R.string.gpx_open_failed
    kr.co.nuni.gildam.data.gpxcompare.GpxParseException.Reason.UNREADABLE -> R.string.gpx_parse_failed
    kr.co.nuni.gildam.data.gpxcompare.GpxParseException.Reason.TOO_FEW_POINTS -> R.string.gpx_too_few_points
}
