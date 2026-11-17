package kr.co.nuni.gildam.data.region

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 기록의 첫 좌표를 기기 Geocoder 로 되짚어 짧은 지역 이름을 만듭니다.
 *
 * - 결과는 "서울 종로구", "경기 성남시 분당구", "제주 제주시" 처럼 '시/도 + 시·군·구' 형태입니다.
 * - 기기에 Geocoder 가 없거나, 네트워크가 없거나, 주소를 못 찾으면 null.
 * - 어떤 예외도 밖으로 던지지 않습니다(목록 화면이 지역 이름 때문에 멈추면 안 됨).
 */
@Singleton
class RegionResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class ResolvedLocation(
        val regionName: String?,
        val placeName: String?
    )

    suspend fun resolve(lat: Double, lon: Double): String? =
        resolveLocation(lat, lon)?.regionName

    suspend fun resolvePlace(lat: Double, lon: Double): String? =
        resolveLocation(lat, lon)?.placeName

    suspend fun resolveLocation(lat: Double, lon: Double): ResolvedLocation? {
        if (!Geocoder.isPresent()) return null
        return try {
            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lookupAsync(lat, lon)
            } else {
                lookupBlocking(lat, lon)
            }
            address?.let {
                val parts = toRawParts(it)
                ResolvedLocation(
                    regionName = shortName(parts),
                    placeName = placeName(parts)
                )
            }
        } catch (t: Throwable) {
            // IOException(네트워크 없음), IllegalArgumentException, SecurityException, 기기별 오류 등 모두 무시
            null
        }
    }

    /** API 33+: GeocodeListener 로 받은 첫 주소를 기다립니다. */
    private suspend fun lookupAsync(lat: Double, lon: Double): Address? =
        suspendCancellableCoroutine { cont ->
            try {
                Geocoder(context, Locale.KOREA).getFromLocation(
                    lat,
                    lon,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                )
            } catch (t: Throwable) {
                if (cont.isActive) cont.resume(null)
            }
        }

    /** API 32 이하: 블로킹 호출이라 IO 스레드에서 돌립니다. */
    private suspend fun lookupBlocking(lat: Double, lon: Double): Address? =
        withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.KOREA).getFromLocation(lat, lon, 1)?.firstOrNull()
        }

    data class RawAddressParts(
        val countryCode: String? = null,
        val countryName: String? = null,
        val adminArea: String? = null,
        val subAdminArea: String? = null,
        val locality: String? = null,
        val subLocality: String? = null,
        val thoroughfare: String? = null,
        val addressLine: String? = null
    )

    companion object {
        /** '시/도' 접미사 — 짧은 이름은 접미사를 떼고 만듭니다 ("서울특별시" → "서울") */
        private val ADMIN_SUFFIXES = listOf(
            "특별자치시", "특별자치도", "특별시", "광역시", "도"
        )

        private val KOREAN_COUNTRIES = setOf(
            "대한민국", "한국", "South Korea", "Republic of Korea", "Korea"
        )

        private val HANGUL_REGEX = Regex("[\\uAC00-\\uD7A3]")

        /**
         * 한국 행정구역 동·읍·면·가 판별:
         * - 매탄동, 매탄1동, 면목3·8동, 종로1·2·3·4가동, 봉담읍, 양서면, 을지로3가 → 일치
         * - 테헤란로, 효자로13길, 123-4, 101동(숫자로 시작하는 건물 동) → 불일치
         */
        private val DONG_EUP_MYEON_REGEX = Regex("^[가-힣]+(?:[0-9·.-]*동|읍|면|[0-9·.-]+가)$")

        /** 시·군·구 토큰 판별 (특별시·광역시·특별자치시 제외) */
        private val SI_GUN_GU_REGEX = Regex("^[가-힣]+(?:시|군|구)$")

        fun toRawParts(address: Address): RawAddressParts = RawAddressParts(
            countryCode = address.countryCode,
            countryName = address.countryName,
            adminArea = address.adminArea,
            subAdminArea = address.subAdminArea,
            locality = address.locality,
            subLocality = address.subLocality,
            thoroughfare = address.thoroughfare,
            addressLine = runCatching { if (address.maxAddressLineIndex >= 0) address.getAddressLine(0) else null }.getOrNull()
        )

        /** Address 에서 "서울 종로구" 같은 짧은 지역 이름을 만듭니다 (지역별 묶어 보기용). 아무것도 없으면 null */
        fun shortName(address: Address): String? = shortName(toRawParts(address))

        fun shortName(parts: RawAddressParts): String? {
            val admin = parts.adminArea?.trim()?.takeIf { it.isNotEmpty() }
            val sub = parts.subAdminArea?.trim()?.takeIf { it.isNotEmpty() }
            val locality = parts.locality?.trim()?.takeIf { it.isNotEmpty() }
            val subLocality = parts.subLocality?.trim()?.takeIf { it.isNotEmpty() }

            val shortAdmin = admin?.let { shortenAdmin(it) }
            // 시·군·구: subAdminArea 를 먼저, 없으면 locality("성남시" 등)
            val district = sub ?: locality

            if (shortAdmin != null && district != null) {
                // "경기 성남시 분당구" 처럼 이미 시/도가 붙어 있는 경우 중복을 피합니다
                if (district == shortAdmin || district.startsWith("$shortAdmin ")) return district
                return "$shortAdmin $district"
            }
            if (shortAdmin != null) return shortAdmin
            return district ?: subLocality ?: admin
        }

        /**
         * 목록 행의 출발·도착 지명:
         * - 한국: 동·읍·면까지 (예: "매탄동", "역삼동", "봉담읍"). 도로명·번지·건물 번호는 절대 포함하지 않음.
         *   동·읍·면을 못 얻으면 시·군·구로 폴백 (예: "종로구", "분당구", "수원시").
         * - 해외: 나라 + 도시 정도 (예: "일본 도쿄", "Japan Tokyo"). 도로명·번지는 넣지 않음.
         */
        fun placeName(address: Address): String? = placeName(toRawParts(address))

        fun placeName(parts: RawAddressParts): String? {
            return if (isKorean(parts)) koreanPlaceName(parts) else foreignPlaceName(parts)
        }

        private fun isKorean(parts: RawAddressParts): Boolean {
            val code = parts.countryCode?.trim()
            if (!code.isNullOrEmpty()) return code.equals("KR", ignoreCase = true)
            val country = parts.countryName?.trim()
            if (!country.isNullOrEmpty()) return country in KOREAN_COUNTRIES
            return listOf(parts.adminArea, parts.subAdminArea, parts.locality, parts.subLocality, parts.thoroughfare)
                .any { it != null && HANGUL_REGEX.containsMatchIn(it) }
        }

        private fun koreanPlaceName(parts: RawAddressParts): String? {
            extractDongEupMyeon(parts.subLocality)?.let { return it }
            extractDongEupMyeon(parts.thoroughfare)?.let { return it }
            extractDongFromAddressLine(parts.addressLine)?.let { return it }

            // 동·읍·면을 못 얻으면 시·군·구로 폴백 (구 우선 → 시/군 → 광역 축약명)
            val guCandidates = listOf(parts.subLocality, parts.subAdminArea, parts.locality)
            guCandidates.firstNotNullOfOrNull { extractSiGunGu(it, preferGuOnly = true) }?.let { return it }
            guCandidates.firstNotNullOfOrNull { extractSiGunGu(it, preferGuOnly = false) }?.let { return it }
            extractSiGunGuFromAddressLine(parts.addressLine)?.let { return it }
            return parts.adminArea?.trim()?.takeIf { it.isNotEmpty() }?.let { shortenAdmin(it) }
        }

        private fun foreignPlaceName(parts: RawAddressParts): String? {
            val country = parts.countryName?.trim()?.takeIf { it.isNotEmpty() }
                ?: parts.countryCode?.trim()?.takeIf { it.isNotEmpty() }
            val city = listOf(parts.locality, parts.subAdminArea, parts.adminArea, parts.subLocality)
                .firstNotNullOfOrNull { raw -> raw?.trim()?.takeIf { it.isNotEmpty() } }
            return when {
                country != null && city != null ->
                    if (city.equals(country, ignoreCase = true)) city else "$country $city"
                city != null -> city
                else -> country
            }
        }

        private fun extractDongEupMyeon(raw: String?): String? {
            val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return text.split(Regex("[\\s,()]+"))
                .firstOrNull { token -> DONG_EUP_MYEON_REGEX.matches(token) }
        }

        private fun extractDongFromAddressLine(line: String?): String? {
            val text = line?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            // 도로명주소 괄호 표기 "(매탄동, ...)" 안의 법정동을 먼저 확인
            val parenMatch = Regex("\\(([^)]+)\\)").findAll(text)
            for (m in parenMatch) {
                extractDongEupMyeon(m.groupValues[1])?.let { return it }
            }
            return text.split(Regex("[\\s,()]+"))
                .firstOrNull { token -> DONG_EUP_MYEON_REGEX.matches(token) }
        }

        private fun extractSiGunGu(raw: String?, preferGuOnly: Boolean): String? {
            val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val tokens = text.split(Regex("[\\s,()]+")).filter { token ->
                SI_GUN_GU_REGEX.matches(token) && ADMIN_SUFFIXES.none { suffix -> token.endsWith(suffix) }
            }
            return if (preferGuOnly) tokens.lastOrNull { it.endsWith("구") } else tokens.lastOrNull()
        }

        private fun extractSiGunGuFromAddressLine(line: String?): String? {
            val text = line?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val tokens = text.split(Regex("[\\s,()]+")).filter { token ->
                SI_GUN_GU_REGEX.matches(token) && ADMIN_SUFFIXES.none { suffix -> token.endsWith(suffix) }
            }
            return tokens.lastOrNull { it.endsWith("구") } ?: tokens.lastOrNull()
        }

        /** "서울특별시" → "서울", "경기도" → "경기", "세종특별자치시" → "세종" */
        fun shortenAdmin(full: String): String {
            val trimmed = full.trim()
            for (suffix in ADMIN_SUFFIXES) {
                if (trimmed.length > suffix.length && trimmed.endsWith(suffix)) {
                    return trimmed.dropLast(suffix.length).trim()
                }
            }
            return trimmed
        }
    }
}
