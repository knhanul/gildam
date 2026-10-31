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
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/** 지도 점: 출발(초록 원) · 현재(점 + 진행 방향 화살표) · 코스 진행 점 */
private data class MapMarkers(
    val start: LatLonPoint?,
    val current: LatLonPoint?,
    val showArrow: Boolean,
    val bearing: Double,
    val onCourseProgress: LatLonPoint?,
    val offCourse: Boolean
)

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
    fitToTrack: Boolean = false,
    /** 기록 화면 카메라 모드. null 이면 예전 방식(현재 위치 고정 줌) */
    cameraMode: MapCameraMode? = null,
    /** 사용자가 지도를 직접 움직여 자동 카메라를 멈춘 상태 */
    autoCameraPaused: Boolean = false,
    /** 지도 위 카드가 가리는 위/아래 높이(px) */
    overlayTopPx: Int = 0,
    overlayBottomPx: Int = 0,
    speedMps: Float = 0f,
    distanceM: Double = 0.0,
    /** 주행 방향 각도(도, 0=북, 시계방향). HEADING 모드에서 단말기 위쪽을 진행 방향으로 맞춤 */
    bearingDeg: Float? = null,
    /** 사용자가 손으로 지도를 움직였을 때 */
    onUserGesture: () -> Unit = {},
    /** 바뀌면 자동 카메라를 즉시 다시 맞춤 ('다시 맞추기') */
    recenterKey: Int = 0,
    /** 경로선 투명도 (기록 중 1.0f, 종료 직후 남겨 둔 경로 스냅샷 0.6f) */
    routeOpacity: Float = 1.0f,
    /** 전체 화면 지도: 회전·기울이기 제스처와 나침반을 켬 (북쪽 고정을 강제하지 않음) */
    fullscreenGestures: Boolean = false,
    /** 기록 중이면 현재 위치에 진행 방향 화살표를 그림. 끝난 뒤에는 출발·도착 점만 */
    showDirectionArrow: Boolean = false,
    /** 코스를 벗어난 상태면 현재 위치 점을 경고색으로 */
    offCourse: Boolean = false,
    /** 따라갈 코스가 있으면 지도 아래쪽에 코스 앞부분이 더 보이게 (현재 위치 68%) */
    followingCourse: Boolean = false
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
            followSpanM = if (cameraMode == MapCameraMode.FOLLOW || cameraMode == MapCameraMode.HEADING) 900.0 * Math.pow(2.0, 17.0 - MapCameraPlanner.followZoom(speedMps, distanceM)) else null,
            overlayTopPx = overlayTopPx, overlayBottomPx = overlayBottomPx,
            isDarkTheme = isDarkTheme,
            showDirectionArrow = showDirectionArrow,
            offCourse = offCourse,
            modifier = modifier
        )
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    remember {
        MapLibre.getInstance(context)
        TileCache.ensure(context)
    }

    val mapView = remember {
        // 한글 등 CJK 글자는 기기 글꼴로 그려 글리프 다운로드를 줄이고 깨짐을 막음
        val options = MapLibreMapOptions.createFromAttributes(context).localIdeographFontFamily("sans-serif")
        MapView(context, options).apply {
            onCreate(null)
        }
    }
    val loadedStyleKey = remember { arrayOfNulls<String>(1) }
    val markerStyleKey = remember { arrayOfNulls<String>(1) }
    // 카메라 상태: [마지막 맞춤 경계, 마지막 맞춤 시각, 제스처 리스너 등록 여부]
    val camState = remember { object { var initialized = false; var lastFit: GeoBounds? = null; var lastFitMs = 0L; var lastFollowMs = 0L; var listener = false; var lastMode: MapCameraMode? = null; var lastKey = 0 } }
    val gestureCallback = androidx.compose.runtime.rememberUpdatedState(onUserGesture)

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

                    // 현재 위치 → 경로 마지막 점 → 코스 첫 점. 아무것도 없으면 null (카메라를 옮기지 않음, 서울로 튀지 않게)
                    val target = MapCameraPlanner.targetFor(trackPoints, currentLocation, coursePoints)

                    // 전체 화면: 회전·기울이기·나침반 허용 (기본 화면은 그대로 둠)
                    if (fullscreenGestures) {
                        val ui = map.uiSettings
                        ui.isRotateGesturesEnabled = true
                        ui.isTiltGesturesEnabled = true
                        ui.isZoomGesturesEnabled = true
                        ui.isScrollGesturesEnabled = true
                        ui.isCompassEnabled = true
                        // 상단 오버레이(코스 이름·닫기) 아래 오른쪽에 나침반
                        ui.compassGravity = android.view.Gravity.TOP or android.view.Gravity.END
                        val d = view.resources.displayMetrics.density
                        val side = (12 * d).toInt(); val top = (76 * d).toInt()
                        ui.setCompassMargins(side, top, side, side)
                    }

                    if (!camState.listener) {
                        camState.listener = true
                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == org.maplibre.android.maps.MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) gestureCallback.value()
                        }
                    }
                    if (camState.lastMode != cameraMode || camState.lastKey != recenterKey) { camState.lastMode = cameraMode; camState.lastKey = recenterKey; camState.lastFit = null; camState.lastFollowMs = 0L }
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (target == null) {
                        // 보여 줄 것이 없을 때: 지도를 처음 만들 때만 한국 전체 보기, 그 뒤엔 직전 화면 유지
                        if (!camState.initialized) {
                            camState.initialized = true
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(GildamMapConfig.KOREA_DEFAULT_LAT, GildamMapConfig.KOREA_DEFAULT_LON))
                                .zoom(GildamMapConfig.KOREA_DEFAULT_ZOOM)
                                .build()
                        }
                    } else {
                    camState.initialized = true
                    when {
                        cameraMode == null -> {
                            val fitPts = if (fitToTrack) trackPoints.ifEmpty { coursePoints } else emptyList()
                            if (fitPts.size >= 2) {
                                // 같은 선이면 한 번만 맞춤 (손으로 움직인 지도를 되돌리지 않음)
                                val raw = MapCameraPlanner.boundsOf(fitPts)!!
                                if (camState.lastFit != raw) {
                                    camState.lastFit = raw
                                    val e = MapCameraPlanner.expand(raw)
                                    val bb = org.maplibre.android.geometry.LatLngBounds.from(e.maxLat, e.maxLon, e.minLat, e.minLon)
                                    val fit = Runnable {
                                        runCatching { map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bb, 48)) }
                                    }
                                    // 아직 크기가 0이면 맞춤이 실패하므로 배치가 끝난 뒤에 맞춤
                                    if (view.width > 0 && view.height > 0) view.post(fit)
                                    else view.addOnLayoutChangeListener(object : android.view.View.OnLayoutChangeListener {
                                        override fun onLayoutChange(v: android.view.View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                                            if (r - l > 0 && b - t > 0) { v.removeOnLayoutChangeListener(this); v.post(fit) }
                                        }
                                    })
                                }
                            } else {
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(target.latitude, target.longitude))
                                    .zoom(target.zoom)
                                    .build()
                            }
                        }
                        autoCameraPaused -> Unit
                        cameraMode == MapCameraMode.OVERVIEW -> {
                            val pad = MapCameraPlanner.overlayPadding(view.width, view.height, overlayTopPx, overlayBottomPx)
                            val fit = MapCameraPlanner.overviewFit(trackPoints, currentLocation, coursePoints, pad)
                            if (fit == null) {
                                map.cameraPosition = CameraPosition.Builder().target(LatLng(target.latitude, target.longitude)).zoom(target.zoom).bearing(0.0).build()
                            } else if (MapCameraPlanner.needsRefit(camState.lastFit, fit.bounds, currentLocation, now, camState.lastFitMs)) {
                                camState.lastFit = fit.bounds; camState.lastFitMs = now
                                if (map.cameraPosition.bearing != 0.0) {
                                    map.cameraPosition = CameraPosition.Builder(map.cameraPosition).bearing(0.0).build()
                                }
                                val bb = org.maplibre.android.geometry.LatLngBounds.from(fit.bounds.maxLat, fit.bounds.maxLon, fit.bounds.minLat, fit.bounds.minLon)
                                view.post {
                                    runCatching {
                                        map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bb, pad.left, pad.top, pad.right, pad.bottom), 900)
                                    }
                                }
                            }
                        }
                        else -> { // FOLLOW (단말기 위쪽 = 북쪽) 또는 HEADING (단말기 위쪽 = 주행방향)
                            if (now - camState.lastFollowMs >= 900) {
                                camState.lastFollowMs = now
                                val pad = MapCameraPlanner.overlayPadding(view.width, view.height, overlayTopPx, overlayBottomPx)
                                map.setPadding(0, pad.top, 0, pad.bottom)
                                val targetBearing = if (cameraMode == MapCameraMode.HEADING) {
                                    MapCameraPlanner.resolveBearing(bearingDeg, trackPoints, currentLocation)
                                } else {
                                    0.0
                                }
                                val zoom = MapCameraPlanner.followZoom(speedMps, distanceM)
                                // 현재 위치를 화면 아래쪽(약 64~68%)에 두어 앞뒤 길이 함께 보이게
                                val aim = MapCameraPlanner.followCameraTarget(
                                    latitude = target.latitude,
                                    longitude = target.longitude,
                                    bearingDeg = targetBearing,
                                    zoom = zoom,
                                    viewHeightPx = view.height,
                                    topPx = pad.top,
                                    bottomPx = pad.bottom,
                                    fraction = MapCameraPlanner.followTargetFraction(followingCourse)
                                )
                                val pos = CameraPosition.Builder()
                                    .target(LatLng(aim.latitude, aim.longitude))
                                    .zoom(zoom)
                                    .bearing(targetBearing)
                                    .build()
                                runCatching { map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(pos), 700) }
                            }
                        }
                    }
                    }
                    if (cameraMode == MapCameraMode.OVERVIEW) map.setPadding(0, 0, 0, 0)

                    val geoJson = buildLineStringGeoJson(trackPoints)
                    // 코스를 따라 걷는 중이면 '지나온 부분 / 남은 부분'으로 나눠 남은 쪽을 흐리게
                    val courseSplit = MapRouteGeometry.splitCourseAt(coursePoints, currentLocation)
                    val walkedCourseGeoJson = buildLineStringGeoJson(courseSplit?.walked ?: emptyList())
                    val remainingCourseGeoJson = buildLineStringGeoJson(courseSplit?.remaining ?: coursePoints)
                    val markers = MapMarkers(
                        start = trackPoints.firstOrNull() ?: coursePoints.firstOrNull(),
                        current = currentLocation ?: trackPoints.lastOrNull(),
                        // 끝난 뒤(스냅샷, 반투명 선)에는 화살표 대신 도착 점만
                        showArrow = showDirectionArrow && routeOpacity > 0.9f,
                        bearing = MapCameraPlanner.resolveBearing(bearingDeg, trackPoints, currentLocation),
                        onCourseProgress = courseSplit?.let { if (it.walked.isNotEmpty()) currentLocation else null },
                        offCourse = offCourse
                    )
                    val existingStyle = map.style
                    if (existingStyle == null || loadedStyleKey[0] != styleUrl) {
                        loadedStyleKey[0] = styleUrl
                        map.setStyle(buildKoreanStyle(view.context, styleUrl)) { style ->
                            applyKoreanLabelsAtRuntime(style)
                            // 남은 코스(흐리게) → 지나온 코스 → 내 경로 → 점들 순서로 쌓기
                            style.addSource(GeoJsonSource(COURSE_SOURCE_ID, remainingCourseGeoJson))
                            style.addLayer(LineLayer(COURSE_LAYER_ID, COURSE_SOURCE_ID).withProperties(
                                PropertyFactory.lineColor(themeCourse.toArgb()),
                                PropertyFactory.lineOpacity(0.42f),
                                PropertyFactory.lineDasharray(arrayOf(1.2f, 1.6f)),
                                PropertyFactory.lineWidth(7f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                            ))
                            style.addSource(GeoJsonSource(WALKED_SOURCE_ID, walkedCourseGeoJson))
                            style.addLayer(LineLayer(WALKED_LAYER_ID, WALKED_SOURCE_ID).withProperties(
                                PropertyFactory.lineColor(themeCourse.toArgb()),
                                PropertyFactory.lineOpacity(0.95f),
                                PropertyFactory.lineWidth(9f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                            ))
                            style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, geoJson))
                            val lineColor = String.format("#%06X", 0xFFFFFF and themeRoute.toArgb())
                            style.addLayer(LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                                PropertyFactory.lineColor(Color.parseColor(lineColor)),
                                PropertyFactory.lineOpacity(routeOpacity),
                                PropertyFactory.lineWidth(5.5f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                            ))
                            addMarkerLayers(
                                style,
                                startColor = START_COLOR,
                                routeColor = 0xFF000000.toInt() or (0xFFFFFF and themeRoute.toArgb()),
                                markers = markers
                            )
                            markerStyleKey[0] = markersKey(markers)
                        }
                    } else {
                        existingStyle.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(geoJson)
                        existingStyle.getSourceAs<GeoJsonSource>(COURSE_SOURCE_ID)?.setGeoJson(remainingCourseGeoJson)
                        existingStyle.getSourceAs<GeoJsonSource>(WALKED_SOURCE_ID)?.setGeoJson(walkedCourseGeoJson)
                        existingStyle.getLayerAs<LineLayer>(ROUTE_LAYER_ID)?.setProperties(
                            PropertyFactory.lineOpacity(routeOpacity)
                        )
                        // 점·화살표는 바뀔 때만 갱신
                        val key = markersKey(markers)
                        if (markerStyleKey[0] != key) {
                            markerStyleKey[0] = key
                            updateMarkerSources(existingStyle, markers)
                        }
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
    modifier: Modifier = Modifier,
    /** 따라가기 모드: 현재 위치를 가운데에 두고 이 폭(m)만 보여줌 */
    followSpanM: Double? = null,
    overlayTopPx: Int = 0,
    overlayBottomPx: Int = 0,
    showDirectionArrow: Boolean = false,
    offCourse: Boolean = false
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
            val pad = MapCameraPlanner.overlayPadding(size.width.toInt(), size.height.toInt(), overlayTopPx, overlayBottomPx)
            val availW = size.width - pad.left - pad.right; val availH = size.height - pad.top - pad.bottom
            val center = currentLocation ?: trackPoints.lastOrNull()
            val sc: Float; val ox: Float; val oy: Float; val minLon: Double; val maxLat: Double; val kx: Double
            if (followSpanM != null && center != null) {
                // 따라가기: 현재 위치 중심, 고정 폭
                kx = kotlin.math.cos(Math.toRadians(center.latitude))
                val spanDeg = followSpanM / 111_320.0
                sc = (availW / spanDeg).toFloat()
                minLon = center.longitude; maxLat = center.latitude
                ox = pad.left + availW / 2; oy = pad.top + availH / 2
            } else {
                val fit = MapCameraPlanner.expand(MapCameraPlanner.boundsOf(all + listOfNotNull(currentLocation))!!)
                kx = kotlin.math.cos(Math.toRadians((fit.minLat + fit.maxLat) / 2))
                val spanX = fit.lonSpan * kx; val spanY = fit.latSpan
                sc = minOf(availW / spanX.toFloat(), availH / spanY.toFloat())
                minLon = fit.minLon; maxLat = fit.maxLat
                ox = pad.left + (availW - spanX.toFloat() * sc) / 2; oy = pad.top + (availH - spanY.toFloat() * sc) / 2
            }
            fun pt(p: LatLonPoint) = Offset(ox + ((p.longitude - minLon) * kx).toFloat() * sc, oy + (maxLat - p.latitude).toFloat() * sc)
            fun line(ps: List<LatLonPoint>) = Path().apply {
                ps.forEachIndexed { i, p ->
                    val o = pt(p)
                    if (i == 0 || p.isPauseBoundary) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                }
            }
            if (coursePoints.size >= 2) {
                drawPath(line(coursePoints), courseColor.copy(alpha = 0.75f), style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(courseColor, 14f, pt(coursePoints.first()))
                drawCircle(androidx.compose.ui.graphics.Color.White, 6f, pt(coursePoints.first()))
                drawCircle(courseColor, 14f, pt(coursePoints.last()))
            }
            if (trackPoints.size >= 2) {
                drawPath(line(trackPoints), routeColor, style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            // 출발 점(초록 원): 내 기록의 첫 점 (코스만 있는 지도는 코스 시작·끝 점을 따로 그림)
            trackPoints.firstOrNull()?.let { st ->
                val o = pt(st)
                drawCircle(androidx.compose.ui.graphics.Color.White, 11f, o)
                drawCircle(androidx.compose.ui.graphics.Color(0xFF2E7D4F), 8f, o)
            }
            (currentLocation ?: trackPoints.lastOrNull())?.let { c ->
                if (trackPoints.isNotEmpty() || currentLocation != null) {
                    val dot = if (offCourse) androidx.compose.ui.graphics.Color(0xFFE06A1B) else routeColor
                    drawCircle(dot.copy(alpha = 0.25f), 30f, pt(c))
                    drawCircle(androidx.compose.ui.graphics.Color.White, 15f, pt(c))
                    drawCircle(dot, 11f, pt(c))
                    if (showDirectionArrow) {
                        // 진행 방향 화살표 머리 (미리보기에서도 방향이 보이게)
                        val o = pt(c)
                        val ang = Math.toRadians(
                            ((MapCameraPlanner.resolveBearing(null, trackPoints, currentLocation) % 360.0 + 360.0) % 360.0)
                        )
                        val dir = Offset(kotlin.math.sin(ang).toFloat(), -kotlin.math.cos(ang).toFloat())
                        val base = o + dir * 26f
                        val side = Offset(-dir.y, dir.x) * 8f
                        val head = Path().apply {
                            moveTo((base + dir * 12f).x, (base + dir * 12f).y)
                            lineTo((base + side).x, (base + side).y)
                            lineTo((base - side).x, (base - side).y)
                            close()
                        }
                        drawPath(head, dot)
                    }
                }
            }
        }
    }
}

internal fun splitIntoSegments(points: List<LatLonPoint>): List<List<LatLonPoint>> {
    if (points.isEmpty()) return emptyList()
    val segments = mutableListOf<MutableList<LatLonPoint>>()
    var current = mutableListOf<LatLonPoint>()
    for (pt in points) {
        if (pt.isPauseBoundary && current.isNotEmpty()) {
            segments.add(current)
            current = mutableListOf()
        }
        current.add(pt)
    }
    if (current.isNotEmpty()) {
        segments.add(current)
    }
    return segments.filter { it.size >= 2 }
}

private val EMPTY_FEATURE_COLLECTION = """{"type":"FeatureCollection","features":[]}"""

private fun buildPointGeoJson(p: LatLonPoint?): String =
    if (p == null) EMPTY_FEATURE_COLLECTION
    else """{"type":"Feature","geometry":{"type":"Point","coordinates":[${p.longitude},${p.latitude}]}}"""

/**
 * 진행 방향 화살표 머리(삼각형) GeoJSON. 현재 점을 중심으로 [bearingDeg] 쪽을 향합니다.
 * 지도 이미지를 따로 만들지 않고 다각형으로 그려 가볍게 유지합니다.
 */
private fun buildArrowGeoJson(p: LatLonPoint?, bearingDeg: Double): String {
    if (p == null) return EMPTY_FEATURE_COLLECTION
    val theta = Math.toRadians(((bearingDeg % 360.0) + 360.0) % 360.0)
    val mPerDegLat = 111_320.0
    val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(p.latitude)).coerceAtLeast(1e-6)
    fun offset(meters: Double, angleRad: Double): Pair<Double, Double> {
        val a = theta + angleRad
        return (p.latitude + meters * kotlin.math.cos(a) / mPerDegLat) to
            (p.longitude + meters * kotlin.math.sin(a) / mPerDegLon)
    }
    val tip = offset(17.0, 0.0)
    val left = offset(6.0, Math.toRadians(155.0))
    val right = offset(6.0, Math.toRadians(-155.0))
    fun ring(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>) =
        "[[${a.second},${a.first}],[${b.second},${b.first}],[${c.second},${c.first}],[${a.second},${a.first}]]"
    return """{"type":"Feature","geometry":{"type":"Polygon","coordinates":${ring(tip, left, right)}}}"""
}

