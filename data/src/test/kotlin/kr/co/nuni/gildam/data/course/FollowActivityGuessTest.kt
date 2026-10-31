package kr.co.nuni.gildam.data.course

import kr.co.nuni.gildam.data.gpxcompare.RuggednessGrade
import kr.co.nuni.gildam.data.gpxcompare.RuggednessResult
import kr.co.nuni.gildam.data.model.TransportType
import org.junit.Assert.assertEquals
import org.junit.Test

class FollowActivityGuessTest {
    private fun course(
        distanceM: Double,
        ascentM: Double,
        avgGrade: Double = 2.0,
        maxUphill: Double = 8.0,
        hasElevation: Boolean = true
    ) = RuggednessResult(
        displayName = "c", pointCount = 100, distanceM = distanceM, hasElevation = hasElevation,
        ascentM = ascentM, descentM = ascentM, maxElevationM = if (hasElevation) 200.0 else null,
        minElevationM = if (hasElevation) 50.0 else null,
        maxUphillGradePct = maxUphill, maxDownhillGradePct = maxUphill, avgAbsGradePct = avgGrade,
        steepDistanceM = 0.0, steepRatio = 0.0, gradeBuckets = emptyList(), profile = emptyList(),
        equivalentKm = distanceM / 1000, score = 1.0, grade = RuggednessGrade.EASY
    )

    @Test fun longFlatCourseSuggestsBike() {
        assertEquals(TransportType.BIKE, FollowActivityGuess.guess(course(distanceM = 25_000.0, ascentM = 150.0)))
    }

    @Test fun steepAscentSuggestsHike() {
        assertEquals(TransportType.HIKE, FollowActivityGuess.guess(course(distanceM = 5_000.0, ascentM = 600.0)))
        assertEquals(TransportType.HIKE, FollowActivityGuess.guess(course(distanceM = 8_000.0, ascentM = 120.0, maxUphill = 22.0)))
    }

    @Test fun shortFlatWalkSuggestsWalk() {
        assertEquals(TransportType.WALK, FollowActivityGuess.guess(course(distanceM = 3_000.0, ascentM = 20.0)))
    }

    @Test fun noElevationDataSuggestsWalk() {
        assertEquals(TransportType.WALK, FollowActivityGuess.guess(course(distanceM = 30_000.0, ascentM = 0.0, hasElevation = false)))
    }

    @Test fun hillyLongCourseIsNotBike() {
        assertEquals(TransportType.HIKE, FollowActivityGuess.guess(course(distanceM = 20_000.0, ascentM = 700.0)))
    }
}
