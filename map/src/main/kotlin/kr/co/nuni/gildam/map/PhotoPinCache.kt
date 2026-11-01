package kr.co.nuni.gildam.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap

/**
 * 지도 위 사진 핀 이미지 (작은 원형 썸네일 + 테두리).
 * 사진 파일은 복사하지 않고, URI 스트림을 디코딩해 메모리에만 캐시합니다 (디스크 저장·전송 없음).
 */
internal object PhotoPinCache {
    private const val MAX_ENTRIES = 40
    private val cache = object : LruCache<String, Bitmap>(MAX_ENTRIES) {}

    /** [sizePx] 크기의 원형 썸네일. 실패하면 테마색 원만 있는 핀 */
    fun load(context: Context, uri: String, sizePx: Int, ringColor: Int): Bitmap {
        val key = "$uri|$sizePx|$ringColor"
        cache.get(key)?.let { return it }
        val thumb = runCatching { decodeThumbnail(context, uri, sizePx) }.getOrNull()
        val pin = drawPin(thumb, sizePx, ringColor)
        cache.put(key, pin)
        return pin
    }

    /** 메모리 캐시만 비움 (사진 원본은 건드리지 않음) */
    fun clear() = cache.evictAll()

    private fun decodeThumbnail(context: Context, uri: String, sizePx: Int): Bitmap? {
        val resolver = context.contentResolver
        val u = Uri.parse(uri)
        // 먼저 크기만 읽어 inSampleSize 결정 (원본 전체를 메모리에 올리지 않음)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > sizePx * 2 && bounds.outHeight / sample > sizePx * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun drawPin(thumb: Bitmap?, sizePx: Int, ringColor: Int): Bitmap {
        val s = sizePx.coerceAtLeast(16)
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val ring = s * 0.11f
        val inner = RectF(ring, ring, s - ring, s - ring)
        // 테두리 원
        c.drawCircle(s / 2f, s / 2f, s / 2f - ring / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ringColor; style = Paint.Style.STROKE; strokeWidth = ring
        })
        if (thumb == null) {
            c.drawCircle(s / 2f, s / 2f, s / 2f - ring * 1.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ringColor; style = Paint.Style.FILL
            })
            return out
        }
        // 원형으로 잘라 그리기
        val layer = c.saveLayer(inner, null)
        c.drawCircle(s / 2f, s / 2f, s / 2f - ring * 1.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        val src = centerCrop(thumb, (s - ring * 3.2f).toInt())
        c.drawBitmap(src, null, RectF(ring * 1.6f, ring * 1.6f, s - ring * 1.6f, s - ring * 1.6f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
        c.restoreToCount(layer)
        return out
    }

    private fun centerCrop(bmp: Bitmap, target: Int): Bitmap {
        val side = minOf(bmp.width, bmp.height)
        val x = (bmp.width - side) / 2
        val y = (bmp.height - side) / 2
        return Bitmap.createBitmap(bmp, x, y, side, side).let { square ->
            if (square.width == target) square else Bitmap.createScaledBitmap(square, target, target, true)
        }
    }
}
