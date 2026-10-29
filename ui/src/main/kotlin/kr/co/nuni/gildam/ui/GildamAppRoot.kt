package kr.co.nuni.gildam.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import kr.co.nuni.gildam.ui.navigation.GildamBottomBar
import kr.co.nuni.gildam.ui.navigation.GildamTab
import kr.co.nuni.gildam.ui.navigation.CoursesRoute
import kr.co.nuni.gildam.ui.navigation.CourseDetailRoute
import kr.co.nuni.gildam.ui.screens.CourseListScreen
import kr.co.nuni.gildam.ui.screens.CourseDetailScreen
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.co.nuni.gildam.ui.navigation.GpxCompareRoute
import kr.co.nuni.gildam.ui.screens.GpxCompareScreen
import kr.co.nuni.gildam.ui.navigation.ActivityDetailRoute
import kr.co.nuni.gildam.ui.navigation.ActivityListRoute
import kr.co.nuni.gildam.ui.navigation.LiveRecordingRoute
import kr.co.nuni.gildam.ui.navigation.SafeShareExportRoute
import kr.co.nuni.gildam.ui.navigation.SettingsRoute
import kr.co.nuni.gildam.ui.navigation.AccountInfoRoute
import kr.co.nuni.gildam.ui.screens.ActivityDetailScreen
import kr.co.nuni.gildam.ui.screens.ActivityListScreen
import kr.co.nuni.gildam.ui.screens.LiveRecordingScreen
import kr.co.nuni.gildam.ui.screens.SafeShareExportScreen
import kr.co.nuni.gildam.ui.screens.SettingsScreen
import kr.co.nuni.gildam.ui.screens.AccountInfoScreen
import kr.co.nuni.gildam.ui.theme.GildamTheme

