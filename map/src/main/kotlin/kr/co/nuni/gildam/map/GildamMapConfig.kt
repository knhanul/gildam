package kr.co.nuni.gildam.map

/**
 * 지도 스타일 설정.
 * 기본값은 앱에 내장한 OpenFreeMap 스타일(Liberty / Dark)이며 타일·글꼴·아이콘은 tiles.openfreemap.org 에서 받습니다.
 * OVH PMTiles 로 옮길 때는 gradle property `mapStyleLightUrl` / `mapStyleDarkUrl` 에 style.json 주소만 넣으면 됩니다.
 */
object GildamMapConfig {
    val LIGHT_STYLE_URL: String = BuildConfig.MAP_STYLE_LIGHT_URL
    val DARK_STYLE_URL: String = BuildConfig.MAP_STYLE_DARK_URL

    const val ASSET_PREFIX = "asset://"

    /** 지도 위에 항상 보여야 하는 출처 표시 (OpenFreeMap 이용 약관·ODbL) */
    const val ATTRIBUTION_TEXT = "© OpenFreeMap © OpenMapTiles © OpenStreetMap 기여자"

    const val SEOUL_DEFAULT_LAT = 37.5796
    const val SEOUL_DEFAULT_LON = 126.9770
}
