package kr.co.nuni.gildam.data.model

/** 지도 아래 계기판 칸 종류 */
enum class GaugeCell(val metric: GaugeMetric) {
    DISTANCE(GaugeMetric.DISTANCE),
    TOTAL_TIME(GaugeMetric.TOTAL_TIME),
    CURRENT_SPEED(GaugeMetric.CURRENT_SPEED),
    MOVING_AVG_SPEED(GaugeMetric.MOVING_AVG_SPEED),
    CLOCK(GaugeMetric.CLOCK),
    /** 오르막·내리막 (기록 중에만) */
    ELEVATION(GaugeMetric.ELEVATION),
    /** 실시간 날씨 (기록 중에만) */
    WEATHER(GaugeMetric.WEATHER);

    /** 기록 중에만 보이는 칸 */
    val recordingOnly: Boolean get() = metric.recordingOnly
}

/**
 * 설정 > 계기판 구성: 칸 순서와 켜고 끈 상태.
 * 계기판은 켠 칸을 이 순서대로 2열로 놓고, 홀수면 마지막 칸을 넓게 채웁니다.
 */
data class GaugeLayout(val entries: List<Entry>) {
    data class Entry(val cell: GaugeCell, val enabled: Boolean)

    val enabledCells: List<GaugeCell> get() = entries.filter { it.enabled }.map { it.cell }

    /**
     * 지도 아래에 실제로 놓을 칸들. 지도 위에 크게 띄운 값([overlay])은 빼고,
     * 기록 중이 아니면 오르막·내리막·날씨 칸도 뺍니다.
     */
    fun gridCells(overlay: GaugeMetric, recording: Boolean): List<GaugeCell> =
        enabledCells.filter { it.metric != overlay && (recording || !it.recordingOnly) }

    fun toggled(cell: GaugeCell, enabled: Boolean): GaugeLayout {
        // 마지막 하나는 끌 수 없음 (빈 계기판 방지)
        if (!enabled && enabledCells == listOf(cell)) return this
        return copy(entries = entries.map { if (it.cell == cell) it.copy(enabled = enabled) else it })
    }

    fun moved(from: Int, to: Int): GaugeLayout {
        if (from !in entries.indices || to !in entries.indices || from == to) return this
        val list = entries.toMutableList()
        list.add(to, list.removeAt(from))
        return copy(entries = list)
    }

    fun encode(): String = entries.joinToString(",") { "${it.cell.name}:${if (it.enabled) 1 else 0}" }

    companion object {
        /** 기본: 지금까지와 같은 순서·전부 켬 (기록 중 지도 위 1개를 빼면 2×3 = 6칸) */
        val DEFAULT = GaugeLayout(GaugeCell.entries.map { Entry(it, true) })

        /** 저장 글자 → 구성. 모르는 값·중복은 버리고 빠진 칸은 끝에 켠 채로 붙임 */
        fun decode(raw: String?): GaugeLayout {
            if (raw.isNullOrBlank()) return DEFAULT
            val seen = LinkedHashMap<GaugeCell, Boolean>()
            raw.split(',').forEach { part ->
                val name = part.substringBefore(':').trim()
                val cell = GaugeCell.entries.firstOrNull { it.name == name } ?: return@forEach
                if (cell !in seen) seen[cell] = part.substringAfter(':', "1").trim() != "0"
            }
            GaugeCell.entries.forEach { if (it !in seen) seen[it] = true }
            val layout = GaugeLayout(seen.map { Entry(it.key, it.value) })
            return if (layout.enabledCells.isEmpty()) DEFAULT else layout
        }
    }
}
