package kr.co.nuni.gildam.data.model

/**
 * 기록 화면 계기판에서 고를 수 있는 값 5가지.
 *
 * 기본 배치는 "총 5개 = 지도 위 1개 + 지도 아래 4개" 입니다.
 * 지도 위에 띄우는 값은 활동 구분마다 다르고([defaultFor]), 설정에서 바꿀 수 있습니다.
 * 나머지 4개는 지도 아래 카드에 큰 값 2개(히어로) + 작은 값 2개(보조)로 보여 줍니다.
 */
enum class GaugeMetric(val labelKo: String, val descKo: String) {
    DISTANCE("누적 거리", "출발부터 지금까지 간 거리"),
    TOTAL_TIME("총 시간", "출발하고 흐른 시간(쉬는 시간 포함)"),
    CURRENT_SPEED("현재 속도", "지금 이 순간의 속도"),
    MOVING_AVG_SPEED("이동 평균 속도", "움직인 시간 동안의 평균 속도"),
    CLOCK("현재 시간", "지금 시각");

    /** 단위가 없는 값(총 시간·현재 시간)은 null */
    val unit: String?
        get() = when (this) {
            DISTANCE -> "km"
            CURRENT_SPEED, MOVING_AVG_SPEED -> "km/h"
            TOTAL_TIME, CLOCK -> null
        }

    /** 계기판 라벨: 단위가 있으면 "누적 거리 (km)" 처럼 붙여서 표시 */
    val labelWithUnit: String
        get() = unit?.let { "$labelKo ($it)" } ?: labelKo

    companion object {
        /** 지도 아래 카드에 놓는 순서. 앞의 2개가 히어로(큰 값), 뒤의 2개가 보조 카드. */
        val displayOrder: List<GaugeMetric> = listOf(DISTANCE, TOTAL_TIME, CURRENT_SPEED, MOVING_AVG_SPEED, CLOCK)

        /** 설정에서 고를 수 있는 값 (계기판에서 실제로 계산되는 값 전부) */
        val selectable: List<GaugeMetric> get() = displayOrder

        /** 활동 구분별 기본값 */
        fun defaultFor(type: TransportType): GaugeMetric = when (type) {
            TransportType.BIKE -> CURRENT_SPEED
            TransportType.CAR -> MOVING_AVG_SPEED
            TransportType.RUN -> DISTANCE
            TransportType.WALK -> TOTAL_TIME
            TransportType.HIKE -> DISTANCE
        }

        /** 지도 위 1개를 뺀 나머지 4개 (지도 아래 카드, 항상 4개·중복 없음) */
        fun bottomFor(overlay: GaugeMetric): List<GaugeMetric> = displayOrder.filter { it != overlay }

        /** 지도 아래 큰 값 2개 */
        fun heroesFor(overlay: GaugeMetric): List<GaugeMetric> = bottomFor(overlay).take(2)

        /** 지도 아래 작은 값 2개 */
        fun subsFor(overlay: GaugeMetric): List<GaugeMetric> = bottomFor(overlay).drop(2)
    }
}
