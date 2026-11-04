package kr.co.nuni.gildam.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapStyleLoadPlannerTest {
    private val url = "asset://styles/liberty.json"

    @Test fun firstTimeLoads() =
        assertEquals(StyleAction.LOAD, MapStyleLoadPlanner.decide(null, url, styleLoaded = false, hasGildamLayers = false))

    @Test fun whileLoadingDoesNotRestartLoad() {
        // 예전 버그: 불러오는 중 화면이 갱신될 때마다 setStyle 을 다시 불러 경로 레이어가 끝내 안 생김
        repeat(10) {
            assertEquals(StyleAction.WAIT, MapStyleLoadPlanner.decide(url, url, styleLoaded = false, hasGildamLayers = false))
        }
    }

    @Test fun loadedWithoutLayersAddsThem() =
        assertEquals(StyleAction.ADD_LAYERS, MapStyleLoadPlanner.decide(url, url, styleLoaded = true, hasGildamLayers = false))

    @Test fun loadedWithLayersUpdatesOnly() =
        assertEquals(StyleAction.UPDATE, MapStyleLoadPlanner.decide(url, url, styleLoaded = true, hasGildamLayers = true))

    @Test fun themeChangeReloads() =
        assertEquals(StyleAction.LOAD, MapStyleLoadPlanner.decide(url, "asset://styles/dark.json", styleLoaded = true, hasGildamLayers = true))
}
