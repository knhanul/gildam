package kr.co.nuni.gildam.data.course

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kr.co.nuni.gildam.data.gpxcompare.GpxPoint

/** 가져온 GPX 코스. 좌표만 저장합니다 (시간·이름 등 개인 정보 없음). */
@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "imported_at") val importedAtEpochMs: Long,
    @ColumnInfo(name = "distance_m") val distanceM: Double,
    @ColumnInfo(name = "ascent_m") val ascentM: Double,
    @ColumnInfo(name = "has_elevation") val hasElevation: Boolean,
    /** 기존 GPX 비교 난이도 점수 */
    val score: Double,
    /** Tobler 기준 예상 소요(초), 개인 보정 전 */
    @ColumnInfo(name = "base_duration_s") val baseDurationSec: Long,
    /** "lat,lon,ele;..." (ele 없으면 빈칸) */
    @ColumnInfo(name = "points_encoded") val pointsEncoded: String
) {
    fun points(): List<GpxPoint> = CoursePointCodec.decode(pointsEncoded)
}

object CoursePointCodec {
    fun encode(points: List<GpxPoint>): String = points.joinToString(";") { p ->
        "%.6f,%.6f,%s".format(java.util.Locale.ROOT, p.lat, p.lon, p.ele?.let { "%.1f".format(java.util.Locale.ROOT, it) } ?: "")
    }
    fun decode(s: String): List<GpxPoint> = if (s.isEmpty()) emptyList() else s.split(';').mapNotNull { part ->
        val f = part.split(',')
        val lat = f.getOrNull(0)?.toDoubleOrNull(); val lon = f.getOrNull(1)?.toDoubleOrNull()
        if (lat == null || lon == null) null else GpxPoint(lat, lon, f.getOrNull(2)?.toDoubleOrNull())
    }
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY imported_at DESC")
    fun observeCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id")
    fun observeCourse(id: String): Flow<CourseEntity?>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getCourse(id: String): CourseEntity?

    @Upsert
    suspend fun upsert(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun delete(id: String)
}
