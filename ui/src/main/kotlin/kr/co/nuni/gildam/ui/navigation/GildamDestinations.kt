package kr.co.nuni.gildam.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-Safe Navigation Routes (https://github.com/android/skills navigation best practices)
 */
@Serializable
object OnboardingRoute

@Serializable
object LiveRecordingRoute

@Serializable
object ActivityListRoute

@Serializable
data class ActivityDetailRoute(val activityId: String)

@Serializable
data class SafeShareExportRoute(val activityId: String)

@Serializable
object SettingsRoute

/** 순수 GPX 파일끼리 길의 험함 비교 (저장·동기화 없음) */
@Serializable
object GpxCompareRoute
