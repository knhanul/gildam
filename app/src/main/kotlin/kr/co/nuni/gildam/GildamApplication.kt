package kr.co.nuni.gildam

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kr.co.nuni.gildam.data.diagnostics.ExitLogStore
import javax.inject.Inject

@HiltAndroidApp
class GildamApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var exitLogStore: ExitLogStore

    override fun onCreate() {
        super.onCreate()
        // 앱 종료 기록(원인 확인용): 크래시 스택 저장 + 지난 종료 이유는 뒤에서 읽어 둠
        exitLogStore.installCrashHandler()
        Thread({ exitLogStore.recordHistoricalExits() }, "exit-log").start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
