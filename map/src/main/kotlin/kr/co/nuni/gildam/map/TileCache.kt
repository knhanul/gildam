package kr.co.nuni.gildam.map

import android.content.Context
import org.maplibre.android.offline.OfflineManager

/**
 * 한 번 받은 지도 조각을 폰에 오래 남겨 두어, 기록을 다시 열 때 서버(OVH)에 다시 묻지 않고 바로 그리게 함.
 * 기본 50MB → 400MB. 앱 실행마다 한 번만 설정.
 */
object TileCache {
    const val MAX_BYTES: Long = 400L * 1024 * 1024
    @Volatile private var done = false

    fun ensure(context: Context) {
        if (done) return
        done = true
        runCatching {
            OfflineManager.getInstance(context.applicationContext)
                .setMaximumAmbientCacheSize(MAX_BYTES, object : OfflineManager.FileSourceCallback {
                    override fun onSuccess() = Unit
                    override fun onError(message: String) { done = false }
                })
        }.onFailure { done = false }
    }
}
