package kr.co.nuni.gildam.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class TransportType(val labelKo: String, val maxRealisticSpeedMps: Float) {
    WALK(labelKo = "도보", maxRealisticSpeedMps = 7.0f),
    BIKE(labelKo = "자전거", maxRealisticSpeedMps = 22.0f),
    CAR(labelKo = "자동차", maxRealisticSpeedMps = 55.0f)
}

@Serializable
enum class ActivityStatus {
    RECORDING,
    PAUSED,
    COMPLETED
}

@Serializable
enum class SyncState(val labelKo: String) {
    LOCAL_ONLY("기기 전용(비로그인)"),
    PENDING_UPLOAD("동기화 대기"),
    SYNCING("동기화 중"),
    SYNCED("NUNI ID 동기화됨"),
    PENDING_DELETE("삭제 동기화 대기"),
    SYNC_ERROR("동기화 재시도 대기")
}

@Serializable
enum class Visibility(val labelKo: String) {
    PRIVATE("비공개 (나만 보기)"),
    PUBLIC_LINK("링크 공유"),
    PUBLIC("공개")
}

@Serializable
enum class PhotoBackupState {
    /** 확정 정책 기본값: 사진 원본과 썸네일은 기기에만 보관하고 클라우드에 업로드하지 않음 */
    LOCAL_ONLY,
    /** 향후 선택적 업로드 확장용 예약 상태 */
    UPLOADED,
    /** 향후 종단간 암호화 백업 확장용 예약 상태 */
    ENCRYPTED_BACKUP
}

@Serializable
enum class UriStatus {
    VALID,
    MISSING_IN_GALLERY,
    PERMISSION_REVOKED
}
