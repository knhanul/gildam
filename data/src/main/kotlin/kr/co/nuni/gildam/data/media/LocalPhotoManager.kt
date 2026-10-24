package kr.co.nuni.gildam.data.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.co.nuni.gildam.data.model.UriStatus
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 확정된 사진 정책 구현체:
 * 1. 갤러리 원본을 앱 폴더로 복사하지 않고 MediaStore URI만 참조합니다.
 * 2. 목록·지도 핀을 빠르고 예쁘게 표시하기 위해 기기 내부(filesDir/thumbnails)에 작은 WebP 썸네일만 생성합니다.
 * 3. 이 썸네일 역시 클라우드(NUNI ID / OVH 서버)에 절대 업로드하지 않습니다.
 * 4. 사용자가 사진을 외부로 직접 공유할 때는 위치·시각(EXIF) 제거 사본을 생성할 수 있습니다.
 */
@Singleton
class LocalPhotoManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val thumbnailDir: File
        get() = File(context.filesDir, "thumbnails").apply {
            if (!exists()) mkdirs()
        }

    /**
     * 선택한 MediaStore URI로부터 로컬 전용 320x320 WebP 썸네일을 생성합니다.
     */
    suspend fun generateLocalThumbnail(photoId: String, mediaStoreUri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.loadThumbnail(mediaStoreUri, Size(320, 320), null)
                } else {
                    resolver.openInputStream(mediaStoreUri)?.use { input ->
                        val raw = BitmapFactory.decodeStream(input) ?: return@withContext null
                        ThumbnailUtils.extractThumbnail(raw, 320, 320)
                    } ?: return@withContext null
                }

                val outFile = File(thumbnailDir, "$photoId.webp")
                FileOutputStream(outFile).use { fos ->
                    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                    bitmap.compress(format, 82, fos)
                }
                outFile.absolutePath
            } catch (e: Exception) {
                null
            }
        }

    /**
     * MediaStore URI가 여전히 유효한지(사용자가 갤러리에서 지웠는지, 권한이 철회되었는지) 검사합니다.
     */
    suspend fun verifyMediaStoreUri(uriString: String): UriStatus = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                UriStatus.VALID
            } ?: UriStatus.MISSING_IN_GALLERY
        } catch (e: FileNotFoundException) {
            UriStatus.MISSING_IN_GALLERY
        } catch (e: SecurityException) {
            UriStatus.PERMISSION_REVOKED
        } catch (e: Exception) {
            UriStatus.MISSING_IN_GALLERY
        }
    }

    /**
     * 사용자가 사진을 직접 외부로 공유할 때 EXIF(GPS 위경도, 촬영 시각, 기기 정보)를 제거한 임시 공유 파일을 생성합니다.
     */
    suspend fun createShareableImageCopy(
        mediaStoreUri: Uri,
        stripExifLocationAndTime: Boolean
    ): File? = withContext(Dispatchers.IO) {
        try {
            val shareDir = File(context.cacheDir, "safe_share").apply { if (!exists()) mkdirs() }
            val targetFile = File(shareDir, "gildam_share_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(mediaStoreUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            if (stripExifLocationAndTime) {
                val exif = ExifInterface(targetFile)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
                exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null)
                exif.setAttribute(ExifInterface.TAG_DATETIME, null)
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null)
                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, null)
                exif.saveAttributes()
            }
            targetFile
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getLocalThumbnailCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        thumbnailDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun clearLocalThumbnailCache() = withContext(Dispatchers.IO) {
        thumbnailDir.deleteRecursively()
        thumbnailDir.mkdirs()
    }
}
