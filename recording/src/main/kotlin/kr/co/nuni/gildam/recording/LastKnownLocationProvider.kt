package kr.co.nuni.gildam.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** 출발 전 날씨 조회에 쓰는 마지막 알려진 위치. 권한이 없거나 값이 없으면 null. */
@Singleton
class LastKnownLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    suspend fun lastLocation(): LatLonPoint? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val location = runCatching { client.lastLocation.await() }.getOrNull() ?: return null
        return LatLonPoint(location.latitude, location.longitude)
    }
}
