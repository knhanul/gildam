package kr.co.nuni.gildam.data.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * '카메라로 찍기' 사진 저장.
 *
 * 1. 카메라 앱에는 길담 앱 전용 임시 파일(FileProvider URI)을 넘깁니다.
 *    (예전처럼 MediaStore 의 IS_PENDING 항목을 넘기면 LG 등 일부 제조사 카메라가 쓰지 못해 사진이 사라졌음)
 * 2. 찍은 뒤 내용이 있으면 길담이 일반 카메라 사진과 같은 곳(DCIM/Camera)에 그대로(EXIF 포함) 옮기고,
 *    그 MediaStore URI 만 기록에 연결합니다. 임시 파일은 지웁니다.
 * 위치·좌표는 로그에 남기지 않습니다.
 */
object CameraPhotoStore {
    private const val RELATIVE_DIR = "DCIM/Camera"
    private const val CACHE_DIR = "camera"

    /** 카메라 앱이 사진을 써 넣을 임시 파일 (만들 수 없으면 null) */
    fun createCaptureFile(context: Context, nowMs: Long = System.currentTimeMillis()): File? = runCatching {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        File(dir, fileName(nowMs)).apply { if (exists()) delete(); createNewFile() }
    }.getOrNull()

    /** 카메라 앱에 넘길 URI */
    fun captureUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".camerafiles", file)

    /** 카메라 앱이 실제로 내용을 써 넣었는지 (결과 코드와 상관없이 파일로 판단) */
    fun hasContent(file: File): Boolean = file.exists() && file.length() > 0

    /**
     * 찍은 임시 파일을 DCIM/Camera 로 옮겨 갤러리에 보이게 합니다. 성공하면 MediaStore URI, 실패하면 null.
     * 파일 복사가 있으므로 IO 스레드에서 부르세요. 성공·실패와 상관없이 임시 파일은 지웁니다.
     */
    fun saveToGallery(context: Context, file: File, nowMs: Long = System.currentTimeMillis()): Uri? {
        try {
            if (!hasContent(file)) return null
            val name = fileName(nowMs)
            val resolver = context.contentResolver
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATE_TAKEN, nowMs)
                    put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_DIR)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                    ?: return null
                val copied = runCatching {
                    resolver.openOutputStream(uri, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
                }.getOrDefault(false)
                if (!copied) {
                    runCatching { resolver.delete(uri, null, null) }
                    return null
                }
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                if (resolver.update(uri, done, null, null) <= 0) {
                    runCatching { resolver.delete(uri, null, null) }
                    return null
                }
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
                if (!dir.exists()) dir.mkdirs()
                val target = File(dir, name)
                file.copyTo(target, overwrite = true)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATE_TAKEN, nowMs)
                    @Suppress("DEPRECATION")
                    put(MediaStore.Images.Media.DATA, target.absolutePath)
                }
                val uri = runCatching { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("image/jpeg"), null)
                uri
            }
        } catch (e: Exception) {
            return null
        } finally {
            discard(file)
        }
    }

    /** 임시 파일 지우기 (취소했거나 옮긴 뒤) */
    fun discard(file: File) {
        runCatching { file.delete() }
    }

    /** Android 9 이하에서만 DCIM 에 쓰려면 저장소 쓰기 권한이 필요 */
    val needsWritePermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    private fun fileName(nowMs: Long) =
        "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMs)) + ".jpg"
}
