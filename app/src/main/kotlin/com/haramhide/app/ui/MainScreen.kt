package com.haramhide.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.haramhide.app.ProjectionRequestActivity
import com.haramhide.app.ProtectionService
import com.haramhide.app.ProtectionState
import com.haramhide.app.R
import com.haramhide.core.data.AppSettings
import com.haramhide.core.data.DayStats
import com.haramhide.core.data.PendingChange
import kotlinx.coroutines.delay

/** Asosiy boshqaruv ekrani. */
@Composable
fun MainScreen(
    settings: AppSettings,
    status: ProtectionState.Status,
    stats: ProtectionState.Stats,
    daily: DayStats,
    perms: PermissionState,
    onRequestNotifications: () -> Unit,
    onOpenApps: () -> Unit,
    onSetSensitivity: (String) -> Unit,
    onSetEngine: (String) -> Unit,
    onSetBlurStyle: (String) -> Unit,
    onSetBlurIntensity: (Int) -> Unit,
    onSetScrollShield: (Boolean) -> Unit,
    onSetUnblurLimit: (Int) -> Unit,
    onCancelPending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        StatusCard(status, perms.allGranted, context)
        settings.pending?.let { PendingCard(it, onCancelPending) }
        DailyCard(daily, context)
        PermissionsCard(perms, context, onRequestNotifications)
        SettingsCard(
            settings = settings,
            onOpenApps = onOpenApps,
            onSetSensitivity = onSetSensitivity,
            onSetEngine = onSetEngine,
            onSetBlurStyle = onSetBlurStyle,
            onSetBlurIntensity = onSetBlurIntensity,
            onSetScrollShield = onSetScrollShield,
            onSetUnblurLimit = onSetUnblurLimit,
        )
        if (settings.debugOverlay) StatsCard(stats, context)
        Oem.hintFor()?.let { OemCard(it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusCard(status: ProtectionState.Status, ready: Boolean, context: Context) {
    val (labelRes, color) = when (status) {
        ProtectionState.Status.RUNNING -> R.string.status_running to Color(0xFF1B8A3A)
        ProtectionState.Status.SESSION_LOST -> R.string.status_lost to Color(0xFFB8860B)
        ProtectionState.Status.STOPPED -> R.string.status_stopped to Color(0xFF8A1B1B)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.status_title), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(labelRes), style = MaterialTheme.typography.titleLarge, color = color)

            if (status == ProtectionState.Status.RUNNING) {
                OutlinedButton(
                    onClick = { ProtectionService.stop(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.btn_stop)) }
            } else {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, ProjectionRequestActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.btn_enable)) }
                if (!ready) {
                    Text(
                        stringResource(R.string.perm_required_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Text(
                stringResource(R.string.system_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** TZ FR-205 — kutayotgan zaiflashtirish so'rovi va taymer. */
@Composable
private fun PendingCard(pending: PendingChange, onCancel: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pending) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val remaining = pending.remainingMs(now)

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.cooldown_title), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(
                    when (pending.type) {
                        PendingChange.Type.STOP -> R.string.cooldown_stop
                        PendingChange.Type.SENSITIVITY -> R.string.cooldown_sensitivity
                        PendingChange.Type.PACKAGES -> R.string.cooldown_packages
                        PendingChange.Type.ENGINE -> R.string.cooldown_engine
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.cooldown_remaining, formatDuration(context, remaining)),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                stringResource(R.string.cooldown_explain),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cooldown_cancel)) }
        }
    }
}

