package kr.co.nuni.gildam.map

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate

/**
 * 기록·따라가기 중 현재 위치를 나타내는 내비게이션 화살촉.
 * 지도 위에서는 항상 GPS 진행 방향을 가리키며, 지도가 돌아가면(주행 방향 위) 화면에서는 위를 향합니다.
 */
internal object LocationArrow {
    const val IMAGE_ID = "gildam-location-arrow"
    /** 화살표 몸통 한 변(dp). 출발점 S(지름 30dp)보다 조금 크게 해서 구분되게 */
    const val SIZE_DP = 36f
    /** 그림자·흰 테두리가 잘리지 않게 비트맵에 더하는 여백(dp) */
    const val PAD_DP = 6f

    fun imageId(fillColor: Int): String = "$IMAGE_ID-${fillColor and 0xFFFFFF}"

    /**
     * 지도가 [mapBearingDeg] 만큼 돌아간 화면에서, 진행 방향 [headingDeg] 화살표가
     * 가리킬 화면 각도(0°=위, 시계방향).
     */
    fun screenRotationDeg(headingDeg: Double, mapBearingDeg: Double = 0.0): Double =
        ((headingDeg - mapBearingDeg) % 360.0 + 360.0) % 360.0

    /**
     * 위쪽을 가리키는 화살촉(뒤로 살짝 파인 셰브론)을 [path]에 넣습니다.
     * 좌표는 한 변 [size]인 정사각형, 중심이 현재 위치.
     */
    fun addChevron(path: AndroidPath, size: Float, offsetX: Float = 0f, offsetY: Float = 0f) {
        path.reset()
        chevronPoints(size).forEachIndexed { i, (x, y) ->
            val px = offsetX + x
            val py = offsetY + y
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    fun createBitmap(density: Float, fillColor: Int): Bitmap {
        val d = density.coerceAtLeast(1f)
        val body = SIZE_DP * d
        val pad = PAD_DP * d
        val s = (body + pad * 2f).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        val path = AndroidPath()
        addChevron(path, body, pad, pad)

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55000000
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(2.4f * d, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.save()
        canvas.translate(0f, 1.1f * d)
        canvas.drawPath(path, shadow)
        canvas.restore()

        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * d
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        })
        return bmp
    }

    /** 미리보기 캔버스에 같은 모양의 화살표를 그립니다. [rotationDeg] 0=위, 시계방향. */
    fun DrawScope.draw(
        center: Offset,
        rotationDeg: Float,
        fill: ComposeColor,
        showHalo: Boolean = true
    ) {
        val body = SIZE_DP * density
        if (showHalo) {
            drawCircle(fill.copy(alpha = 0.22f), radius = body * 0.62f, center = center)
        }
        rotate(rotationDeg, center) {
            translate(center.x - body / 2f, center.y - body / 2f) {
                val path = composeChevron(body)
                // 옅은 그림자
                translate(0f, 1.2f * density) {
                    drawPath(path, ComposeColor.Black.copy(alpha = 0.22f))
                }
                drawPath(path, fill)
                drawPath(
                    path,
                    ComposeColor.White,
                    style = Stroke(width = 2.2f * density, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }

    private fun composeChevron(size: Float): Path = Path().apply {
        chevronPoints(size).forEachIndexed { i, (x, y) ->
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /** 한 변 [size]인 정사각형 안의 화살촉 꼭짓점 (끝·오른날개·홈·왼날개). */
    internal fun chevronPoints(size: Float): List<Pair<Float, Float>> {
        val cx = size * 0.5f
        val cy = size * 0.5f
        return listOf(
            cx to size * 0.06f,          // 끝
            cx + size * 0.36f to size * 0.86f, // 오른
            cx to cy + size * 0.16f,     // 뒤로 파인 홈
            cx - size * 0.36f to size * 0.86f  // 왼
        )
    }
}