/** 점들을 바뀔 때만 다시 그리기 위한 키 (위치·상태 요약) */
private fun markersKey(m: MapMarkers): String {
    fun p(x: LatLonPoint?) = x?.let { "%.6f,%.6f".format(java.util.Locale.ROOT, it.latitude, it.longitude) } ?: "-"
    return "${p(m.start)}|${p(m.current)}|${m.showArrow}|${(m.bearing * 2).toInt()}|${p(m.onCourseProgress)}|${m.offCourse}"
}

/** 출발 점 · 코스 진행 점 · 현재 점 · 방향 화살표 레이어를 스타일 위에 올립니다 (선보다 위) */
private fun addMarkerLayers(
    style: Style,
    startColor: Int,
    routeColor: Int,
    markers: MapMarkers
) {
    val white = Color.WHITE
    style.addSource(GeoJsonSource(START_SOURCE_ID, buildPointGeoJson(markers.start)))
    style.addLayer(
        CircleLayer(START_LAYER_ID, START_SOURCE_ID).withProperties(
            PropertyFactory.circleColor(startColor),
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleStrokeColor(white),
            PropertyFactory.circleStrokeWidth(2f)
        )
    )

    style.addSource(GeoJsonSource(PROGRESS_SOURCE_ID, buildPointGeoJson(markers.onCourseProgress)))
    style.addLayer(
        CircleLayer(PROGRESS_LAYER_ID, PROGRESS_SOURCE_ID).withProperties(
            PropertyFactory.circleColor(white),
            PropertyFactory.circleRadius(3.5f),
            PropertyFactory.circleStrokeColor(routeColor),
            PropertyFactory.circleStrokeWidth(1.5f)
        )
    )

    val currentColor = if (markers.offCourse) OFF_COURSE_COLOR else routeColor
    style.addSource(GeoJsonSource(CURRENT_SOURCE_ID, buildPointGeoJson(markers.current)))
    style.addLayer(
        CircleLayer(CURRENT_LAYER_ID, CURRENT_SOURCE_ID).withProperties(
            PropertyFactory.circleColor(currentColor),
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleStrokeColor(white),
            PropertyFactory.circleStrokeWidth(3f)
        )
    )

    style.addSource(GeoJsonSource(ARROW_SOURCE_ID, buildArrowGeoJson(markers.current.takeIf { markers.showArrow }, markers.bearing)))
    style.addLayer(
        FillLayer(ARROW_LAYER_ID, ARROW_SOURCE_ID).withProperties(
            PropertyFactory.fillColor(currentColor),
            PropertyFactory.fillOpacity(0.95f)
        )
    )
}

