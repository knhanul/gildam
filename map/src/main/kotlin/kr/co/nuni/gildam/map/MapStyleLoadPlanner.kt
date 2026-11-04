package kr.co.nuni.gildam.map

/** 지도 스타일과 길담 레이어(경로·점)를 어떻게 다룰지 */
enum class StyleAction {
    /** 새 스타일을 불러옴 (처음, 또는 밝게/어둡게 바뀜) */
    LOAD,
    /** 같은 스타일을 불러오는 중 → 다시 부르지 말고 기다림 */
    WAIT,
    /** 스타일은 다 불러왔는데 길담 레이어가 없음 → 레이어 추가 */
    ADD_LAYERS,
    /** 레이어가 있음 → 데이터만 갱신 */
    UPDATE
}

/**
 * 순수 함수 (단위 테스트용).
 * 예전에는 스타일을 불러오는 동안(map.style == null) 화면이 갱신될 때마다 setStyle 을 다시 불러,
 * 네트워크가 느리면 불러오기가 계속 처음부터 다시 시작되어 경로·현재 위치 레이어가 끝내 추가되지 않았습니다.
 */
object MapStyleLoadPlanner {
    fun decide(requestedUrl: String?, wantedUrl: String, styleLoaded: Boolean, hasGildamLayers: Boolean): StyleAction = when {
        requestedUrl != wantedUrl -> StyleAction.LOAD
        !styleLoaded -> StyleAction.WAIT
        !hasGildamLayers -> StyleAction.ADD_LAYERS
        else -> StyleAction.UPDATE
    }
}
