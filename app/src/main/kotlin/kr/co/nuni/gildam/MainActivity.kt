package kr.co.nuni.gildam

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import kr.co.nuni.gildam.auth.NuniAuthManager
import kr.co.nuni.gildam.ui.GildamAppRoot
import javax.inject.Inject

/**
 * Android Skills `edge-to-edge` 가이드라인 준수:
 * - Target SDK 35 대응을 위해 `enableEdgeToEdge()`를 호출하고,
 *   각 Compose 화면에서 `WindowInsets.statusBars` / `navigationBars`를 소비합니다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var nuniAuthManager: NuniAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthIntentIfPresent(intent)

        setContent {
            GildamAppRoot()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntentIfPresent(intent)
    }

    private fun handleOAuthIntentIfPresent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.host == "gildam.nuni.co.kr" && data.path?.startsWith("/auth/callback") == true) {
            nuniAuthManager.handleRedirectCallback(data)
        }
    }
}