/** 점·화살표 데이터만 갱신 (레이어는 그대로) */
private fun updateMarkerSources(style: Style, markers: MapMarkers) {
    style.getSourceAs<GeoJsonSource>(START_SOURCE_ID)?.setGeoJson(buildPointGeoJson(markers.start))
    style.getSourceAs<GeoJsonSource>(PROGRESS_SOURCE_ID)?.setGeoJson(buildPointGeoJson(markers.onCourseProgress))
    style.getSourceAs<GeoJsonSource>(CURRENT_SOURCE_ID)?.setGeoJson(buildPointGeoJson(markers.current))
    style.getSourceAs<GeoJsonSource>(ARROW_SOURCE_ID)?.setGeoJson(buildArrowGeoJson(markers.current.takeIf { markers.showArrow }, markers.bearing))
}

private fun buildLineStringGeoJson(points: List<LatLonPoint>): String {
    val segments = splitIntoSegments(points)
    if (segments.isEmpty()) {
        return """{"type":"FeatureCollection","features":[]}"""
    }
    val multiCoords = segments.joinToString(",") { seg ->
        val coords = seg.joinToString(",") { "[${it.longitude},${it.latitude}]" }
        "[$coords]"
    }
    return """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"MultiLineString","coordinates":[$multiCoords]}}]}"""
}

