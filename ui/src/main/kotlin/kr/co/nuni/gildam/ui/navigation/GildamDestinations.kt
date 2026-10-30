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

/** 설정 > 기록 설정 > 화면 꺼짐 중 기록 상세 */
@Serializable
object RecordingStatusRoute

/** 설정 > 기록 설정 > 지도 강조 지표 편집 */
@Serializable
object GaugeHighlightRoute

/** 설정 > 기록 설정 > 기록 중 화면·자동 종료 */
@Serializable
object RecordingAutoStopRoute

/** 설정 > 데이터 보관 > 사진 보관 안내 */
@Serializable
object PhotoStorageRoute

/** 순수 GPX 파일끼리 길의 험함 비교 (저장·동기화 없음) */
@Serializable
object GpxCompareRoute

@Serializable
object CoursesRoute

@Serializable
data class CourseDetailRoute(val courseId: String)
