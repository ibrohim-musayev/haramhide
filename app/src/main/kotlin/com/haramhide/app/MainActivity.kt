package com.haramhide.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.haramhide.core.context.ActivePackageMonitor
import com.haramhide.core.data.AppSettings
import com.haramhide.core.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * F0 boshqaruv ekrani.
 *
 * Bu to'liq mahsulot UI'si emas — TZ 7-bo'limidagi onboarding, cool-down va
 * whitelist ekranlari F2 fazasiga tegishli. Bu yerdagi maqsad: ruxsatlarni
 * berish, himoyani yoqish va **F0 o'lchovlarini ko'rish**.
 */
class MainActivity : ComponentActivity() {

    private lateinit var settingsRepo: SettingsRepository

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepo = SettingsRepository(applicationContext)
        Notifications.ensureChannels(this)

        setContent {
            MaterialTheme {
                Scaffold { padding ->
                    MainScreen(
                        modifier = Modifier.padding(padding),
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        settingsRepo = settingsRepo,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreen(
    modifier: Modifier = Modifier,
    onRequestNotifications: () -> Unit,
    settingsRepo: SettingsRepository,
) {
    val context = LocalContext.current
    val status by ProtectionState.status.collectAsState()
    val stats by ProtectionState.stats.collectAsState()

    // Ruxsatlar tizim ekranida beriladi — qaytgach qayta tekshiramiz
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var settings by remember { mutableStateOf(AppSettings()) }
    LaunchedSettings(settingsRepo, refreshKey) { settings = it }

    val perms = remember(refreshKey) { Permissions.check(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "HaramHide",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "F0 texnik prototip — TZ v2.1",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StatusCard(status = status, ready = perms.allGranted, context = context)
        PermissionsCard(perms = perms, context = context, onRequestNotifications = onRequestNotifications)
        StatsCard(stats = stats)
        SettingsCard(settings = settings, settingsRepo = settingsRepo)
        OemCard()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LaunchedSettings(
    repo: SettingsRepository,
    key: Int,
    onLoaded: (AppSettings) -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(key) {
        runCatching { onLoaded(repo.settings.first()) }
    }
}

@Composable
private fun StatusCard(status: ProtectionState.Status, ready: Boolean, context: Context) {
    val (label, color) = when (status) {
        ProtectionState.Status.RUNNING -> "Faol" to Color(0xFF1B8A3A)
        ProtectionState.Status.SESSION_LOST -> "Sessiya uzildi — qayta yoqing" to Color(0xFFB8860B)
        ProtectionState.Status.STOPPED -> "To'xtagan" to Color(0xFF8A1B1B)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Holat", style = MaterialTheme.typography.labelMedium)
            Text(label, style = MaterialTheme.typography.titleLarge, color = color)

            if (status == ProtectionState.Status.RUNNING) {
                Button(
                    onClick = { ProtectionService.stop(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Himoyani to'xtatish") }
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
                ) { Text("Himoyani yoqish") }
                if (!ready) {
                    Text(
                        "Avval quyidagi ruxsatlarni bering",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Text(
                "Eslatma: Android har safar ekran yozib olishga alohida rozilik so'raydi " +
                    "va ekran qulflansa sessiya avtomatik to'xtaydi. Bu tizim cheklovi " +
                    "(TZ C-01, C-02) — chetlab o'tib bo'lmaydi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionsCard(
    perms: Permissions.State,
    context: Context,
    onRequestNotifications: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Ruxsatlar", style = MaterialTheme.typography.titleMedium)

            PermRow("Ekran ustida chizish", perms.overlay) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            PermRow("Foydalanish statistikasi", perms.usageStats) {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            PermRow("Bildirishnomalar", perms.notifications) { onRequestNotifications() }
            PermRow("Batareya cheklovidan ozod", perms.batteryExempt) {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

@Composable
private fun PermRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (if (granted) "✓  " else "✗  ") + label,
            color = if (granted) Color(0xFF1B8A3A) else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!granted) TextButton(onClick = onClick) { Text("Ochish") }
    }
}

@Composable
private fun StatsCard(stats: ProtectionState.Stats) {
    val context = LocalContext.current
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("O'lchovlar", style = MaterialTheme.typography.titleMedium)
            Text(
                "TZ FR-105 qabul mezoni: statik rasm ustida 10 s davomida " +
                    "MILTILLASH = 0 bo'lishi shart.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Mono("model                : ${stats.engine}")
            Mono("inference            : ${stats.inferenceMs} ms")
            Mono("miltillash (flicker) : ${stats.flickerEvents}")
            Mono("kadr / fps           : ${stats.framesProcessed} / ${"%.1f".format(stats.fps)}")
            Mono("ishlov o'rt / maks   : ${"%.1f".format(stats.avgProcessMs)} / ${stats.maxProcessMs} ms")
            Mono("mask faol / yaratildi: ${stats.masksActive} / ${stats.masksCreated}")
            Mono("probe tasdiq / jami  : ${stats.probesConfirmed} / ${stats.probes}")
            Mono("Stage A ball         : ${"%.2f".format(stats.stageAScore)}")
            Mono("Stage B ulushi       : ${"%.1f".format(stats.stageBRatio * 100)}%")
            Mono("oxirgi klasslar      : ${stats.lastLabels.ifEmpty { "-" }}")
            Mono("qora kadr (SECURE)   : ${stats.secureFrames}")
            Mono("sessiya uzilishi     : ${stats.sessionLostCount}")
            Mono("capture              : ${stats.captureSize}")
            Mono("faol ilova           : ${stats.activePackage ?: "-"}")

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    ProtectionService.resetStats(context)
                }) { Text("Tozalash", fontSize = 12.sp) }

                Button(onClick = {
                    ProtectionService.resetStats(context)
                    context.startActivity(
                        Intent(context, TestPatternActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("F0 test namunasi", fontSize = 12.sp) }
            }
            Text(
                "Test namunasi statik. Uni ochib 10 s kuting, keyin orqaga qayting va " +
                    "miltillash sonini tekshiring. Bu TZ FR-105 ning to'g'ridan-to'g'ri o'lchovi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Mono(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}

@Composable
private fun SettingsCard(settings: AppSettings, settingsRepo: SettingsRepository) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var engine by remember(settings) { mutableStateOf(settings.detectorEngine) }
    var sensitivity by remember(settings) { mutableStateOf(settings.sensitivity) }
    var policy by remember(settings) { mutableStateOf(settings.releasePolicy) }
    var style by remember(settings) { mutableStateOf(settings.blurStyle) }
    var intensity by remember(settings) { mutableStateOf(settings.blurIntensity.toFloat()) }
    var scrollShield by remember(settings) { mutableStateOf(settings.scrollShield) }

    fun save(block: suspend () -> Unit) {
        activity?.lifecycleScope?.launch { runCatching { block() } }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sozlamalar", style = MaterialTheme.typography.titleMedium)

            Text("Detektor", style = MaterialTheme.typography.labelMedium)
            ChipRow(listOf("NUDENET", "HEURISTIC"), engine) {
                engine = it
                save { settingsRepo.setDetectorEngine(it) }
            }
            Text(
                if (engine == "NUDENET")
                    "NudeNet v3 (YOLOv8n, AGPL-3.0) — haqiqiy model."
                else
                    "F0 soxta detektori: teri rangi + tekstura. NSFW ni aniqlamaydi, " +
                        "faqat taqqoslash uchun qoldirilgan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Sezgirlik", style = MaterialTheme.typography.labelMedium)
            ChipRow(listOf("LOW", "MEDIUM", "STRICT"), sensitivity) {
                sensitivity = it
                save { settingsRepo.setSensitivity(it) }
            }
            Text(
                when (sensitivity) {
                    "LOW" -> "Faqat aniq yalang'ochlik."
                    "STRICT" -> "Kiyim ostidan bilinadigan qismlar ham. Ko'p xato beradi."
                    else -> "Yalang'ochlik + ochiq qorin, yalang'och erkak ko'kragi."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "Mask'ni bo'shatish siyosati (ADR-003)",
                style = MaterialTheme.typography.labelMedium,
            )
            ChipRow(listOf("PROBE", "TIMEOUT_ONLY", "MOTION_ONLY"), policy) {
                policy = it
                save { settingsRepo.setReleasePolicy(it) }
            }
            Text(
                when (policy) {
                    "PROBE" -> "Timeout tugagach overlay ~120 ms yashiriladi va ostiga qaraladi. Aniq, lekin kontent ko'rinib qolishi mumkin."
                    "TIMEOUT_ONLY" -> "Timeout tugagach tekshirmasdan ochiladi. Sinov oynasi yo'q, lekin kontent hali joyida bo'lsa ochilib ketadi."
                    else -> "Faqat harakat (scroll / ilova almashish) mask'ni bo'shatadi. Eng xavfsiz, lekin statik ekranda mask qolib ketishi mumkin."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Blur uslubi", style = MaterialTheme.typography.labelMedium)
            ChipRow(listOf("GAUSSIAN", "PIXELATE", "SOLID"), style) {
                style = it
                save { settingsRepo.setBlurStyle(it) }
            }

            Text("Blur kuchi: ${intensity.toInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = intensity,
                onValueChange = { intensity = it },
                onValueChangeFinished = { save { settingsRepo.setBlurIntensity(intensity.toInt()) } },
                valueRange = 10f..100f,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Scroll Shield (FR-108)", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = scrollShield,
                    onCheckedChange = {
                        scrollShield = it
                        save { settingsRepo.setScrollShield(it) }
                    },
                )
            }
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
private fun OemCard() {
    val hint = remember { Oem.hintFor(Build.MANUFACTURER) } ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${Build.MANUFACTURER} uchun (TZ FR-003)", style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Ruxsatlar holati. TZ FR-002. */
object Permissions {
    data class State(
        val overlay: Boolean,
        val usageStats: Boolean,
        val notifications: Boolean,
        val batteryExempt: Boolean,
    ) {
        /** Batareya ozodligi majburiy emas — usiz ham ishlaydi, faqat OEM o'ldirishi mumkin. */
        val allGranted: Boolean get() = overlay && usageStats
    }

    fun check(context: Context): State {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        return State(
            overlay = Settings.canDrawOverlays(context),
            usageStats = ActivePackageMonitor(context).hasPermission(),
            notifications = nm.areNotificationsEnabled(),
            batteryExempt = pm.isIgnoringBatteryOptimizations(context.packageName),
        )
    }
}

/** TZ FR-003 / C-12: OEM battery killer'lari uchun yo'riqnoma. */
object Oem {
    fun hintFor(manufacturer: String): String? = when (manufacturer.lowercase()) {
        "xiaomi", "redmi", "poco" ->
            "Sozlamalar → Ilovalar → HaramHide → Batareyani tejash: «Cheklov yo'q». " +
                "Shuningdek «Avtomatik ishga tushirish» (Autostart) ni yoqing va " +
                "«Boshqa ilova ustida ko'rsatish» ruxsatini alohida bering."
        "huawei", "honor" ->
            "Telefon menejeri → Ishga tushirish → HaramHide → qo'lda boshqarish: " +
                "«Avtomatik ishga tushirish», «Fonda ishlash» — ikkalasini yoqing."
        "oppo", "realme", "oneplus" ->
            "Sozlamalar → Batareya → Energiya tejash → HaramHide → «Cheklov yo'q». " +
                "«Fon holatida ishlashga ruxsat» ni ham yoqing."
        "vivo", "iqoo" ->
            "iManager → Ilovalar menejeri → Avtomatik ishga tushirish → HaramHide ni yoqing. " +
                "Yuqori fon quvvat sarfiga ham ruxsat bering."
        "samsung" ->
            "Sozlamalar → Batareya → Fon foydalanish chegaralari → HaramHide " +
                "«Uxlaydigan ilovalar» ro'yxatida BO'LMASLIGI kerak."
        else -> null
    }
}
