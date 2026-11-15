package kr.co.nuni.gildam.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kr.co.nuni.gildam.data.weather.PrecipType
import kr.co.nuni.gildam.data.weather.SkyCondition
import kr.co.nuni.gildam.data.weather.WeatherNow
import kr.co.nuni.gildam.data.weather.WeatherSource

/**
 * 기록 시작 시점의 날씨. 로컬 전용이며 동기화하지 않습니다.
 */
@Entity(
    tableName = "weather_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["activity_id"])]
)
data class WeatherSnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "activity_id")
    val activityId: String,
    @ColumnInfo(name = "source")
    val source: String? = null,
    @ColumnInfo(name = "observed_at")
    val observedAtIso: String? = null,
    @ColumnInfo(name = "temp_c")
    val tempC: Double? = null,
    @ColumnInfo(name = "humidity")
    val humidity: Double? = null,
    @ColumnInfo(name = "precip_type")
    val precipType: String? = null,
    @ColumnInfo(name = "precip_1h_mm")
    val precip1hMm: Double? = null,
    @ColumnInfo(name = "wind_speed_ms")
    val windSpeedMs: Double? = null,
    @ColumnInfo(name = "wind_dir_deg")
    val windDirDeg: Double? = null,
    @ColumnInfo(name = "wind_gust_ms")
    val windGustMs: Double? = null,
    @ColumnInfo(name = "sky")
    val sky: String? = null
) {
    fun toWeatherNow(): WeatherNow = WeatherNow(
        source = source?.let { raw -> WeatherSource.entries.firstOrNull { it.name == raw } },
        observedAtIso = observedAtIso,
        tempC = tempC,
        humidity = humidity,
        precipType = precipType?.let { raw -> PrecipType.entries.firstOrNull { it.name == raw } } ?: PrecipType.NONE,
        precip1hMm = precip1hMm,
        windSpeedMs = windSpeedMs,
        windDirDeg = windDirDeg,
        windGustMs = windGustMs,
        sky = sky?.let { raw -> SkyCondition.entries.firstOrNull { it.name == raw } }
    )

    companion object {
        fun from(activityId: String, weather: WeatherNow) = WeatherSnapshotEntity(
            activityId = activityId,
            source = weather.source?.name,
            observedAtIso = weather.observedAtIso,
            tempC = weather.tempC,
            humidity = weather.humidity,
            precipType = weather.precipType.name,
            precip1hMm = weather.precip1hMm,
            windSpeedMs = weather.windSpeedMs,
            windDirDeg = weather.windDirDeg,
            windGustMs = weather.windGustMs,
            sky = weather.sky?.name
        )
    }
}
