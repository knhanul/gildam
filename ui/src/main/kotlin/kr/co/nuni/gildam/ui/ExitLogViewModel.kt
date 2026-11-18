package kr.co.nuni.gildam.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.nuni.gildam.data.diagnostics.ExitLogEntry
import kr.co.nuni.gildam.data.diagnostics.ExitLogStore
import javax.inject.Inject

/** 설정 > 앱 종료 기록: 이 기기 `crash/` 폴더의 기록을 읽어 보여 줌 */
@HiltViewModel
class ExitLogViewModel @Inject constructor(
    private val store: ExitLogStore
) : ViewModel() {
    private val _entries = MutableStateFlow<List<ExitLogEntry>>(emptyList())
    val entries: StateFlow<List<ExitLogEntry>> = _entries.asStateFlow()

    init {
        viewModelScope.launch {
            _entries.value = withContext(Dispatchers.IO) {
                // 앱 시작 직후 아직 못 읽었을 수 있으니 여기서도 새 기록을 한 번 확인
                store.recordHistoricalExits()
                store.entries()
            }
        }
    }
}
