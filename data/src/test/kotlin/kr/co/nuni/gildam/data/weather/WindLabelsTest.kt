package kr.co.nuni.gildam.data.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WindLabelsTest {

    @Test
    fun eightPointLabelsFromMeteorologicalDirection() {
        assertEquals(WindCardinal.N, windCardinal(0.0))
        assertEquals("북풍", windCardinal(0.0).labelKo)
        assertEquals(WindCardinal.NE, windCardinal(45.0))
        assertEquals("북동풍", windCardinal(45.0).labelKo)
        assertEquals(WindCardinal.E, windCardinal(90.0))
        assertEquals(WindCardinal.SE, windCardinal(135.0))
        assertEquals(WindCardinal.S, windCardinal(180.0))
        assertEquals(WindCardinal.SW, windCardinal(225.0))
        assertEquals(WindCardinal.W, windCardinal(270.0))
        assertEquals(WindCardinal.NW, windCardinal(315.0))
        assertEquals("북서풍", windCardinal(315.0).labelKo)
    }

    @Test
    fun sectorBoundariesAndWrapAround() {
        assertEquals(WindCardinal.N, windCardinal(22.4))
        assertEquals(WindCardinal.NE, windCardinal(22.5))
        assertEquals(WindCardinal.N, windCardinal(337.5))
        assertEquals(WindCardinal.N, windCardinal(360.0))
        assertEquals(WindCardinal.NW, windCardinal(-45.0))
        assertEquals(WindCardinal.E, windCardinal(450.0))
    }

    @Test
    fun strengthBuckets() {
        assertEquals(WindStrength.LIGHT, windStrength(0.0))
        assertEquals("약함", windStrength(3.99).labelKo)
        assertEquals(WindStrength.MODERATE, windStrength(4.0))
        assertEquals("보통", windStrength(8.99).labelKo)
        assertEquals(WindStrength.STRONG, windStrength(9.0))
        assertEquals("강함", windStrength(13.99).labelKo)
        assertEquals(WindStrength.VERY_STRONG, windStrength(14.0))
        assertEquals("매우 강함", windStrength(20.0).labelKo)
    }

    @Test
    fun towardRotationIsDirectionPlus180() {
        assertEquals(180f, windTowardRotationDeg(0.0), 0.01f)
        assertEquals(135f, windTowardRotationDeg(315.0), 0.01f)
        assertEquals(0f, windTowardRotationDeg(180.0), 0.01f)
    }
}
