package kr.co.nuni.gildam.data.weather

/**
 * 바람 방향·세기. labelKo 는 테스트·내부용이고, 화면 글자는 ui/l10n/Labels.kt 를 씁니다.
 *
 * 방향은 기상 관례(바람이 불어오는 쪽, 0=북·시계 방향)를 8방위로 나눕니다.
 * 세기는 m/s 기준: 약함 <4, 보통 4–9, 강함 9–14, 매우 강함 ≥14.
 */
enum class WindCardinal(val labelKo: String) {
    N("북풍"),
    NE("북동풍"),
    E("동풍"),
    SE("남동풍"),
    S("남풍"),
    SW("남서풍"),
    W("서풍"),
    NW("북서풍")
}

enum class WindStrength(val labelKo: String) {
    LIGHT("약함"),
    MODERATE("보통"),
    STRONG("강함"),
    VERY_STRONG("매우 강함")
}

fun windCardinal(dirDeg: Double): WindCardinal {
    val normalized = ((dirDeg % 360.0) + 360.0) % 360.0
    val index = ((normalized + 22.5) / 45.0).toInt() % 8
    return WindCardinal.entries[index]
}

fun windStrength(speedMs: Double): WindStrength = when {
    speedMs < 4.0 -> WindStrength.LIGHT
    speedMs < 9.0 -> WindStrength.MODERATE
    speedMs < 14.0 -> WindStrength.STRONG
    else -> WindStrength.VERY_STRONG
}

/** 바람이 불어가는 쪽(Compose Navigation 아이콘 회전각, 위가 0·시계 방향). */
fun windTowardRotationDeg(dirDeg: Double): Float = (((dirDeg + 180.0) % 360.0 + 360.0) % 360.0).toFloat()
