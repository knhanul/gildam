package kr.co.nuni.gildam.data.course

import kr.co.nuni.gildam.data.R
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kr.co.nuni.gildam.data.gpxcompare.GpxParseException
import kr.co.nuni.gildam.data.gpxcompare.GpxPoint
import kr.co.nuni.gildam.data.gpxcompare.GpxRuggedness
import kr.co.nuni.gildam.data.gpxcompare.GpxTrackParser
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CourseDao
) {
    fun observeCourses(): Flow<List<CourseEntity>> = dao.observeCourses()
    fun observeCourse(id: String): Flow<CourseEntity?> = dao.observeCourse(id)
    suspend fun getCourse(id: String): CourseEntity? = dao.getCourse(id)
    suspend fun delete(id: String) = dao.delete(id)

    /**
     * SAF 로 고른 GPX 를 읽어 저장. 저장한 개수와, 실패한 파일마다 그 이유를 돌려줌.
     * (이유 글자는 ui 모듈이 [GpxParseException.Reason] 으로 고름)
     */
    suspend fun importFromUris(uris: List<Uri>): CourseImportOutcome = withContext(Dispatchers.IO) {
        var imported = 0
        val failures = mutableListOf<GpxParseException.Reason>()
        for (uri in uris) {
            try {
                val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                    ?: throw GpxParseException(GpxParseException.Reason.OPEN_FAILED)
                val pts = stream.use(GpxTrackParser::parse)
                save(displayName(uri), pts)
                imported++
            } catch (e: GpxParseException) {
                failures += e.reason
            } catch (e: Exception) {
                failures += GpxParseException.Reason.UNREADABLE
            }
        }
        CourseImportOutcome(imported, failures)
    }

    /** 기록의 경로 점으로 바로 코스 만들기. 점이 2개 미만이면 null */
    suspend fun createFromPoints(name: String, pts: List<GpxPoint>): String? = withContext(Dispatchers.IO) {
        if (pts.size < 2) return@withContext null
        save(name.ifBlank { context.getString(R.string.course_default_name) }, pts)
    }

    private suspend fun save(name: String, pts: List<GpxPoint>): String {
        val r = GpxRuggedness.analyze(name, pts)
        val id = UUID.randomUUID().toString()
        dao.upsert(
            CourseEntity(
                id = id, name = name, importedAtEpochMs = System.currentTimeMillis(),
                distanceM = r.distanceM, ascentM = r.ascentM, hasElevation = r.hasElevation, score = r.score,
                baseDurationSec = HikingTimeEstimator.estimateSeconds(r.profile, r.distanceM).toLong(),
                pointsEncoded = CoursePointCodec.encode(pts)
            )
        )
        return id
    }

    private fun displayName(uri: Uri): String {
        val raw = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: context.getString(R.string.course_default_name)
        return raw.removeSuffix(".gpx").removeSuffix(".GPX")
    }
}

/** GPX 불러오기 결과: 저장한 수와 실패한 파일들의 이유 */
data class CourseImportOutcome(val imported: Int, val failures: List<GpxParseException.Reason>)