/** TZ FR-305 — bugungi lokal statistika. Hech qayerga yuborilmaydi. */
@Composable
private fun DailyCard(daily: DayStats, context: Context) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.daily_title), style = MaterialTheme.typography.titleMedium)
            StatRow(
                stringResource(R.string.daily_active),
                formatDuration(context, daily.activeSeconds * 1000),
            )
            StatRow(stringResource(R.string.daily_masks), daily.masksCreated.toString())
            StatRow(stringResource(R.string.daily_drops), daily.sessionDrops.toString())
            StatRow(stringResource(R.string.daily_unblurs), daily.unblursUsed.toString())
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun PermissionsCard(
    perms: PermissionState,
    context: Context,
    onRequestNotifications: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.titleMedium)

            PermRow(R.string.perm_overlay, R.string.perm_overlay_why, perms.overlay) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            PermRow(R.string.perm_usage, R.string.perm_usage_why, perms.usageStats) {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            PermRow(R.string.perm_notif, R.string.perm_notif_why, perms.notifications) {
                onRequestNotifications()
            }
            PermRow(R.string.perm_battery, R.string.perm_battery_why, perms.batteryExempt) {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

@Composable
private fun PermRow(labelRes: Int, whyRes: Int, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                (if (granted) "✓  " else "✗  ") + stringResource(labelRes),
                color = if (granted) Color(0xFF1B8A3A) else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(whyRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!granted) TextButton(onClick = onClick) { Text(stringResource(R.string.perm_open)) }
    }
}

@Composable
private fun SettingsCard(
    settings: AppSettings,
    onOpenApps: () -> Unit,
    onSetSensitivity: (String) -> Unit,
    onSetEngine: (String) -> Unit,
    onSetBlurStyle: (String) -> Unit,
    onSetBlurIntensity: (Int) -> Unit,
    onSetScrollShield: (Boolean) -> Unit,
    onSetUnblurLimit: (Int) -> Unit,
) {
    var intensity by remember(settings.blurIntensity) {
        mutableStateOf(settings.blurIntensity.toFloat())
    }
    var unblur by remember(settings.unblurLimitPerDay) {
        mutableStateOf(settings.unblurLimitPerDay.toFloat())
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium)

            // Himoyalanadigan ilovalar
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.set_apps), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (settings.protectedPackages.isEmpty()) stringResource(R.string.set_apps_all)
                        else stringResource(R.string.set_apps_count, settings.protectedPackages.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenApps) { Text(stringResource(R.string.perm_open)) }
            }

            Text(stringResource(R.string.set_sensitivity), style = MaterialTheme.typography.labelMedium)
            ChipRow(listOf("LOW", "MEDIUM", "STRICT"), settings.sensitivity, onSetSensitivity)
            Text(
                stringResource(
                    when (settings.sensitivity) {
                        "LOW" -> R.string.set_sens_low
                        "STRICT" -> R.string.set_sens_strict
                        else -> R.string.set_sens_medium
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(stringResource(R.string.set_blur_style), style = MaterialTheme.typography.labelMedium)
            ChipRow(listOf("GAUSSIAN", "PIXELATE", "SOLID"), settings.blurStyle, onSetBlurStyle)

            Text(
                stringResource(R.string.set_blur_intensity, intensity.toInt()),
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = intensity,
                onValueChange = { intensity = it },
                onValueChangeFinished = { onSetBlurIntensity(intensity.toInt()) },
                valueRange = 10f..100f,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.set_scroll_shield), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.set_scroll_shield_why),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.scrollShield, onCheckedChange = onSetScrollShield)
            }

            Text(
                stringResource(R.string.set_unblur_limit, unblur.toInt()),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                stringResource(R.string.set_unblur_why),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = unblur,
                onValueChange = { unblur = it },
                onValueChangeFinished = { onSetUnblurLimit(unblur.toInt()) },
                valueRange = 0f..20f,
                steps = 19,
            )

            Text(stringResource(R.string.set_detector), style = MaterialTheme.typography.labelMedium)
            ChipRow(listOf("NUDENET", "HEURISTIC"), settings.detectorEngine, onSetEngine)
            Text(
                stringResource(
                    if (settings.detectorEngine == "NUDENET") R.string.set_detector_nudenet
                    else R.string.set_detector_heuristic
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option, fontSize = 11.sp) },
            )
        }
    }
}

@Composable
private fun StatsCard(stats: ProtectionState.Stats, context: Context) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.titleMedium)
            Mono("model      : ${stats.engine}")
            Mono("inference  : ${stats.inferenceMs} ms")
            Mono("miltillash : ${stats.flickerEvents}")
            Mono("kadr/fps   : ${stats.framesProcessed} / ${"%.1f".format(stats.fps)}")
            Mono("ishlov     : ${"%.1f".format(stats.avgProcessMs)} / ${stats.maxProcessMs} ms")
            Mono("mask       : ${stats.masksActive} / ${stats.masksCreated}")
            Mono("probe      : ${stats.probesConfirmed} / ${stats.probes}")
            Mono("klasslar   : ${stats.lastLabels.ifEmpty { "-" }}")
            Mono("qora kadr  : ${stats.secureFrames}")
            Mono("uzilish    : ${stats.sessionLostCount}")
            Mono("capture    : ${stats.captureSize}")
            Mono("faol ilova : ${stats.activePackage ?: "-"}")
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { ProtectionService.resetStats(context) }) {
                Text(stringResource(R.string.stats_reset), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Mono(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}

@Composable
private fun OemCard(hint: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(android.os.Build.MANUFACTURER, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
    }
}
