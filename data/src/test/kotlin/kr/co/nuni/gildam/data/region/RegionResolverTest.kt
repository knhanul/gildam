package kr.co.nuni.gildam.data.region

import kr.co.nuni.gildam.data.list.ActivityPlaceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionResolverTest {

    @Test
    fun `한국 주소에서 동·읍·면·가를 추출한다`() {
        assertEquals(
            "매탄동",
            RegionResolver.placeName(
                RegionResolver.RawAddressParts(
                    countryCode = "KR",
                    adminArea = "경기도",
                    locality = "수원시",
                    subLocality = "영통구",
                    thoroughfare = "매탄동"
                )
            )
        )
        assertEquals(
            "역삼1동",
            RegionResolver.placeName(
                RegionResolver.RawAddressParts(
                    countryCode = "KR",
                    adminArea = "서울특별시",
                    subAdminArea = "강남구",
                    subLocality = "역삼1동",
                    thoroughfare = "테헤란로"
                )
            )
        )
        assertEquals(
            "봉담읍",
            RegionResolver.placeName(
                RegionResolver.RawAddressParts(
                    countryCode = "KR",
                    adminArea = "경기도",
                    locality = "화성시",
                    subLocality = "봉담읍",
                    thoroughfare = "동화길"
                )
            )
        )
        assertEquals(
            "양서면",
            RegionResolver.placeName(
                RegionResolver.RawAddressParts(
                    countryCode = "KR",
                    adminArea = "경기도",
                    locality = "양평군",
                    subLocality = "양서면",
                    thoroughfare = "양수리"
                )
            )
        )
        assertEquals(
            "을지로3가",
            RegionResolver.placeName(
                RegionResolver.RawAddressParts(
                    countryCode = "KR",
                    adminArea = "서울특별시",
                    subAdminArea = "중구",
                    subLocality = "을지로3가"
                )
            )
        )
    }

    @Test
    fun `도로명·번지·건물 동 번호는 표시하지 않고 괄호 속 법정동이나 시·군·구로 처리한다`() {
        // 지번이나 아파트 동 번호("101동")가 붙어 있어도 행정동 이름만 남긴다
        assertEquals(
            "매탄동",
            RegionResolver.placeName(
                RegionResolver.RawAddressParts(
                    countryCode = "KR",
                    adminArea = "경기도",
                    locality = "수원시",
                    subLocality = "영통구",
                    thoroughfare = "매탄동 123-4 101동"
                )
            )
        )
        // 도로명주소 전체 문자열 괄호에 법정동이 있는 경우 법정동만 뽑고 도로명·건물번호·건물명은 버린다
        assertEquals(
            "매탄동",
            RegionResolver.placeName(
                RegionResolver.RawAddressParts(
                    countryCode = "KR",
                    adminArea = "경기도",
                    locality = "수원시",
                    subLocality = "영통구",
                    thoroughfare = "삼성로",
                    addressLine = "대한민국 경기도 수원시 영통구 삼성로 129 (매탄동, 삼성전자)"
                )
            )
        )
        // 동·읍·면 정보가 전혀 없고 도로명·번지만 있으면 시·군·구로 폴백한다
        assertEquals(
            "강남구",
            RegionResolver.placeName(
                RegionResolver.RawAddressParts(
                    countryCode = "KR",
                    adminArea = "서울특별시",
                    subLocality = "강남구",
                    thoroughfare = "테헤란로 152",
                    addressLine = "대한민국 서울특별시 강남구 테헤란로 152 101동"
                )
            )
        )
        assertEquals(
            "분당구",
            RegionResolver.placeName(
                RegionResolver.RawAddressParts(
                    countryCode = "KR",
                    adminArea = "경기도",
                    locality = "성남시",
                    subLocality = "분당구",
                    thoroughfare = "판교역로 235"
                )
            )
        )
        assertEquals(
            "양평군",
            RegionResolver.placeName(
                RegionResolver.RawAddressParts(
                    countryCode = "KR",
                    adminArea = "경기도",
                    locality = "양평군",
                    thoroughfare = "북한강로 89"
                )
            )
        )
    }

    @Test
    fun `지역별 묶어 보기용 shortName 은 시·도 + 시·군·구 단위를 유지한다`() {
        assertEquals(
            "서울 종로구",
            RegionResolver.shortName(
                RegionResolver.RawAddressParts(
                    countryCode = "KR",
                    adminArea = "서울특별시",
                    subAdminArea = "종로구",
                    subLocality = "삼청동"
                )
            )
        )
        assertEquals(
            "경기 성남시 분당구",
            RegionResolver.shortName(
                RegionResolver.RawAddressParts(
                    countryCode = "KR",
                    adminArea = "경기도",
                    subAdminArea = "성남시 분당구",
                    subLocality = "정자동"
                )
            )
        )
    }

    @Test
    fun `해외 주소는 나라와 도시 정도만 표시하고 도로명·번지는 넣지 않는다`() {
        assertEquals(
            "일본 도쿄",
            RegionResolver.placeName(
                RegionResolver.RawAddressParts(
                    countryCode = "JP",
                    countryName = "일본",
                    adminArea = "도쿄도",
                    locality = "도쿄",
                    thoroughfare = "시부야 1-2-3"
                )
            )
        )
        assertEquals(
            "France Paris",
            RegionResolver.placeName(
                RegionResolver.RawAddressParts(
                    countryCode = "FR",
                    countryName = "France",
                    locality = "Paris",
                    thoroughfare = "10 Rue de Rivoli"
                )
            )
        )
    }

    @Test
    fun `출발·도착 지명이 같으면 한 번만 표시한다`() {
        assertEquals(
            "매탄동 → 역삼동",
            ActivityPlaceInfo(startPlace = "매탄동", endPlace = "역삼동").displayPlaces()
        )
        assertEquals(
            "매탄동",
            ActivityPlaceInfo(startPlace = "매탄동", endPlace = "매탄동").displayPlaces()
        )
        assertEquals(
            "매탄동",
            ActivityPlaceInfo(startPlace = "매탄동", endPlace = null).displayPlaces()
        )
        assertNull(
            ActivityPlaceInfo(startPlace = null, endPlace = null).displayPlaces()
        )
    }
}
