package com.haramhide.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haramhide.app.R
import com.haramhide.core.context.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * **TZ FR-204 — ilovalar bo'yicha whitelist.**
 *
 * Bo'sh ro'yxat = hamma ilovada ishlaydi. Bu ataylab shunday default:
 * foydalanuvchi ro'yxatni to'ldirmasa ham himoya ishlaydi, aksincha emas.
 *
 * Ro'yxatdan olib tashlash himoyani zaiflashtiradi, shuning uchun saqlash
 * cool-down orqali o'tadi (TZ FR-205) — buni chaqiruvchi hal qiladi.
 */
@Composable
fun AppPickerScreen(
    selected: Set<String>,
    onSave: (Set<String>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApps.Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    // Default TRUE. Telefonlarda brauzer, YouTube va ko'p ijtimoiy ilovalar
    // oldindan o'rnatilgan, ya'ni FLAG_SYSTEM ga ega. Ularni yashirish aynan
    // himoyalash kerak bo'lgan ilovalarni ro'yxatdan chiqarib yuborardi.
    var showSystem by remember { mutableStateOf(true) }
    var current by remember { mutableStateOf(selected) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { InstalledApps.list(context) }
        loading = false
    }

    val visible = apps.filter { e ->
        (showSystem || !e.isSystem || e.packageName in current) &&
            (query.isBlank() ||
                e.label.contains(query, ignoreCase = true) ||
                e.packageName.contains(query, ignoreCase = true))
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.apps_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.apps_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.apps_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        current = current + InstalledApps.suggestDefaults(apps)
                    }) { Text(stringResource(R.string.apps_select_defaults), fontSize = 12.sp) }
                    TextButton(onClick = { current = emptySet() }) {
                        Text(stringResource(R.string.apps_clear), fontSize = 12.sp)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.apps_show_system), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = showSystem, onCheckedChange = { showSystem = it })
                }
            }
            Divider()

            if (loading) {
                Text("…", Modifier.padding(16.dp))
            } else if (visible.isEmpty()) {
                Text(
                    stringResource(R.string.apps_empty),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(visible, key = { it.packageName }) { app ->
                        val checked = app.packageName in current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    current = if (checked) current - app.packageName
                                    else current + app.packageName
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Divider()
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.ob_back)) }
                TextButton(onClick = { onSave(current) }) {
                    Text(stringResource(R.string.apps_save), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
