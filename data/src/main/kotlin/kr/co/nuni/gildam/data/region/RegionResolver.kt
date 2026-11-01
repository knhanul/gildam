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

    suspend fun resolve(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lookupAsync(lat, lon)
            } else {
                lookupBlocking(lat, lon)
            }
            address?.let { shortName(it) }
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

    companion object {
        /** '시/도' 접미사 — 짧은 이름은 접미사를 떼고 만듭니다 ("서울특별시" → "서울") */
        private val ADMIN_SUFFIXES = listOf(
            "특별자치시", "특별자치도", "특별시", "광역시", "도"
        )

        /** Address 에서 "서울 종로구" 같은 짧은 지역 이름을 만듭니다. 아무것도 없으면 null */
        fun shortName(address: Address): String? {
            val admin = address.adminArea?.trim()?.takeIf { it.isNotEmpty() }
            val sub = address.subAdminArea?.trim()?.takeIf { it.isNotEmpty() }
            val locality = address.locality?.trim()?.takeIf { it.isNotEmpty() }
            val subLocality = address.subLocality?.trim()?.takeIf { it.isNotEmpty() }

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
