package kr.co.nuni.gildam.data.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * '카메라로 찍기' 사진 자리 만들기.
 * 사진은 일반 카메라 사진과 같은 곳(DCIM/Camera)에 MediaStore 항목으로 만들어지고,
 * 길담은 그 URI 만 연결합니다 (앱 폴더로 복사하거나 서버로 올리지 않음).
 * 위치·좌표는 로그에 남기지 않습니다.
 */
object CameraPhotoStore {
    private const val RELATIVE_DIR = "DCIM/Camera"

    /**
     * 카메라 앱이 사진을 써 넣을 빈 항목을 만듭니다.
     * - Android 10 이상: RELATIVE_PATH=DCIM/Camera, IS_PENDING=1 (다 찍기 전엔 갤러리에 안 보임)
     * - Android 9 이하: DCIM/Camera 경로를 직접 지정 (저장소 쓰기 권한 필요)
     * 만들 수 없으면 null.
     */
    fun createPending(context: Context, nowMs: Long = System.currentTimeMillis()): Uri? {
        val name = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMs)) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, nowMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_DIR)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
                if (!dir.exists()) dir.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(dir, name).absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return runCatching { context.contentResolver.insert(collection, values) }.getOrNull()
    }

    /** 찍은 내용이 실제로 들어 있는지 (일부 카메라 앱은 성공해도 false 를 돌려줌) */
    fun hasContent(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize > 0 } ?: false
    }.getOrDefault(false)

    /** 다 찍었으면 갤러리에 보이게 (Android 10+ 는 IS_PENDING 해제, 9 이하는 미디어 스캔) */
    fun publish(context: Context, uri: Uri): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            context.contentResolver.update(uri, values, null, null) > 0
        } else {
            val path = context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            if (path != null) MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("image/jpeg"), null)
            true
        }
    }.getOrDefault(false)

    /** 찍다가 취소하면 빈 항목을 지움 (빈 파일이 갤러리에 남지 않게) */
    fun discard(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    /** Android 9 이하에서만 저장소 쓰기 권한이 필요 */
    val needsWritePermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
}
