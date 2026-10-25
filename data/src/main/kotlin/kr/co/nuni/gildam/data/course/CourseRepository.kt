package kr.co.nuni.gildam.data.course

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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

    /** SAF 로 고른 GPX 를 읽어 저장. 실패한 파일 수를 돌려줌. */
    suspend fun importFromUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var failed = 0
        for (uri in uris) {
            runCatching {
                val pts = context.contentResolver.openInputStream(uri)!!.use(GpxTrackParser::parse)
                val name = displayName(uri)
                val r = GpxRuggedness.analyze(name, pts)
                dao.upsert(
                    CourseEntity(
                        id = UUID.randomUUID().toString(), name = name, importedAtEpochMs = System.currentTimeMillis(),
                        distanceM = r.distanceM, ascentM = r.ascentM, hasElevation = r.hasElevation, score = r.score,
                        baseDurationSec = HikingTimeEstimator.estimateSeconds(r.profile, r.distanceM).toLong(),
                        pointsEncoded = CoursePointCodec.encode(pts)
                    )
                )
            }.onFailure { failed++ }
        }
        failed
    }

    private fun displayName(uri: Uri): String {
        val raw = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "코스"
        return raw.removeSuffix(".gpx").removeSuffix(".GPX")
    }
}
