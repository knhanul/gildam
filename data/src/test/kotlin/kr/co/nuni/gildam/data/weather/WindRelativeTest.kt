package kr.co.nuni.gildam.data.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WindRelativeTest {
    @Test fun cardinal16() {
        assertEquals(WindCardinal16.N, windCardinal16(0.0))
        assertEquals(WindCardinal16.N, windCardinal16(359.0))
        assertEquals(WindCardinal16.NNE, windCardinal16(22.5))
        assertEquals(WindCardinal16.NE, windCardinal16(45.0))
        assertEquals(WindCardinal16.WNW, windCardinal16(292.5))
        assertEquals(WindCardinal16.S, windCardinal16(-180.0))
    }

    @Test fun headTailCross() {
        // 북쪽으로 가는데 북풍(북에서 불어옴) → 맞바람
        assertEquals(RelativeWind.HEAD, relativeWind(0.0, 0.0))
        assertEquals(RelativeWind.HEAD, relativeWind(40.0, 0.0))
        assertEquals(RelativeWind.HEAD, relativeWind(350.0, 10.0))
        // 남풍인데 북쪽으로 → 뒷바람
        assertEquals(RelativeWind.TAIL, relativeWind(180.0, 0.0))
        assertEquals(RelativeWind.TAIL, relativeWind(200.0, 10.0))
        // 서풍인데 북쪽으로 → 옆바람
        assertEquals(RelativeWind.CROSS, relativeWind(270.0, 0.0))
        assertEquals(RelativeWind.CROSS, relativeWind(90.0, 0.0))
    }

    @Test fun arrowPointsWhereWindBlows() {
        // 북풍은 남쪽으로 붑니다 → 북쪽 위 지도에서 아래(180°)
        assertEquals(180f, windArrowScreenDeg(0.0, 0.0), 0.01f)
        // 지도가 90° 돌아가 있으면 화살표도 그만큼 반대로
        assertEquals(90f, windArrowScreenDeg(0.0, 90.0), 0.01f)
        assertEquals(270f, windArrowScreenDeg(90.0, 0.0), 0.01f)
    }

    @Test fun feelsLike() {
        // 0°C, 5 m/s → 기상청 식 약 -4.9°C
        assertEquals(-4.9, feelsLikeC(0.0, 5.0, 50.0), 0.1)
        // 바람이 약하면 기온 그대로
        assertEquals(5.0, feelsLikeC(5.0, 1.0, 50.0), 0.001)
        // 32°C, 습도 70% → 열지수 약 40°C
        assertEquals(40.0, feelsLikeC(32.0, 2.0, 70.0), 1.5)
        // 20°C 는 그대로
        assertEquals(20.0, feelsLikeC(20.0, 5.0, 90.0), 0.001)
    }
}