private val LocalMapPreviewColors = androidx.compose.runtime.staticCompositionLocalOf {
    Triple(androidx.compose.ui.graphics.Color(0xFFF0ECE3), androidx.compose.ui.graphics.Color(0xFFD95D39), androidx.compose.ui.graphics.Color(0xFF2E4F3E))
}

private const val ROUTE_SOURCE_ID = "gildam-route-source"
private const val ROUTE_LAYER_ID = "gildam-route-layer"
private const val COURSE_SOURCE_ID = "gildam-course-source"
private const val COURSE_LAYER_ID = "gildam-course-layer"
private const val WALKED_SOURCE_ID = "gildam-course-walked-source"
private const val WALKED_LAYER_ID = "gildam-course-walked-layer"
private const val START_SOURCE_ID = "gildam-start-source"
private const val START_LAYER_ID = "gildam-start-layer"
private const val CURRENT_SOURCE_ID = "gildam-current-source"
private const val CURRENT_LAYER_ID = "gildam-current-layer"
private const val ARROW_SOURCE_ID = "gildam-arrow-source"
private const val ARROW_LAYER_ID = "gildam-arrow-layer"
private const val PROGRESS_SOURCE_ID = "gildam-progress-source"
private const val PROGRESS_LAYER_ID = "gildam-progress-layer"

/** 출발 점 색 (숲색 계열 초록) */
private val START_COLOR = Color.parseColor("#2E7D4F")
/** 코스를 벗어났을 때 현재 점 색 (따뜻한 경고 주황) */
private val OFF_COURSE_COLOR = Color.parseColor("#E06A1B")


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
