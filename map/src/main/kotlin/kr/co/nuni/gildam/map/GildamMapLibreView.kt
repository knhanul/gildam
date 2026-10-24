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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kr.co.nuni.gildam.recording.LatLonPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
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
    modifier: Modifier = Modifier
) {
    val isPreview = LocalInspectionMode.current
    if (isPreview) {
        PreviewFallbackMapCanvas(
            trackPoints = trackPoints,
            isDarkTheme = isDarkTheme,
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    remember {
        MapLibre.getInstance(context)
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }

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
                    val styleUrl = if (isDarkTheme) {
                        GildamMapConfig.DEFAULT_DARK_STYLE_URL
                    } else {
                        GildamMapConfig.DEFAULT_LIGHT_STYLE_URL
                    }

                    val targetLat = currentLocation?.latitude
                        ?: trackPoints.lastOrNull()?.latitude
                        ?: GildamMapConfig.SEOUL_DEFAULT_LAT
                    val targetLon = currentLocation?.longitude
                        ?: trackPoints.lastOrNull()?.longitude
                        ?: GildamMapConfig.SEOUL_DEFAULT_LON

                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(targetLat, targetLon))
                        .zoom(15.2)
                        .build()

                    val geoJson = buildLineStringGeoJson(trackPoints)
                    val existingStyle = map.style
                    if (existingStyle == null || existingStyle.uri != styleUrl) {
                        map.setStyle(styleUrl) { style ->
                            val source = GeoJsonSource(ROUTE_SOURCE_ID, geoJson)
                            style.addSource(source)
                            val lineColor = if (isDarkTheme) "#FF8A65" else "#D95D39"
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
                text = "© OpenStreetMap · MapLibre",
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
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDarkTheme) androidx.compose.ui.graphics.Color(0xFF141816)
    else androidx.compose.ui.graphics.Color(0xFFEAE4D8)
    val routeColor = if (isDarkTheme) androidx.compose.ui.graphics.Color(0xFFFF8A65)
    else androidx.compose.ui.graphics.Color(0xFFD95D39)

    Box(modifier = modifier.background(bgColor)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(size.width * 0.18f, size.height * 0.75f)
                cubicTo(
                    size.width * 0.35f, size.height * 0.55f,
                    size.width * 0.55f, size.height * 0.62f,
                    size.width * 0.82f, size.height * 0.25f
                )
            }
            drawPath(
                path = path,
                color = routeColor,
                style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawCircle(
                color = routeColor,
                radius = 16f,
                center = Offset(size.width * 0.82f, size.height * 0.25f)
            )
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

private const val ROUTE_SOURCE_ID = "gildam-route-source"
private const val ROUTE_LAYER_ID = "gildam-route-layer"
