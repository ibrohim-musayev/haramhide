package com.haramhide.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haramhide.app.R
import com.haramhide.core.data.DetectionLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * **TZ FR-302 / FR-303 / FR-304 — aniqlash jurnali.**
 *
 * Jurnalda **hech qanday rasm yo'q** — faqat metama'lumot. Eksport ham
 * qo'lda: foydalanuvchi matnni o'zi ulashadi, avtomatik yuborish yo'q
 * (server ham yo'q).
 */
@Composable
fun LogScreen(log: DetectionLog, onBack: () -> Unit) {
    val context = LocalContext.current
    var records by remember { mutableStateOf<List<DetectionLog.Record>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        records = withContext(Dispatchers.IO) { log.recent() }
    }

    val timeFormat = remember { SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()) }
    // Composable ichida getString() konfiguratsiyaga bog'liq emas — oldindan olamiz
    val exportTitle = stringResource(R.string.log_export)

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.log_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.log_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()

            if (records.isEmpty()) {
                Text(
                    stringResource(R.string.log_empty),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(Modifier.weight(1f)) {
                items(records, key = { it.id }) { r ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                timeFormat.format(Date(r.timestampMs)) + "  " + r.packageName,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${r.labels}  %.2f  [%.2f,%.2f]".format(r.score, r.left, r.top),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (r.falsePositive) {
                            Text(
                                stringResource(R.string.log_marked),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB8860B),
                            )
                        } else {
                            TextButton(onClick = {
                                log.markFalsePositive(r.id)
                                refreshKey++
                            }) { Text(stringResource(R.string.log_wrong), fontSize = 11.sp) }
                        }
                    }
                    HorizontalDivider()
                }
            }

            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.ob_back)) }
                Row {
                    TextButton(onClick = {
                        log.clear()
                        refreshKey++
                    }) { Text(stringResource(R.string.log_clear), fontSize = 12.sp) }
                    TextButton(onClick = {
                        // FR-303: qo'lda ulashish. Avtomatik yuborish YO'Q.
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "HaramHide log")
                            putExtra(Intent.EXTRA_TEXT, log.exportText())
                        }
                        context.startActivity(
                            Intent.createChooser(share, exportTitle)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) {
                        Text(
                            stringResource(R.string.log_export),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
