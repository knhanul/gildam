package kr.co.nuni.gildam.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kr.co.nuni.gildam.ui.navigation.ActivityDetailRoute
import kr.co.nuni.gildam.ui.navigation.ActivityListRoute
import kr.co.nuni.gildam.ui.navigation.LiveRecordingRoute
import kr.co.nuni.gildam.ui.navigation.SafeShareExportRoute
import kr.co.nuni.gildam.ui.navigation.SettingsRoute
import kr.co.nuni.gildam.ui.screens.ActivityDetailScreen
import kr.co.nuni.gildam.ui.screens.ActivityListScreen
import kr.co.nuni.gildam.ui.screens.LiveRecordingScreen
import kr.co.nuni.gildam.ui.screens.SafeShareExportScreen
import kr.co.nuni.gildam.ui.screens.SettingsScreen
import kr.co.nuni.gildam.ui.theme.GildamTheme

@Composable
fun GildamAppRoot(
    viewModel: GildamViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val liveState by viewModel.liveRecordingState.collectAsState()
    val sessionState by viewModel.authSessionState.collectAsState()
    val activities by viewModel.filteredActivities.collectAsState()
    val selectedTransportFilter by viewModel.selectedTransportFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTransportForNew by viewModel.selectedTransportForNewRecording.collectAsState()
    val thumbnailCacheBytes by viewModel.thumbnailCacheSizeBytes.collectAsState()

    // 1단계 포그라운드 위치 + 알림 권한 요청 런처 (Android 11+ 단계별 권한 분리 정책 준수)
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted) {
            viewModel.startRecording()
        }
    }

    GildamTheme {
        NavHost(
            navController = navController,
            startDestination = LiveRecordingRoute
        ) {
            composable<LiveRecordingRoute> {
                LiveRecordingScreen(
                    liveState = liveState,
                    selectedTransport = selectedTransportForNew,
                    onSelectTransport = viewModel::selectTransportForNewRecording,
                    onStartRecording = {
                        val permissions = buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }.toTypedArray()
                        foregroundPermissionLauncher.launch(permissions)
                    },
                    onPauseRecording = viewModel::pauseRecording,
                    onResumeRecording = viewModel::resumeRecording,
                    onStopRecording = viewModel::stopRecording,
                    onAddWaypoint = viewModel::addWaypointMemo,
                    onNavigateToList = { navController.navigate(ActivityListRoute) },
                    onNavigateToSettings = { navController.navigate(SettingsRoute) }
                )
            }

            composable<ActivityListRoute> {
                ActivityListScreen(
                    activities = activities,
                    selectedTransportFilter = selectedTransportFilter,
                    searchQuery = searchQuery,
                    onSelectTransportFilter = viewModel::setTransportFilter,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onSelectActivity = { id -> navController.navigate(ActivityDetailRoute(id)) },
                    onNavigateBackToRecorder = { navController.popBackStack() },
                    onNavigateToSettings = { navController.navigate(SettingsRoute) }
                )
            }

            composable<ActivityDetailRoute> { backStackEntry ->
                val route: ActivityDetailRoute = backStackEntry.toRoute()
                val detailBundle by viewModel.observeActivityDetail(route.activityId)
                    .collectAsState(initial = null)
                val trackPoints by viewModel.observeTrackPoints(route.activityId)
                    .collectAsState(initial = emptyList())

                ActivityDetailScreen(
                    bundle = detailBundle,
                    trackPoints = trackPoints,
                    onVerifyPhotos = viewModel::verifyPhotosOnDetailOpen,
                    onRelinkMissingPhoto = viewModel::relinkMissingPhoto,
                    onRemoveMissingPhotoKeepMemo = viewModel::removeMissingPhotoKeepMemo,
                    onNavigateToSafeExport = { id -> navController.navigate(SafeShareExportRoute(id)) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<SafeShareExportRoute> { backStackEntry ->
                val route: SafeShareExportRoute = backStackEntry.toRoute()
                val trackPoints by viewModel.observeTrackPoints(route.activityId)
                    .collectAsState(initial = emptyList())

                SafeShareExportScreen(
                    trackPoints = trackPoints,
                    computePreview = viewModel::computeMaskedPreview,
                    onExportGpx = { options ->
                        viewModel.generateSafeGpxExport(route.activityId, options)
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<SettingsRoute> {
                SettingsScreen(
                    sessionState = sessionState,
                    thumbnailCacheBytes = thumbnailCacheBytes,
                    isBatteryOptimizationIgnored = viewModel.isIgnoringBatteryOptimizations(),
                    onLaunchNuniCustomTabLogin = { viewModel.launchNuniLogin(context) },
                    onDemoSimulateLogin = viewModel::triggerDemoLoginAndSync,
                    onManualSync = viewModel::triggerManualSync,
                    onLogout = viewModel::logout,
                    onClearThumbnailCache = viewModel::clearThumbnailCache,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
