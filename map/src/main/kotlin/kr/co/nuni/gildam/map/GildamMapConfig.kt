package kr.co.nuni.gildam.map

/**
 * MapLibre 벡터 타일 스타일 설정.
 * - 설계서 8장 권장안대로 스타일 URL을 한 곳에서 추상화하여,
 *   초기 상용 타일(MapTiler/Stadia) 또는 OVH 자체 PMTiles 호스팅으로 앱 배포 없이 전환할 수 있습니다.
 */
object GildamMapConfig {
    // 기본 데모 벡터 스타일 (실제 배포 시 OVH PMTiles style.json 또는 MapTiler 키 URL로 교체)
    const val DEFAULT_LIGHT_STYLE_URL = "https://demotiles.maplibre.org/style.json"
    const val DEFAULT_DARK_STYLE_URL = "https://demotiles.maplibre.org/style.json"

    const val SEOUL_DEFAULT_LAT = 37.5796
    const val SEOUL_DEFAULT_LON = 126.9770
}
