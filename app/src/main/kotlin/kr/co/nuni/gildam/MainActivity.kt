package kr.co.nuni.gildam

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.co.nuni.gildam.auth.NuniAuthManager
import kr.co.nuni.gildam.sync.worker.GildamSyncWorker
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
        if (nuniAuthManager.isAuthRedirect(data)) {
            setIntent(Intent(intent).setData(null)) // 회전 등으로 같은 코드를 두 번 쓰지 않도록
            lifecycleScope.launch {
                nuniAuthManager.completeLogin(data)
                    .onSuccess { GildamSyncWorker.enqueueImmediateSync(applicationContext) }
                    .onFailure { Toast.makeText(this@MainActivity, it.message ?: "로그인에 실패했어요.", Toast.LENGTH_LONG).show() }
            }
        }
        // 로그아웃 복귀(kr.co.nuni.gildam:/logout)는 앱으로 돌아오기만 하면 됨
    }
}