@Composable
fun GildamAppRoot(
    viewModel: GildamViewModel = hiltViewModel(),
    courseViewModel: CourseViewModel = hiltViewModel(),
    appearanceViewModel: AppearanceViewModel = hiltViewModel()
) {
    val palette by appearanceViewModel.palette.collectAsState()
    val darkMode by appearanceViewModel.darkMode.collectAsState()
    val recordMapMode by appearanceViewModel.recordMapMode.collectAsState()
    val gaugeSelections by appearanceViewModel.gaugeSelections.collectAsState()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (darkMode) {
        kr.co.nuni.gildam.ui.theme.DarkModePref.SYSTEM -> systemDark
        kr.co.nuni.gildam.ui.theme.DarkModePref.LIGHT -> false
        kr.co.nuni.gildam.ui.theme.DarkModePref.DARK -> true
    }
    // 앱에서 밝기를 직접 고른 경우 상태바·내비게이션바 아이콘 색도 맞춤
    val hostView = androidx.compose.ui.platform.LocalView.current
    if (!hostView.isInEditMode) androidx.compose.runtime.SideEffect {
        (hostView.context as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, hostView).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }
    val navController = rememberNavController()
    val context = LocalContext.current

    val liveState by viewModel.liveRecordingState.collectAsState()
    val sessionState by viewModel.authSessionState.collectAsState()
    val accountDisplay by viewModel.accountDisplay.collectAsState()
    val activities by viewModel.filteredActivities.collectAsState()
    val selectedTransportFilter by viewModel.selectedTransportFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTransportForNew by viewModel.selectedTransportForNewRecording.collectAsState()
    val thumbnailCacheBytes by viewModel.thumbnailCacheSizeBytes.collectAsState()
    LaunchedEffect(sessionState.isLoggedIn) {
        viewModel.refreshServerAccount()
    }

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

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTab = when {
        destination == null -> GildamTab.RECORD
        destination.hasRoute<LiveRecordingRoute>() -> GildamTab.RECORD
        destination.hasRoute<ActivityListRoute>() -> GildamTab.LIST
        destination.hasRoute<CoursesRoute>() -> GildamTab.COURSES
        destination.hasRoute<GpxCompareRoute>() -> GildamTab.COURSES
        destination.hasRoute<SettingsRoute>() -> GildamTab.SETTINGS
        else -> null
    }
    // 기록 중(일시정지 포함)·상세·내보내기·온보딩 등 탭 이외 화면에서는 숨김
    val followUi by courseViewModel.follow.collectAsState()
    val showBottomBar = currentTab != null && !liveState.isRecording

    val selectTab: (GildamTab) -> Unit = { tab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    GildamTheme(darkTheme = isDark, palette = palette) {
      Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) GildamBottomBar(currentTab = currentTab, onSelectTab = selectTab)
        }
      ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = LiveRecordingRoute,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
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
                    onNavigateToList = { selectTab(GildamTab.LIST) },
                    onNavigateToSettings = { selectTab(GildamTab.SETTINGS) },
                    showTopNavIcons = false,
                    follow = followUi,
                    onStopFollow = courseViewModel::stopFollow,
                    defaultCameraMode = recordMapMode,
                    overlayMetric = gaugeSelections[if (liveState.isRecording) liveState.transportType else selectedTransportForNew]
                        ?: kr.co.nuni.gildam.data.model.GaugeMetric.defaultFor(selectedTransportForNew)
                )
            }

            composable<GpxCompareRoute> {
                val compareViewModel: GpxCompareViewModel = viewModel()
                val entries by compareViewModel.entries.collectAsState()
                val loading by compareViewModel.loading.collectAsState()
                // SAF: 읽기 전용, 영구 권한 요청 없음
                val pickGpx = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    compareViewModel.addFiles(context, uris)
                }
                GpxCompareScreen(
                    entries = entries,
                    isLoading = loading,
                    maxFiles = GpxCompareViewModel.MAX_FILES,
                    onPickFiles = { pickGpx.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream", "*/*")) },
                    onRemove = compareViewModel::remove,
                    onClear = compareViewModel::clear,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<CoursesRoute> {
                val courseList by courseViewModel.courseList.collectAsState()
                val importing by courseViewModel.importing.collectAsState()
                val pickCourses = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    courseViewModel.import(uris)
                }
                CourseListScreen(
                    courses = courseList,
                    isImporting = importing,
                    onImport = { pickCourses.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream", "*/*")) },
                    onOpenCourse = { id -> navController.navigate(CourseDetailRoute(id)) },
                    onOpenCompare = { navController.navigate(GpxCompareRoute) }
                )
            }

            composable<CourseDetailRoute> { entry ->
                val route: CourseDetailRoute = entry.toRoute()
                val detail by remember(route.courseId) { courseViewModel.observeDetail(route.courseId) }.collectAsState(initial = null)
                CourseDetailScreen(
                    detail = detail,
                    onNavigateBack = { navController.popBackStack() },
                    onFollow = { id -> courseViewModel.startFollow(id); selectTab(GildamTab.RECORD) },
                    onDelete = { id -> courseViewModel.delete(id); navController.popBackStack() }
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
                    onNavigateBackToRecorder = { selectTab(GildamTab.RECORD) },
                    onNavigateToSettings = { selectTab(GildamTab.SETTINGS) },
                    isTopLevelTab = true,
                    isLoggedIn = sessionState.isLoggedIn,
                    onEditActivity = viewModel::editActivity,
                    onDeleteActivity = viewModel::deleteActivity,
                    onUploadActivity = viewModel::uploadActivityToCloud
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
                    onNavigateBack = { navController.popBackStack() },
                    sameRouteEta = remember(trackPoints) { courseViewModel.sameRouteEta(trackPoints) },
                    isLoggedIn = sessionState.isLoggedIn,
                    onEditActivity = viewModel::editActivity,
                    onDeleteActivity = viewModel::deleteActivity,
                    onUploadActivity = viewModel::uploadActivityToCloud
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
                val serverTrackCount by viewModel.serverTrackCount.collectAsState()
                SettingsScreen(
                    serverTrackCount = serverTrackCount,
                    accountDisplayName = accountDisplay.displayName,
                    accountNickname = accountDisplay.nickname,
                    sessionState = sessionState,
                    thumbnailCacheBytes = thumbnailCacheBytes,
                    isBatteryOptimizationIgnored = viewModel.isIgnoringBatteryOptimizations(),
                    onKakaoLogin = { viewModel.launchKakaoLogin(context) },
                    onSwitchAccount = { viewModel.launchKakaoLogin(context, switchAccount = true) },
                    onDemoSimulateLogin = if (BuildConfig.DEBUG) viewModel::triggerDemoLoginAndSync else null,
                    onManualSync = viewModel::triggerManualSync,
                    onLogout = { viewModel.logout(context) },
                    onClearThumbnailCache = viewModel::clearThumbnailCache,
                    onNavigateBack = { navController.popBackStack() },
                    isTopLevelTab = true,
                    palette = palette,
                    darkMode = darkMode,
                    onSelectPalette = appearanceViewModel::setPalette,
                    onSelectDarkMode = appearanceViewModel::setDarkMode,
                    recordMapMode = recordMapMode,
                    onSelectRecordMapMode = appearanceViewModel::setRecordMapMode,
                    gaugeSelections = gaugeSelections,
                    onSelectGauge = appearanceViewModel::setGaugeSelection,
                    onResetGauge = appearanceViewModel::resetGaugeSelections,
                    onOpenAccountInfo = { navController.navigate(AccountInfoRoute) }
                )
            }

            composable<AccountInfoRoute> {
                AccountInfoScreen(
                    isLoggedIn = sessionState.isLoggedIn,
                    displayName = accountDisplay.displayName,
                    nickname = accountDisplay.nickname,
                    onOpenAccountPage = { viewModel.openNuniAccountPage(context) },
                    onLogout = { viewModel.logout(context) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
      }
    }
}
