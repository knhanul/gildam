package kr.co.nuni.gildam.data.photo

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 기기 갤러리(MediaStore)에서 기록 시간창 안에 찍힌 사진 후보를 찾습니다.
 *
 * - 파일은 읽지 않고 URI 만 씁니다 (복사·전송 없음). EXIF GPS 를 읽을 때는 스트림만 열고 닫습니다.
 * - 권한이 없으면 예외 없이 빈 결과 + [Result.permissionDenied] = true.
 * - 후보는 최대 [MAX_CANDIDATES] 장까지만 EXIF 를 읽습니다 (느린 기기 보호).
 */
@Singleton
class DevicePhotoFinder @Inject constructor(@ApplicationContext private val context: Context) {

    data class Result(
        val photos: List<MatchedPhoto> = emptyList(),
        val permissionDenied: Boolean = false,
        /** 시간창 안 후보 수 (잘리기 전) */
        val candidateCount: Int = 0,
        /** 후보 상한에 걸려 일부만 확인했는지 */
        val truncated: Boolean = false,
        /**
         * Android 10+ 에서 '사진 위치 읽기'(ACCESS_MEDIA_LOCATION) 권한이 없어
         * 시스템이 사진 속 GPS 를 지운 채로 보여 주는 상태인지 (허용하면 정확한 자리로 바뀜)
         */
        val locationRedacted: Boolean = false
    )

    /** 사진 접근 권한이 있는지 (Android 14 부분 허용도 '있음'으로 봄) */
    fun hasPermission(): Boolean {
        val ctx = context
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(ctx, Manifest.permission.READ_MEDIA_IMAGES) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    granted(ctx, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"))
        } else {
            granted(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * 사진 속 위치(EXIF GPS)를 읽을 수 있는지.
     * Android 10(Q)+ 는 ACCESS_MEDIA_LOCATION 이 없으면 시스템이 GPS 를 지운 사본을 넘겨 줍니다.
     */
    fun hasMediaLocationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(context, Manifest.permission.ACCESS_MEDIA_LOCATION)

    private fun granted(ctx: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * 기록 시간창([startMs],[endMs]) 안에 찍힌 사진을 찾아 경로와 짝지어 돌려줍니다.
     * [routePoints] 는 (lat, lon, recordedAtMs) 목록.
     */
    suspend fun findForRecord(
        startMs: Long,
        endMs: Long,
        routePoints: List<Triple<Double, Double, Long>>
    ): Result = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext Result(permissionDenied = true)
        val all = runCatching { queryWindow(startMs, endMs) }.getOrElse { return@withContext Result() }
        val truncated = all.size > MAX_CANDIDATES
        val canReadLocation = hasMediaLocationPermission()
        val withGps = all.take(MAX_CANDIDATES).map { (uri, taken) ->
            val gps = runCatching { readGps(uri, canReadLocation) }.getOrNull()
            PhotoCandidate(uri.toString(), taken, gps?.first, gps?.second)
        }
        val route = RecordPhotoMatcher.routeWithDistance(routePoints)
        Result(
            photos = RecordPhotoMatcher.match(startMs, endMs, route, withGps),
            candidateCount = all.size,
            truncated = truncated,
            locationRedacted = !canReadLocation && all.isNotEmpty()
        )
    }

    /** DATE_TAKEN 이 시간창 안인 이미지 (URI, 촬영 시각). 전체 스캔 없이 selection 으로 좁힘. */
    private fun queryWindow(startMs: Long, endMs: Long): List<Pair<Uri, Long>> {
        val (from, to) = RecordPhotoMatcher.queryWindow(startMs, endMs)
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN)
        val selection = "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} <= ?"
        val args = arrayOf(from.toString(), to.toString())
        val out = ArrayList<Pair<Uri, Long>>()
        context.contentResolver.query(
            collection, projection, selection, args,
            "${MediaStore.Images.Media.DATE_TAKEN} ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (c.moveToNext()) {
                val taken = c.getLong(dateCol)
                if (taken <= 0L) continue // 촬영 시각이 없는 사진은 시간으로 맞출 수 없음
                out += ContentUris.withAppendedId(collection, c.getLong(idCol)) to taken
            }
        }
        return out
    }

    /**
     * EXIF GPS 만 읽기 (사진 내용은 쓰지 않고, 스트림만 열었다 닫음). 없거나 못 읽으면 null.
     *
     * Android 10+ 는 ACCESS_MEDIA_LOCATION 을 가진 앱이 [MediaStore.setRequireOriginal] 로 연
     * 스트림에만 GPS 를 남겨 줍니다. 그냥 열면 GPS 가 지워져 모든 사진이 '위치 없음'이 됩니다.
     */
    private fun readGps(uri: Uri, canReadLocation: Boolean): Pair<Double, Double>? = try {
        val source = if (canReadLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
        } else uri
        val stream = try {
            context.contentResolver.openInputStream(source)
        } catch (e: SecurityException) {
            // 원본 요청이 거절되면(권한이 막 빠진 경우 등) 일반 스트림으로 — GPS 는 없겠지만 사진은 붙음
            if (source != uri) context.contentResolver.openInputStream(uri) else throw e
        } catch (e: UnsupportedOperationException) {
            if (source != uri) context.contentResolver.openInputStream(uri) else throw e
        }
        stream?.use { input ->
            val exif = ExifInterface(input)
            val latLong = exif.latLong
            if (latLong != null) usableGps(latLong[0], latLong[1]) else null
        }
    } catch (e: SecurityException) {
        null // 권한이 빠진 사진(부분 허용 목록 밖 등)은 위치 없이 시간으로만 판단
    } catch (e: Exception) {
        null
    }

    companion object {
        /** 지워진 GPS(0,0)·잘못된 값은 위치 없음으로 */
        internal fun usableGps(lat: Double, lon: Double): Pair<Double, Double>? = when {
            !lat.isFinite() || !lon.isFinite() -> null
            lat !in -90.0..90.0 || lon !in -180.0..180.0 -> null
            lat == 0.0 && lon == 0.0 -> null
            else -> lat to lon
        }

        /** 한 번에 EXIF 를 읽는 최대 사진 수 */
        const val MAX_CANDIDATES = 200
    }
}
