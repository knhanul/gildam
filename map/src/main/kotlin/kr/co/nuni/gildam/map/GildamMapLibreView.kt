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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
    val offCourse: Boolean,
    /** 고도 그래프에서 고른 코스 지점 (점으로 표시) */
    val selectedCourse: LatLonPoint? = null
)

/** 지도 위 사진 핀 (기록에 붙은 기기 사진). 사진은 기기에만 있고 URI 만 참조합니다. */
data class MapPhotoMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val uri: String,
    /** 위치 정보가 없어 시간만으로 맞춘 사진 (핀 테두리를 점선 느낌의 옅은 색으로) */
    val timeOnly: Boolean = false
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
    followingCourse: Boolean = false,
    /** 고도 그래프와 연결: 코스에서 고른 지점까지 거리(km). 점으로 표시 */
    selectedCourseKm: Double? = null,
    /** 코스 선을 탭하면 그 지점의 시작점부터 거리(km). 코스가 없으면 호출 안 됨 */
    onCourseTap: ((Double) -> Unit)? = null,
    /** 기록에 붙은 사진들을 지도 위 작은 원형 핀으로 표시 (사진 자리 또는 가장 가까운 경로 점) */
    photoMarkers: List<MapPhotoMarker> = emptyList(),
    /** 사진 핀을 누르면 그 사진 id */
    onPhotoMarkerTap: ((String) -> Unit)? = null,
    /** 지도 회전각(도)이 바뀔 때 (나침반 바늘용) */
    onMapBearingChanged: (Float) -> Unit = {}
) {
    val isPreview = LocalInspectionMode.current
    val themeRoute = MaterialTheme.colorScheme.primary
    val themeCourse = MaterialTheme.colorScheme.secondary
    val themeBg = MaterialTheme.colorScheme.surfaceVariant
    if (isPreview) {
        androidx.compose.runtime.CompositionLocalProvider(LocalMapPreviewColors provides Triple(themeBg, themeRoute, themeCourse)) {
        // 미리보기: 전체 경로 보기면 실제 지도처럼 경로를 세로로 돌려 그림
        val previewBearing = if (cameraMode == MapCameraMode.OVERVIEW)
            OverviewBearingPlanner.orient(
                OverviewBearingPlanner.bestBearing(trackPoints + listOfNotNull(currentLocation) + coursePoints),
                trackPoints.ifEmpty { coursePoints }
            ) else 0.0
        androidx.compose.runtime.SideEffect { onMapBearingChanged(previewBearing.toFloat()) }
        val rot = previewRotator(trackPoints + listOfNotNull(currentLocation) + coursePoints, previewBearing)
        PreviewFallbackMapCanvas(
            trackPoints = trackPoints.map(rot),
            coursePoints = coursePoints.map(rot),
            currentLocation = currentLocation?.let(rot),
            followSpanM = if (cameraMode == MapCameraMode.FOLLOW || cameraMode == MapCameraMode.HEADING) 900.0 * Math.pow(2.0, 17.0 - MapCameraPlanner.followZoom(speedMps, distanceM)) else null,
            overlayTopPx = overlayTopPx, overlayBottomPx = overlayBottomPx,
            isDarkTheme = isDarkTheme,
            showDirectionArrow = showDirectionArrow,
            offCourse = offCourse,
            selectedCourse = selectedCourseKm?.let { km -> MapRouteGeometry.pointAtKm(coursePoints, km) },
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
    val photoKey = remember { arrayOfNulls<String>(1) }
    // 카메라 상태: [마지막 맞춤 경계, 마지막 맞춤 시각, 제스처 리스너 등록 여부]
    // 탭 → 코스 거리: 리스너는 한 번만 달기 때문에 콜백·코스 점은 항상 최신 값을 봄
    val tapCallback = androidx.compose.runtime.rememberUpdatedState(onCourseTap)
    val coursePointsNow = androidx.compose.runtime.rememberUpdatedState(coursePoints)
    val photoMarkersNow = androidx.compose.runtime.rememberUpdatedState(photoMarkers)
    val photoTapCallback = androidx.compose.runtime.rememberUpdatedState(onPhotoMarkerTap)
    val bearingCallback = androidx.compose.runtime.rememberUpdatedState(onMapBearingChanged)
    val camState = remember { object { var initialized = false; var lastFit: GeoBounds? = null; var lastFitMs = 0L; var lastFollowMs = 0L; var listener = false; var lastMode: MapCameraMode? = null; var lastKey = 0; var overviewBearing: Double? = null; var overviewOriented: Double? = null } }
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
                    } else {
                        // 기록·상세 화면은 앱 자체 나침반 버튼을 쓰므로 지도 기본 나침반은 끈다 (두 개 겹침 방지)
                        map.uiSettings.isCompassEnabled = false
                    }

                    if (!camState.listener) {
                        camState.listener = true
                        map.addOnCameraMoveListener { bearingCallback.value(map.cameraPosition.bearing.toFloat()) }
                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == org.maplibre.android.maps.MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) gestureCallback.value()
                        }
                        map.addOnMapClickListener { latLng ->
                            // 1) 사진 핀을 눌렀으면 그 사진 열기 (핀 반경 안)
                            val photoCb = photoTapCallback.value
                            if (photoCb != null) {
                                val hit = nearestPhotoMarker(photoMarkersNow.value, map, latLng)
                                if (hit != null) { photoCb(hit); return@addOnMapClickListener true }
                            }
                            // 2) 아니면 코스 위 지점 (허용 거리 안일 때만)
                            val cb = tapCallback.value ?: return@addOnMapClickListener false
                            val d = MapRouteGeometry.nearestCourseDistanceKm(
                                coursePointsNow.value, latLng.latitude, latLng.longitude, MapRouteGeometry.TAP_TOLERANCE_M
                            ) ?: return@addOnMapClickListener false
                            cb(d)
                            true
                        }
                    }
                    if (camState.lastMode != cameraMode || camState.lastKey != recenterKey) { camState.lastMode = cameraMode; camState.lastKey = recenterKey; camState.lastFit = null; camState.lastFollowMs = 0L; camState.overviewBearing = null; camState.overviewOriented = null }
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
                            val pad = MapCameraPlanner.overviewPadding(view.width, view.height, overlayTopPx, overlayBottomPx, view.resources.displayMetrics.density)
                            val fit = MapCameraPlanner.overviewFit(trackPoints, currentLocation, coursePoints, pad)
                            if (fit == null) {
                                map.cameraPosition = CameraPosition.Builder().target(LatLng(target.latitude, target.longitude)).zoom(target.zoom).bearing(0.0).build()
                            } else if (MapCameraPlanner.needsRefit(camState.lastFit, fit.bounds, currentLocation, now, camState.lastFitMs)) {
                                camState.lastFit = fit.bounds; camState.lastFitMs = now
                                // 경로가 세로 화면을 가장 잘 채우는 방향으로 한 번만 돌림 (크게 바뀔 때만 다시, 천천히)
                                val pts = ArrayList<LatLonPoint>(trackPoints.size + coursePoints.size + 1).apply {
                                    addAll(trackPoints); currentLocation?.let(::add); addAll(coursePoints)
                                }
                                val axisNow = OverviewBearingPlanner.choose(camState.overviewBearing, OverviewBearingPlanner.bestBearing(pts))
                                val rotated = axisNow != camState.overviewBearing
                                camState.overviewBearing = axisNow
                                // 방향(위/아래)은 축을 새로 정할 때만 정해서 갑자기 뒤집히지 않게
                                if (rotated || camState.overviewOriented == null) camState.overviewOriented = OverviewBearingPlanner.orient(axisNow, trackPoints.ifEmpty { pts })
                                val bearingNow = camState.overviewOriented ?: axisNow
                                // 출발점이 위/아래 어느 쪽인지에 따라 여백을 다시 잡음
                                val startTop = OverviewBearingPlanner.startIsAtTop(
                                    trackPoints.firstOrNull() ?: coursePoints.firstOrNull(),
                                    currentLocation ?: trackPoints.lastOrNull() ?: coursePoints.lastOrNull(),
                                    bearingNow
                                )
                                val pad = MapCameraPlanner.overviewPadding(view.width, view.height, overlayTopPx, overlayBottomPx, view.resources.displayMetrics.density, startTop)
                                val cam = OverviewBearingPlanner.fitRotated(pts, bearingNow, view.width, view.height, pad, view.resources.displayMetrics.density)
                                if (cam != null) view.post {
                                    runCatching {
                                        val pos = CameraPosition.Builder()
                                            .target(LatLng(cam.latitude, cam.longitude))
                                            .zoom(cam.zoom)
                                            .bearing(cam.bearing)
                                            .tilt(0.0)
                                            .padding(pad.left.toDouble(), pad.top.toDouble(), pad.right.toDouble(), pad.bottom.toDouble())
                                            .build()
                                        map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(pos), if (rotated) 1600 else 900)
                                    }
                                }
                            }
                        }
                        else -> { // FOLLOW (단말기 위쪽 = 북쪽) 또는 HEADING (단말기 위쪽 = 주행방향)
                            if (now - camState.lastFollowMs >= 900) {
                                camState.lastFollowMs = now
                                val pad = MapCameraPlanner.overlayPadding(view.width, view.height, overlayTopPx, overlayBottomPx)
                                val targetBearing = if (cameraMode == MapCameraMode.HEADING) {
                                    MapCameraPlanner.resolveBearing(bearingDeg, trackPoints, currentLocation)
                                } else {
                                    0.0
                                }
                                val zoom = MapCameraPlanner.followZoom(speedMps, distanceM)
                                // 현재 위치를 지도 영역 아래에서 1/3 지점에 두기: 거리(m) 계산 대신 카메라 여백(px)으로 중심을 내림.
                                // (예전 m 환산은 화면 밀도·512px 타일을 빼먹어 5배쯤 밀려 현재 위치가 아래 계기판 밑으로 숨었음)
                                val fp = MapCameraPlanner.followPadding(view.height, pad.top, pad.bottom, MapCameraPlanner.followTargetFraction(followingCourse))
                                val pos = CameraPosition.Builder()
                                    .target(LatLng(target.latitude, target.longitude))
                                    .zoom(zoom)
                                    .bearing(targetBearing)
                                    .padding(0.0, fp.top.toDouble(), 0.0, fp.bottom.toDouble())
                                    .build()
                                runCatching { map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(pos), 700) }
                            }
                        }
                    }
                    }

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
                        offCourse = offCourse,
                        selectedCourse = selectedCourseKm?.let { km -> MapRouteGeometry.pointAtKm(coursePoints, km) }
                    )
                    // 길담 레이어(코스·경로·점·사진 핀)를 스타일 위에 쌓기
                    val addGildamLayers: (Style) -> Unit = { style ->
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
                            addPhotoPinLayers(
                                style,
                                context = view.context,
                                pins = photoMarkers,
                                ringColor = 0xFF000000.toInt() or (0xFFFFFF and themeRoute.toArgb()),
                                mutedRingColor = 0xFF8A8A8A.toInt()
                            )
                            markerStyleKey[0] = markersKey(markers)
                            photoKey[0] = photoMarkersKey(photoMarkers)
                    }
                    val existingStyle = map.style
                    val action = MapStyleLoadPlanner.decide(
                        requestedUrl = loadedStyleKey[0],
                        wantedUrl = styleUrl,
                        styleLoaded = existingStyle?.isFullyLoaded == true,
                        hasGildamLayers = existingStyle?.getLayer(ROUTE_LAYER_ID) != null
                    )
                    if (action == StyleAction.LOAD) {
                        loadedStyleKey[0] = styleUrl
                        map.setStyle(buildKoreanStyle(view.context, styleUrl)) { style ->
                            applyKoreanLabelsAtRuntime(style)
                            if (style.getLayer(ROUTE_LAYER_ID) == null) runCatching { addGildamLayers(style) }
                        }
                    } else if (action == StyleAction.ADD_LAYERS && existingStyle != null) {
                        runCatching { addGildamLayers(existingStyle) }
                    } else if (action == StyleAction.UPDATE && existingStyle != null) {
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
                        // 사진 핀도 사진 목록이 바뀔 때만 다시 만듦 (아이콘 등록 포함)
                        val pKey = photoMarkersKey(photoMarkers)
                        if (photoKey[0] != pKey) {
                            photoKey[0] = pKey
                            if (existingStyle.getSourceAs<GeoJsonSource>(PHOTO_SOURCE_ID) == null) {
                                addPhotoPinLayers(
                                    existingStyle,
                                    context = view.context,
                                    pins = photoMarkers,
                                    ringColor = 0xFF000000.toInt() or (0xFFFFFF and themeRoute.toArgb()),
                                    mutedRingColor = 0xFF8A8A8A.toInt()
                                )
                            } else {
                                // 새로 생긴 사진의 썸네일 아이콘을 추가하고 핀 위치만 갱신
                                addPhotoPinImages(
                                    existingStyle, view.context, photoMarkers,
                                    ringColor = 0xFF000000.toInt() or (0xFFFFFF and themeRoute.toArgb()),
                                    mutedRingColor = 0xFF8A8A8A.toInt()
                                )
                                existingStyle.getSourceAs<GeoJsonSource>(PHOTO_SOURCE_ID)
                                    ?.setGeoJson(buildPhotoPinsGeoJson(photoMarkers))
                            }
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
    offCourse: Boolean = false,
    selectedCourse: LatLonPoint? = null
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
            val pad = if (followSpanM != null) MapCameraPlanner.overlayPadding(size.width.toInt(), size.height.toInt(), overlayTopPx, overlayBottomPx)
                else MapCameraPlanner.overviewPadding(
                    size.width.toInt(), size.height.toInt(), overlayTopPx, overlayBottomPx, density,
                    // 미리보기 점은 이미 돌려 둔 좌표라 북쪽 위 기준으로 판단
                    startAtTop = OverviewBearingPlanner.startIsAtTop(
                        trackPoints.firstOrNull() ?: coursePoints.firstOrNull(),
                        currentLocation ?: trackPoints.lastOrNull() ?: coursePoints.lastOrNull(), 0.0
                    )
                )
            val availW = size.width - pad.left - pad.right; val availH = size.height - pad.top - pad.bottom
            val center = currentLocation ?: trackPoints.lastOrNull()
            val sc: Float; val ox: Float; val oy: Float; val minLon: Double; val maxLat: Double; val kx: Double
            if (followSpanM != null && center != null) {
                // 따라가기: 현재 위치 중심, 고정 폭
                kx = kotlin.math.cos(Math.toRadians(center.latitude))
                val spanDeg = followSpanM / 111_320.0
                sc = (availW / spanDeg).toFloat()
                minLon = center.longitude; maxLat = center.latitude
                ox = pad.left + availW / 2
                oy = pad.top + availH * MapCameraPlanner.followTargetFraction(coursePoints.isNotEmpty()).toFloat()
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
            // 고도 그래프에서 고른 코스 지점
            selectedCourse?.let { sel ->
                val o = pt(sel)
                drawCircle(androidx.compose.ui.graphics.Color.White, 12f, o)
                drawCircle(routeColor, 12f, o, style = Stroke(6f))
            }
            // 출발 점(초록 원): 내 기록의 첫 점 (코스만 있는 지도는 코스 시작·끝 점을 따로 그림)
            trackPoints.firstOrNull()?.let { st ->
                val o = pt(st)
                // 실제 지도와 같은 크기(dp): 흰 테두리 포함 반지름 15dp, 안쪽 12dp
                drawCircle(androidx.compose.ui.graphics.Color.White, MapCameraPlanner.START_MARKER_RADIUS_DP * density, o)
                drawCircle(androidx.compose.ui.graphics.Color(0xFF2E7D4F), (MapCameraPlanner.START_MARKER_RADIUS_DP - 3) * density, o)
                drawIntoCanvas { c ->
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                        textSize = 14f * density
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    c.nativeCanvas.drawText("S", o.x, o.y + 5f * density, paint)
                }
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

/** 지도에 동시에 그리는 사진 핀 최대 수 */
private const val MAX_PHOTO_PINS = 40

/** 사진 핀 목록이 바뀌었는지 비교하는 키 */
private fun photoMarkersKey(pins: List<MapPhotoMarker>): String =
    pins.joinToString("|") {
        "%s,%.6f,%.6f,%s".format(java.util.Locale.ROOT, it.id, it.latitude, it.longitude, it.timeOnly)
    }

private fun buildPhotoPinsGeoJson(pins: List<MapPhotoMarker>): String {
    if (pins.isEmpty()) return EMPTY_FEATURE_COLLECTION
    val features = pins.joinToString(",") { p ->
        val img = "gildam-photo-pin-${p.id}"
        """{"type":"Feature","properties":{"photoId":"${p.id}","pin":"$img"},"geometry":{"type":"Point","coordinates":[${p.longitude},${p.latitude}]}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

/**
 * 사진 핀 레이어. 40dp 원형 썸네일 이미지를 Style.addImage 로 등록해 SymbolLayer 로 그립니다.
 * 사진은 기기에서만 읽어 메모리 캐시(최대 40장)로 쓰고, 파일 복사·전송은 없습니다.
 */
private fun addPhotoPinLayers(
    style: Style,
    context: android.content.Context,
    pins: List<MapPhotoMarker>,
    ringColor: Int,
    mutedRingColor: Int
) {
    val density = context.resources.displayMetrics.density
    val sizePx = (40 * density).toInt()
    addPhotoPinImages(style, context, pins, ringColor, mutedRingColor)
    style.addSource(GeoJsonSource(PHOTO_SOURCE_ID, buildPhotoPinsGeoJson(pins)))
    style.addLayer(
        SymbolLayer(PHOTO_LAYER_ID, PHOTO_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(Expression.get("pin")),
            PropertyFactory.iconSize(1f),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
        )
    )
}

/** 핀마다 자기 사진 썸네일을 스타일에 등록 (없는 것만, 최대 40장, 메모리 캐시) */
private fun addPhotoPinImages(
    style: Style,
    context: android.content.Context,
    pins: List<MapPhotoMarker>,
    ringColor: Int,
    mutedRingColor: Int
) {
    val density = context.resources.displayMetrics.density
    val sizePx = (40 * density).toInt()
    pins.take(MAX_PHOTO_PINS).forEach { p ->
        val name = "gildam-photo-pin-${p.id}"
        if (style.getImage(name) != null) return@forEach
        runCatching {
            style.addImage(name, PhotoPinCache.load(context, p.uri, sizePx, if (p.timeOnly) mutedRingColor else ringColor))
        }
    }
}

/** 탭 좌표에서 가장 가까운 사진 핀 (핀 크기만큼 여유) */
private fun nearestPhotoMarker(
    pins: List<MapPhotoMarker>,
    map: org.maplibre.android.maps.MapLibreMap,
    latLng: LatLng
): String? {
    if (pins.isEmpty()) return null
    val tolerancePx = 26.0
    val tap = map.projection.toScreenLocation(latLng)
    var bestId: String? = null
    var best = tolerancePx
    pins.forEach { p ->
        val s = map.projection.toScreenLocation(LatLng(p.latitude, p.longitude))
        val d = kotlin.math.hypot((s.x - tap.x).toDouble(), (s.y - tap.y).toDouble())
        if (d <= best) { best = d; bestId = p.id }
    }
    return bestId
}


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
    return "${p(m.start)}|${p(m.current)}|${m.showArrow}|${(m.bearing * 2).toInt()}|${p(m.onCourseProgress)}|${m.offCourse}|${p(m.selectedCourse)}"
}

/** 출발 점 · 코스 진행 점 · 현재 점 · 방향 화살표 레이어를 스타일 위에 올립니다 (선보다 위) */
private fun addMarkerLayers(
    style: Style,
    startColor: Int,
    routeColor: Int,
    markers: MapMarkers
) {
    val white = Color.WHITE
    // 고도 그래프와 연결된 선택 지점: 흰 바탕 + 굵은 테두리 원
    style.addSource(GeoJsonSource(SELECTED_SOURCE_ID, buildPointGeoJson(markers.selectedCourse)))
    style.addLayer(
        CircleLayer(SELECTED_LAYER_ID, SELECTED_SOURCE_ID).withProperties(
            PropertyFactory.circleColor(white),
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleStrokeColor(routeColor),
            PropertyFactory.circleStrokeWidth(3.5f)
        )
    )
    style.addSource(GeoJsonSource(START_SOURCE_ID, buildPointGeoJson(markers.start)))
    style.addLayer(
        CircleLayer(START_LAYER_ID, START_SOURCE_ID).withProperties(
            PropertyFactory.circleColor(startColor),
            PropertyFactory.circleRadius(12f),
            PropertyFactory.circleStrokeColor(white),
            PropertyFactory.circleStrokeWidth(3f)
        )
    )
    // 출발점 'S' 글자 (스타일 글리프 사용, 다른 라벨에 가려지지 않게)
    style.addLayer(
        SymbolLayer(START_LABEL_LAYER_ID, START_SOURCE_ID).withProperties(
            PropertyFactory.textField("S"),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(14f),
            PropertyFactory.textColor(white),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true)
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
    style.getSourceAs<GeoJsonSource>(SELECTED_SOURCE_ID)?.setGeoJson(buildPointGeoJson(markers.selectedCourse))
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
private const val START_LABEL_LAYER_ID = "gildam-start-label-layer"
private const val CURRENT_SOURCE_ID = "gildam-current-source"
private const val CURRENT_LAYER_ID = "gildam-current-layer"
private const val ARROW_SOURCE_ID = "gildam-arrow-source"
private const val ARROW_LAYER_ID = "gildam-arrow-layer"
private const val PHOTO_SOURCE_ID = "gildam-photo-source"
private const val PHOTO_LAYER_ID = "gildam-photo-layer"
private const val PROGRESS_SOURCE_ID = "gildam-progress-source"
private const val PROGRESS_LAYER_ID = "gildam-progress-layer"
private const val SELECTED_SOURCE_ID = "gildam-selected-source"
private const val SELECTED_LAYER_ID = "gildam-selected-layer"

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

/** 미리보기용: 점들을 [bearing] 만큼 돌린 가짜 위경도로 (위쪽 = 그 방위) */
private fun previewRotator(all: List<LatLonPoint>, bearing: Double): (LatLonPoint) -> LatLonPoint {
    if (bearing == 0.0 || all.isEmpty()) return { it }
    val lat0 = all.sumOf { it.latitude } / all.size
    val lon0 = all.sumOf { it.longitude } / all.size
    val k = kotlin.math.cos(Math.toRadians(lat0))
    return { p ->
        val (r, u) = OverviewBearingPlanner.rotate((p.longitude - lon0) * k * 111_320.0, (p.latitude - lat0) * 111_320.0, bearing)
        p.copy(latitude = lat0 + u / 111_320.0, longitude = lon0 + r / (k * 111_320.0))
    }
}
