package kr.co.nuni.gildam.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.nuni.gildam.data.diagnostics.ExitLogEntry
import kr.co.nuni.gildam.ui.R
import kr.co.nuni.gildam.ui.l10n.currentLocale
import kr.co.nuni.gildam.ui.l10n.labelRes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 설정 > 앱 정보 > 앱 종료 기록.
 * 목록(시각 · 이유 설명) → 누르면 전체 내용과 '복사' · '공유'. 기록은 이 기기에만 있음.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppExitLogScreen(
    entries: List<ExitLogEntry>,
    onNavigateBack: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
    /** 미리보기·테스트에서 처음부터 열어 둘 기록 id */
    initialSelectedId: String? = null
) {
    var selectedId by rememberSaveable { mutableStateOf(initialSelectedId) }
    val selected = entries.firstOrNull { it.id == selectedId }
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", currentLocale())
    fun time(e: ExitLogEntry) = Instant.ofEpochMilli(e.timestampMs).atZone(zone).format(formatter)

    BackHandler(enabled = selected != null) { selectedId = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exit_log_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (selected != null) selectedId = null else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (selected == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.exit_log_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entries.isEmpty()) {
                    Text(stringResource(R.string.exit_log_empty), style = MaterialTheme.typography.bodyMedium)
                } else SettingsListCard {
                    entries.forEachIndexed { i, e ->
                        if (i > 0) SettingsItemDivider()
                        ExitLogRow(time(e), stringResource(e.kind.labelRes()), e.summary.ifBlank { e.reasonName }) { selectedId = e.id }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        } else {
            ExitLogDetail(selected, time(selected), Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun ExitLogRow(time: String, reason: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsItemIcon(Icons.Outlined.ErrorOutline)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(reason, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (summary.isNotBlank()) Text(
                summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExitLogDetail(entry: ExitLogEntry, time: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copiedText = stringResource(R.string.exit_log_copied)
    val shareTitle = stringResource(R.string.exit_log_share_title)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(entry.kind.labelRes()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = {
                clipboard.setText(AnnotatedString(entry.content))
                Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
            }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.exit_log_copy))
            }
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                    putExtra(Intent.EXTRA_TEXT, entry.content)
                }
                runCatching { context.startActivity(Intent.createChooser(send, shareTitle)) }
            }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.exit_log_share))
            }
        }
        Text(
            entry.content,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        )
    }
}
