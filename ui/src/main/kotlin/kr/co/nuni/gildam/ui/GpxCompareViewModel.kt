package kr.co.nuni.gildam.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.nuni.gildam.data.gpxcompare.GpxParseException
import kr.co.nuni.gildam.data.gpxcompare.GpxRuggedness
import kr.co.nuni.gildam.data.gpxcompare.GpxTrackParser
import kr.co.nuni.gildam.ui.screens.GpxCompareEntry
import java.util.UUID

/**
 * GPX 비교 상태. 파일은 SAF 로 읽기만 하고 DB 저장·동기화·영구 권한 요청을 하지 않습니다.
 * 화면을 떠나면(ViewModel 정리) 결과도 사라집니다.
 */
class GpxCompareViewModel : ViewModel() {
    private val _entries = MutableStateFlow<List<GpxCompareEntry>>(emptyList())
    val entries: StateFlow<List<GpxCompareEntry>> = _entries.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun addFiles(context: Context, uris: List<Uri>) {
        val room = MAX_FILES - _entries.value.size
        if (room <= 0 || uris.isEmpty()) return
        val resolver = context.applicationContext.contentResolver
        viewModelScope.launch {
            _loading.value = true
            val added = withContext(Dispatchers.IO) {
                uris.take(room).map { uri ->
                    val name = displayName(context, uri)
                    try {
                        val pts = resolver.openInputStream(uri)?.use { GpxTrackParser.parse(it) }
                            ?: throw GpxParseException("파일을 열 수 없어요.")
                        GpxCompareEntry.Loaded(UUID.randomUUID().toString(), GpxRuggedness.analyze(name, pts))
                    } catch (e: GpxParseException) {
                        GpxCompareEntry.Failed(UUID.randomUUID().toString(), name, e.message ?: "읽기 실패")
                    } catch (e: Exception) {
                        GpxCompareEntry.Failed(UUID.randomUUID().toString(), name, "파일을 읽는 중 문제가 생겼어요.")
                    }
                }
            }
            _entries.value = _entries.value + added
            _loading.value = false
        }
    }

    fun remove(id: String) { _entries.value = _entries.value.filterNot { it.id == id } }
    fun clear() { _entries.value = emptyList() }

    private fun displayName(context: Context, uri: Uri): String {
        val raw = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "GPX"
        return raw.removeSuffix(".gpx").removeSuffix(".GPX")
    }

    companion object { const val MAX_FILES = 4 }
}
