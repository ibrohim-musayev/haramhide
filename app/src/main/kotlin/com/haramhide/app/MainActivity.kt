package com.haramhide.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.haramhide.app.ui.AppPickerScreen
import com.haramhide.app.ui.LogScreen
import com.haramhide.app.ui.MainScreen
import com.haramhide.app.ui.OnboardingScreen
import com.haramhide.app.ui.checkPermissions
import com.haramhide.core.data.AppSettings
import com.haramhide.core.data.DailyStatsRepository
import com.haramhide.core.data.DayStats
import com.haramhide.core.data.DetectionLog
import com.haramhide.core.data.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Yagona aktiviti. Navigatsiya oddiy holat bilan — kutubxona qo'shmaslik uchun
 * (F-Droid uchun bog'liqliklar qancha kam bo'lsa shuncha yaxshi).
 */
class MainActivity : ComponentActivity() {

    private enum class Screen { MAIN, APPS, LOG }

    private lateinit var repo: SettingsRepository
    private lateinit var dailyRepo: DailyStatsRepository
    private lateinit var detectionLog: DetectionLog

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SettingsRepository(applicationContext)
        dailyRepo = DailyStatsRepository(applicationContext)
        detectionLog = DetectionLog(applicationContext)
        Notifications.ensureChannels(this)

        setContent {
            MaterialTheme {
                Scaffold { padding ->
                    Root(Modifier.padding(padding))
                }
            }
        }
    }

    @Composable
    private fun Root(modifier: Modifier) {
        val context = LocalContext.current
        val settings by repo.settings.collectAsState(initial = null)
        val status by ProtectionState.status.collectAsState()
        val stats by ProtectionState.stats.collectAsState()
        val daily by dailyRepo.today.collectAsState(initial = DayStats())
        var screen by remember { mutableStateOf(Screen.MAIN) }

        // Ruxsatlar tizim ekranida beriladi — qaytgach qayta tekshiramiz
        var refreshKey by remember { mutableIntStateOf(0) }
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refreshKey++
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
        val perms = remember(refreshKey) { checkPermissions(context) }

        val s: AppSettings = settings ?: return

        if (!s.onboardingDone) {
            OnboardingScreen(onDone = { save { repo.setOnboardingDone(true) } })
            return
        }

        when (screen) {
            Screen.LOG -> LogScreen(
                log = detectionLog,
                onBack = { screen = Screen.MAIN },
            )

            Screen.APPS -> AppPickerScreen(
                selected = s.protectedPackages,
                onSave = {
                    save { repo.requestPackages(it) }
                    screen = Screen.MAIN
                },
                onBack = { screen = Screen.MAIN },
            )

            Screen.MAIN -> MainScreen(
                settings = s,
                status = status,
                stats = stats,
                daily = daily,
                perms = perms,
                modifier = modifier,
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onOpenApps = { screen = Screen.APPS },
                onSetSensitivity = { save { repo.requestSensitivity(it) } },
                onSetEngine = { save { repo.requestEngine(it) } },
                onSetBlurStyle = { save { repo.setBlurStyle(it) } },
                onSetBlurIntensity = { save { repo.setBlurIntensity(it) } },
                onSetScrollShield = { save { repo.setScrollShield(it) } },
                onSetMaleChest = { save { repo.requestBlurMaleChest(it) } },
                onSetUnblurLimit = { save { repo.setUnblurLimit(it) } },
                onSetDebugOverlay = { save { repo.setDebugOverlay(it) } },
                onCancelPending = { save { repo.cancelPending() } },
                onOpenLog = { screen = Screen.LOG },
            )
        }
    }

    private fun save(block: suspend () -> Unit) {
        lifecycleScope.launch { runCatching { block() } }
    }
}
