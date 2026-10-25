package kr.co.nuni.gildam.map

import android.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kr.co.nuni.gildam.recording.LatLonPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.SymbolLayer
import android.content.Context
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

data class MapWaypointMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val hasPhoto: Boolean
)

/**
 * MapLibre Native SDK Compose 컴포넌트.
 * - 실시간 폴리라인(Terracotta #D95D39 / Dark Coral #FF8A65)을 GeoJSON LineLayer로 렌더링합니다.
 * - Android Studio Preview 모드에서는 MapLibre 네이티브 초기화 대신 벡터 캔버스 프리뷰를 자동 표시합니다.
 */
@Composable
fun GildamMapLibreView(
    trackPoints: List<LatLonPoint>,
    currentLocation: LatLonPoint?,
    waypointMarkers: List<MapWaypointMarker> = emptyList(),
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier,
    /** 따라 걷는 코스 선 (기록 선 아래에 숲색으로 그림) */
    coursePoints: List<LatLonPoint> = emptyList(),
    /** true 면 선 전체가 보이게 카메라를 맞춤 (코스 미리보기) */
    fitToTrack: Boolean = false
) {
    val isPreview = LocalInspectionMode.current
    val themeRoute = MaterialTheme.colorScheme.primary
    val themeCourse = MaterialTheme.colorScheme.secondary
    val themeBg = MaterialTheme.colorScheme.surfaceVariant
    if (isPreview) {
        androidx.compose.runtime.CompositionLocalProvider(LocalMapPreviewColors provides Triple(themeBg, themeRoute, themeCourse)) {
        PreviewFallbackMapCanvas(
            trackPoints = trackPoints,
            coursePoints = coursePoints,
            currentLocation = currentLocation,
            isDarkTheme = isDarkTheme,
            modifier = modifier
        )
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    remember {
        MapLibre.getInstance(context)
    }

    val mapView = remember {
        // 한글 등 CJK 글자는 기기 글꼴로 그려 글리프 다운로드를 줄이고 깨짐을 막음
        val options = MapLibreMapOptions.createFromAttributes(context).localIdeographFontFamily("sans-serif")
        MapView(context, options).apply {
            onCreate(null)
        }
    }
    val loadedStyleKey = remember { arrayOfNulls<String>(1) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { view ->
                view.getMapAsync { map ->
                    val styleUrl = if (isDarkTheme) GildamMapConfig.DARK_STYLE_URL else GildamMapConfig.LIGHT_STYLE_URL

                    val targetLat = currentLocation?.latitude
                        ?: trackPoints.lastOrNull()?.latitude
                        ?: GildamMapConfig.SEOUL_DEFAULT_LAT
                    val targetLon = currentLocation?.longitude
                        ?: trackPoints.lastOrNull()?.longitude
                        ?: GildamMapConfig.SEOUL_DEFAULT_LON

                    val fitPts = if (fitToTrack) trackPoints.ifEmpty { coursePoints } else emptyList()
                    if (fitPts.size >= 2) {
                        val b = org.maplibre.android.geometry.LatLngBounds.Builder()
                        fitPts.forEach { b.include(LatLng(it.latitude, it.longitude)) }
                        view.post {
                            runCatching { map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(b.build(), 64)) }
                        }
                    } else {
                        val c = if (currentLocation == null && trackPoints.isEmpty()) coursePoints.firstOrNull() else null
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(c?.latitude ?: targetLat, c?.longitude ?: targetLon))
                            .zoom(15.2)
                            .build()
                    }

                    val geoJson = buildLineStringGeoJson(trackPoints)
                    val courseGeoJson = buildLineStringGeoJson(coursePoints)
                    val existingStyle = map.style
                    if (existingStyle == null || loadedStyleKey[0] != styleUrl) {
                        loadedStyleKey[0] = styleUrl
                        map.setStyle(buildKoreanStyle(view.context, styleUrl)) { style ->
                            applyKoreanLabelsAtRuntime(style)
                            style.addSource(GeoJsonSource(COURSE_SOURCE_ID, courseGeoJson))
                            style.addLayer(LineLayer(COURSE_LAYER_ID, COURSE_SOURCE_ID).withProperties(
                                PropertyFactory.lineColor(themeCourse.toArgb()),
                                PropertyFactory.lineOpacity(0.75f),
                                PropertyFactory.lineWidth(8f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                            ))
                            val source = GeoJsonSource(ROUTE_SOURCE_ID, geoJson)
                            style.addSource(source)
                            val lineColor = String.format("#%06X", 0xFFFFFF and themeRoute.toArgb())
                            val lineLayer = LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                                PropertyFactory.lineColor(Color.parseColor(lineColor)),
                                PropertyFactory.lineWidth(5.5f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                            )
                            style.addLayer(lineLayer)
                        }
                    } else {
                        existingStyle.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(geoJson)
                        existingStyle.getSourceAs<GeoJsonSource>(COURSE_SOURCE_ID)?.setGeoJson(courseGeoJson)
                    }
                }
            }
        )

        // OpenStreetMap 필수 저작권 표기 배지
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
        ) {
            Text(
                text = GildamMapConfig.ATTRIBUTION_TEXT,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun PreviewFallbackMapCanvas(
    trackPoints: List<LatLonPoint>,
    coursePoints: List<LatLonPoint>,
    currentLocation: LatLonPoint?,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDarkTheme) androidx.compose.ui.graphics.Color(0xFF141816)
    else androidx.compose.ui.graphics.Color(0xFFEAE4D8)
    val (themeBg, routeColor, courseColor) = LocalMapPreviewColors.current

    val finalBg = if (themeBg.luminance() < 0.5f) themeBg else androidx.compose.ui.graphics.lerp(bgColor, themeBg, 0.6f)
    @Suppress("NAME_SHADOWING") val gridColor = androidx.compose.ui.graphics.lerp(finalBg, androidx.compose.ui.graphics.Color.Gray, 0.14f)
    Box(modifier = modifier.background(finalBg)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 48f
            var gx = 0f; while (gx < size.width) { drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), 1.5f); gx += step }
            var gy = 0f; while (gy < size.height) { drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), 1.5f); gy += step }
            val all = coursePoints + trackPoints
            if (all.size < 2) {
                val path = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.75f)
                    cubicTo(size.width * 0.35f, size.height * 0.55f, size.width * 0.55f, size.height * 0.62f, size.width * 0.82f, size.height * 0.25f)
                }
                drawPath(path, routeColor, style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                return@Canvas
            }
            val minLat = all.minOf { it.latitude }; val maxLat = all.maxOf { it.latitude }
            val minLon = all.minOf { it.longitude }; val maxLon = all.maxOf { it.longitude }
            val kx = kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2))
            val spanX = ((maxLon - minLon) * kx).coerceAtLeast(1e-6); val spanY = (maxLat - minLat).coerceAtLeast(1e-6)
            val pad = 0.12f
            val sc = minOf(size.width * (1 - 2 * pad) / spanX.toFloat(), size.height * (1 - 2 * pad) / spanY.toFloat())
            val ox = (size.width - spanX.toFloat() * sc) / 2; val oy = (size.height - spanY.toFloat() * sc) / 2
            fun pt(p: LatLonPoint) = Offset(ox + ((p.longitude - minLon) * kx).toFloat() * sc, oy + (maxLat - p.latitude).toFloat() * sc)
            fun line(ps: List<LatLonPoint>) = Path().apply { ps.forEachIndexed { i, p -> val o = pt(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            if (coursePoints.size >= 2) {
                drawPath(line(coursePoints), courseColor.copy(alpha = 0.75f), style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(courseColor, 14f, pt(coursePoints.first()))
                drawCircle(androidx.compose.ui.graphics.Color.White, 6f, pt(coursePoints.first()))
                drawCircle(courseColor, 14f, pt(coursePoints.last()))
            }
            if (trackPoints.size >= 2) {
                drawPath(line(trackPoints), routeColor, style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            (currentLocation ?: trackPoints.lastOrNull())?.let { c ->
                if (trackPoints.isNotEmpty() || currentLocation != null) {
                    drawCircle(routeColor.copy(alpha = 0.25f), 30f, pt(c))
                    drawCircle(androidx.compose.ui.graphics.Color.White, 15f, pt(c))
                    drawCircle(routeColor, 11f, pt(c))
                }
            }
        }
    }
}

private fun buildLineStringGeoJson(points: List<LatLonPoint>): String {
    if (points.size < 2) {
        return """{"type":"FeatureCollection","features":[]}"""
    }
    val coords = points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    return """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]}}]}"""
}

private val LocalMapPreviewColors = androidx.compose.runtime.staticCompositionLocalOf {
    Triple(androidx.compose.ui.graphics.Color(0xFFF0ECE3), androidx.compose.ui.graphics.Color(0xFFD95D39), androidx.compose.ui.graphics.Color(0xFF2E4F3E))
}

private const val ROUTE_SOURCE_ID = "gildam-route-source"
private const val ROUTE_LAYER_ID = "gildam-route-layer"
private const val COURSE_SOURCE_ID = "gildam-course-source"
private const val COURSE_LAYER_ID = "gildam-course-layer"


/**
 * 스타일 빌더: asset:// 이면 앱 내장 JSON 을 읽어 라벨을 한글로 바꾼 뒤 사용,
 * https:// 이면 그대로 불러오고 로드 후 [applyKoreanLabelsAtRuntime] 로 바꿈.
 */
private fun buildKoreanStyle(context: Context, styleUrl: String): Style.Builder {
    if (styleUrl.startsWith(GildamMapConfig.ASSET_PREFIX)) {
        val path = styleUrl.removePrefix(GildamMapConfig.ASSET_PREFIX)
        val raw = runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
        if (raw != null) {
            val localized = runCatching { KoreanLabelLocalizer.localizeStyleJson(raw) }.getOrDefault(raw)
            return Style.Builder().fromJson(localized)
        }
    }
    return Style.Builder().fromUri(styleUrl)
}

/** 원격 스타일 등 이미 로드된 스타일의 이름 라벨을 name:ko → name 순서로 바꿈 (내장 스타일엔 영향 없음). */
private fun applyKoreanLabelsAtRuntime(style: Style) {
    style.layers.filterIsInstance<SymbolLayer>().forEach { layer ->
        val expr = layer.textField.expression ?: return@forEach
        if (KoreanLabelLocalizer.usesNameField(expr.toString())) {
            layer.setProperties(
                PropertyFactory.textField(Expression.coalesce(Expression.get("name:ko"), Expression.get("name")))
            )
        }
    }
}
