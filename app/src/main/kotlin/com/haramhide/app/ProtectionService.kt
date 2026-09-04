package com.haramhide.app

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import com.haramhide.core.capture.BlackFrameDetector
import com.haramhide.core.capture.CaptureConfig
import com.haramhide.core.capture.Frame
import com.haramhide.core.capture.FrameSignals
import com.haramhide.core.capture.ScreenCapturer
import com.haramhide.core.capture.SecurePolicy
import com.haramhide.core.context.ActivePackageMonitor
import com.haramhide.core.data.AppSettings
import com.haramhide.core.data.SettingsRepository
import com.haramhide.core.detect.DetectorConfig
import com.haramhide.core.detect.HeuristicDetector
import com.haramhide.core.detect.NudeNetDetector
import com.haramhide.core.detect.SkinPrescreen
import com.haramhide.core.detect.Sensitivity
import com.haramhide.core.detect.TwoStageDetector
import com.haramhide.core.overlay.BlurSpec
import com.haramhide.core.overlay.BlurStyle
import com.haramhide.core.overlay.FrameContext
import com.haramhide.core.overlay.MaskConfig
import com.haramhide.core.overlay.MaskStateMachine
import com.haramhide.core.overlay.OverlayController
import com.haramhide.core.overlay.ReleasePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Himoya xizmati — TZ 4.2 pipeline'ini boshqaradi.
 *
 * ### Ikki xil hayot davri
 * Bu klass ikkita mustaqil holatni ajratadi va bu **ataylab** shunday:
 *
 *  - **Xizmat hayoti** — foydalanuvchi boshqaradi, uzoq.
 *  - **MediaProjection sessiyasi** — tizim boshqaradi, qisqa va tez-tez uziladi
 *    (TZ C-02: ekran qulflansa avtomatik to'xtaydi).
 *
 * Sessiya uzilganda xizmat **o'lmaydi**. U tirik qoladi, qalqonni yoqadi va
 * `ACTION_USER_PRESENT` ni kutadi — qulf ochilishi bilan bir bosishlik tiklash
 * so'rovini chiqaradi (TZ FR-103). Agar xizmat ham o'lsa, bu receiver'ni
 * ro'yxatdan o'tkazadigan hech kim qolmaydi (USER_PRESENT manifestdan
 * qabul qilinmaydi) va foydalanuvchi ilovani qo'lda ochishga majbur bo'lardi.
 */
class ProtectionService : Service() {

    private lateinit var captureThread: HandlerThread
    private lateinit var captureHandler: Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var overlay: OverlayController
    private lateinit var activePackages: ActivePackageMonitor
    private lateinit var settingsRepo: SettingsRepository

    private val signals = FrameSignals()
    private val stateMachine = MaskStateMachine()

    // F0 soxta detektori — taqqoslash uchun saqlanadi (bir klass ikkala bosqichni ham beradi)
    private val heuristic = HeuristicDetector()

    // F1: arzon darvoza + haqiqiy model
    private val skinPrescreen = SkinPrescreen()
    private var nudeNet: NudeNetDetector? = null

    @Volatile private var detector = TwoStageDetector(heuristic, heuristic)
    @Volatile private var currentEngine: String? = null
    @Volatile private var engineLabel: String = "HEURISTIC"

    private var projection: MediaProjection? = null
    private var capturer: ScreenCapturer? = null
    private var isForeground = false

    @Volatile private var settings = AppSettings()
    @Volatile private var lastPackage: String? = null
    @Volatile private var sessionLostCount = 0
    @Volatile private var secureFrames = 0L
    @Volatile private var framesProcessed = 0L
    @Volatile private var processNsTotal = 0L
    @Volatile private var maxProcessMs = 0L
    @Volatile private var lastStatsPublishMs = 0L
    @Volatile private var lastFpsWindowStart = 0L
    @Volatile private var framesInWindow = 0
    @Volatile private var fps = 0f
    @Volatile private var captureSize = "-"
    @Volatile private var lastLabels: String = ""
    @Volatile private var scrollingState = false
    private var scrollHighFrames = 0
    private var lastLogMs = 0L

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        overlay = OverlayController(this)
        activePackages = ActivePackageMonitor(this)
        settingsRepo = SettingsRepository(applicationContext)

        captureThread = HandlerThread("haramhide-capture", android.os.Process.THREAD_PRIORITY_DISPLAY)
        captureThread.start()
        captureHandler = Handler(captureThread.looper)

        settingsRepo.settings
            .onEach { s ->
                settings = s
                applySettings(s)
            }
            .launchIn(scope)

        registerReceiver(
            userPresentReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            receiverFlags(),
        )

        displayManager().registerDisplayListener(displayListener, mainHandler)
        Log.i(TAG, "Xizmat yaratildi")
    }

    /**
     * MUHIM: `startForeground(type = mediaProjection)` ni **faqat** rozilik
     * bo'lganda chaqirish mumkin.
     *
     * Avval bu metod har qanday amal uchun uni chaqirardi. Natijada
     * bildirishnomadagi «To'xtatish» tugmasi (yoki har qanday boshqa amal)
     * sessiya allaqachon tugagan holatda ilovani **qulatardi**:
     *
     * ```
     * SecurityException: Starting FGS with type mediaProjection ...
     *   requires ... Media projection screen capture permission
     * ```
     *
     * Android 14+ da tizim FGS turini rozilik tokeni bilan tekshiradi.
     * Shuning uchun endi faqat [ACTION_START_SESSION] FGS ni ko'taradi —
     * u yerda rozilik hozirgina olingan. Qolgan amallar mavjud
     * bildirishnomani yangilaydi, xolos.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SESSION -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (data != null) {
                    // FGS bildirishnomasi MediaProjection olinishidan OLDIN
                    // ko'rsatilishi shart (TZ C-08).
                    promoteToForeground(running = false)
                    startSessionInternal(code, data)
                } else {
                    stopSelfSafely()
                }
            }

            ACTION_STOP -> {
                scope.launchSet { settingsRepo.setProtectionDesired(false) }
                stopSelfSafely()
            }

            null -> {
                // Tizim xizmatni qayta tikladi (START_STICKY). Sessiya yo'q —
                // FGS ni ko'tara olmaymiz, shuning uchun shunchaki to'xtaymiz.
                if (!isForeground) stopSelf()
            }

            ACTION_SHIELD_CHECK -> updateShieldForCurrentApp()

            ACTION_BENCHMARK -> captureHandler.post {
                val nn = nudeNet
                if (nn == null) Log.w(TAG, "NudeNet yuklanmagan")
                else { nn.benchmarkNow(5); nn.sweep() }
            }

            ACTION_RESET_STATS -> {
                stateMachine.resetStats()
                nudeNet?.resetStats()
                framesProcessed = 0
                processNsTotal = 0
                maxProcessMs = 0
                secureFrames = 0
                publishStats()
                Log.i(TAG, "O'lchovlar tozalandi")
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // TZ FR-107: rotatsiya / split-screen / foldable
        val d = currentDisplay()
        capturer?.resize(d.width, d.height, d.dpi, d.rotation)
        captureSize = "${d.width}x${d.height}/${d.rotation}"
        stateMachine.reset()
        signals.reset()
    }

    override fun onDestroy() {
        Log.i(TAG, "Xizmat to'xtatilmoqda")
        stopSessionInternal(lost = false)
        runCatching { unregisterReceiver(userPresentReceiver) }
        runCatching { displayManager().unregisterDisplayListener(displayListener) }
        mainHandler.removeCallbacksAndMessages(null)
        overlay.detach()
        detector.close()
        nudeNet?.close()
        nudeNet = null
        scope.cancel()
        captureThread.quitSafely()
        ProtectionState.setStatus(ProtectionState.Status.STOPPED)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------ session

    private fun startSessionInternal(resultCode: Int, data: Intent) {
        stopSessionInternal(lost = false)

        if (!overlay.canDrawOverlays()) {
            Log.e(TAG, "Overlay ruxsati yo'q — sessiya boshlanmaydi")
            stopSelfSafely()
            return
        }
        overlay.attach()

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = try {
            mpm.getMediaProjection(resultCode, data)
        } catch (t: Throwable) {
            // TZ C-01: tokenni ikkinchi marta ishlatish SecurityException beradi
            Log.e(TAG, "MediaProjection olinmadi", t)
            null
        }
        if (mp == null) {
            onSessionLost()
            return
        }
        projection = mp

        val d = currentDisplay()
        captureSize = "${d.width}x${d.height}/${d.rotation}"
        signals.reset()
        stateMachine.reset()
        lastPackage = null

        val cap = ScreenCapturer(
            projection = mp,
            config = CaptureConfig.TIER_B,
            handler = captureHandler,
            onProjectionStopped = { mainHandler.post { onSessionLost() } },
            onFrame = ::onFrame,
        )
        capturer = cap
        cap.start(d.width, d.height, d.dpi, d.rotation)

        overlay.setShield(false)
        Notifications.cancelAction(this)
        updateNotification(running = true)
        ProtectionState.setStatus(ProtectionState.Status.RUNNING)
        scope.launchSet { settingsRepo.setProtectionDesired(true) }
        Log.i(TAG, "Sessiya boshlandi: $captureSize")
    }

    private fun stopSessionInternal(lost: Boolean) {
        capturer?.stop()
        capturer = null
        runCatching { projection?.stop() }
        projection = null
        stateMachine.reset()
        signals.reset()
        if (!lost) overlay.clear()
    }

    /**
     * Sessiya tizim tomonidan uzildi. TZ C-02 / FR-103.
     *
     * Eng ehtimolli sabab — ekran qulflandi. Bu kuniga o'nlab marta bo'ladi va
     * hujjatda R-01 sifatida "mahsulotni o'ldiradigan UX muammosi" deb belgilangan.
     * Shuning uchun bu yerdagi har bir qadam bir bosishga tushirilgan.
     */
    private fun onSessionLost() {
        if (projection == null && capturer == null &&
            ProtectionState.status.value == ProtectionState.Status.SESSION_LOST
        ) return

        sessionLostCount++
        stopSessionInternal(lost = true)
        ProtectionState.setStatus(ProtectionState.Status.SESSION_LOST)
        updateNotification(running = false)
        updateShieldForCurrentApp()
        Log.w(TAG, "Sessiya uzildi (jami=$sessionLostCount)")
    }

    private fun stopSelfSafely() {
        stopSessionInternal(lost = false)
        overlay.setShield(false)
        overlay.detach()
        Notifications.cancelAction(this)
        ProtectionState.setStatus(ProtectionState.Status.STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
        stopSelf()
    }

    // ------------------------------------------------------------------ pipeline

    /** Capture oqimida chaqiriladi. TZ 4.2 dagi butun zanjir shu yerda. */
    private fun onFrame(frame: Frame) {
        val t0 = SystemClock.elapsedRealtimeNanos()
        val now = frame.timestampMs
        val s = settings

        signals.update(frame.analysis)

        // --- C-03 / FR-104: qora kadr = FLAG_SECURE oynasi
        if (BlackFrameDetector.isBlackFrame(frame.analysis)) {
            secureFrames++
            val failClosed = s.securePolicy == SecurePolicy.FAIL_CLOSED.name
            overlay.setShield(failClosed, if (failClosed) getString(R.string.shield_text) else null)
            if (stateMachine.masks().isNotEmpty()) stateMachine.reset()
            signals.commit()
            finishFrame(t0, now)
            return
        }
        overlay.setShield(false)

        // --- Gate 2: faol ilova filtri (TZ FR-204)
        val pkg = activePackages.current(now)
        val packageChanged = pkg != null && pkg != lastPackage
        if (packageChanged) lastPackage = pkg

        val inScope = s.protectedPackages.isEmpty() || (pkg != null && pkg in s.protectedPackages)
        if (!inScope) {
            if (stateMachine.masks().isNotEmpty()) stateMachine.reset()
            overlay.render(frame, emptyList(), blurSpec(s), false, debugText(s, 0f, "scope tashqarisi"))
            signals.commit()
            finishFrame(t0, now)
            return
        }

        // --- Gate 1: frame-diff
        val globalDelta = signals.globalDelta()
        val scrolling = updateScrolling(globalDelta)
        val hasMasks = stateMachine.masks().isNotEmpty()
        val runDetector = globalDelta >= GATE1_DELTA_THRESHOLD || hasMasks

        val sensitivity = parseSensitivity(s.sensitivity)
        val detections = if (runDetector) detector.run(frame, sensitivity) else emptyList()
        if (detections.isNotEmpty()) {
            lastLabels = detections.joinToString(",") { it.label }.take(80)
        }

        // --- Mask State Machine (TZ FR-105)
        val masks = stateMachine.update(
            FrameContext(
                nowMs = now,
                detections = detections,
                packageChanged = packageChanged,
                globalDelta = globalDelta,
                scrolling = scrolling,
                ringDelta = { d -> signals.ringDelta(d.left, d.top, d.right, d.bottom) },
            )
        )

        // --- Render
        overlay.render(
            frame = frame,
            masks = masks,
            spec = blurSpec(s),
            scrollShield = s.scrollShield && scrolling,
            debugText = debugText(s, globalDelta.toFloat(), pkg ?: "?"),
        )

        signals.commit()
        finishFrame(t0, now)
    }

    /**
     * Scroll aniqlash — Schmitt trigger.
     *
     * Bitta chegara yetarli emas edi: overlay va debug qatlami kadrga tushib,
     * o'zi delta beradi va Scroll Shield o'z-o'zini ushlab turadi. Bu C-04
     * halqasining Scroll Shield'dagi ko'rinishi. Ikki chegara + ketma-ket
     * kadr talabi buni uzadi.
     */
    private fun updateScrolling(delta: Int): Boolean {
        if (scrollingState) {
            if (delta < SCROLL_DELTA_EXIT) {
                scrollingState = false
                scrollHighFrames = 0
            }
        } else {
            if (delta > SCROLL_DELTA_ENTER) {
                scrollHighFrames++
                if (scrollHighFrames >= SCROLL_ENTER_FRAMES) scrollingState = true
            } else {
                scrollHighFrames = 0
            }
        }
        return scrollingState
    }

    private fun finishFrame(t0: Long, nowMs: Long) {
        val ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
        framesProcessed++
        processNsTotal += SystemClock.elapsedRealtimeNanos() - t0
        if (ms > maxProcessMs) maxProcessMs = ms

        framesInWindow++
        if (lastFpsWindowStart == 0L) lastFpsWindowStart = nowMs
        val windowMs = nowMs - lastFpsWindowStart
        if (windowMs >= 1000) {
            fps = framesInWindow * 1000f / windowMs
            framesInWindow = 0
            lastFpsWindowStart = nowMs
        }

        if (nowMs - lastStatsPublishMs >= STATS_INTERVAL_MS) {
            lastStatsPublishMs = nowMs
            publishStats()
        }
        if (nowMs - lastLogMs >= METRIC_LOG_INTERVAL_MS) {
            lastLogMs = nowMs
            logMetrics()
        }
    }

    private fun publishStats() {
        val avgMs = if (framesProcessed == 0L) 0f
        else (processNsTotal.toDouble() / framesProcessed / 1_000_000.0).toFloat()
        ProtectionState.setStats(
            ProtectionState.Stats(
                framesProcessed = framesProcessed,
                fps = fps,
                avgProcessMs = avgMs,
                maxProcessMs = maxProcessMs,
                masksActive = stateMachine.masks().count { it.isVisible },
                masksCreated = stateMachine.totalMasksCreated,
                flickerEvents = stateMachine.flickerEvents,
                probes = stateMachine.totalProbes,
                probesConfirmed = stateMachine.probesConfirmed,
                stageAScore = detector.lastStageAScore,
                stageBRatio = detector.stageBRatio(),
                secureFrames = secureFrames,
                sessionLostCount = sessionLostCount,
                activePackage = lastPackage,
                captureSize = captureSize,
                edgeAverage = heuristic.lastEdgeAverage,
                engine = engineLabel,
                inferenceMs = nudeNet?.lastInferenceMs ?: 0L,
                lastLabels = lastLabels,
            )
        )
    }

    /** F0 o'lchovlarini logcat'ga yozadi — `adb logcat -s HaramHideMetrics`. */
    private fun logMetrics() {
        val st = stateMachine
        Log.i(
            METRIC_TAG,
            "engine=%s inf=%dms(pre=%d run=%d post=%d) runAvg=%d/%d fps=%.1f avg=%.1fms max=%dms mask=%d/%d flicker=%d probe=%d/%d A=%.2f teri=%.2f stageB=%.0f%% labels=[%s] pkg=%s"
                .format(
                    engineLabel, nudeNet?.lastInferenceMs ?: 0L,
                    nudeNet?.lastPreprocessMs ?: 0L, nudeNet?.lastRunMs ?: 0L,
                    nudeNet?.lastPostprocessMs ?: 0L,
                    nudeNet?.runAvgMs ?: 0L, nudeNet?.runSamples ?: 0L,
                    fps, avgMsFast(), maxProcessMs,
                    st.masks().count { it.isVisible }, st.totalMasksCreated,
                    st.flickerEvents, st.probesConfirmed, st.totalProbes,
                    detector.lastStageAScore, skinPrescreen.lastPeakRatio,
                    detector.stageBRatio() * 100, lastLabels, lastPackage ?: "-",
                )
        )
    }

    private fun debugText(s: AppSettings, delta: Float, pkg: String): String? {
        if (!s.debugOverlay) return null
        val st = stateMachine
        return buildString {
            append("HaramHide F0  ").append(captureSize).append('\n')
            append("fps ").append(String.format("%.1f", fps))
            append("  ish ").append(String.format("%.1f", avgMsFast())).append("ms")
            append("  d ").append(delta.toInt()).append('\n')
            append("mask ").append(st.masks().count { it.isVisible })
            append("/").append(st.totalMasksCreated)
            append("  MILTILLASH ").append(st.flickerEvents).append('\n')
            append("probe ").append(st.probesConfirmed).append("/").append(st.totalProbes)
            append("  A ").append(String.format("%.2f", detector.lastStageAScore))
            append("  teri ").append(String.format("%.2f", skinPrescreen.lastPeakRatio)).append('\n')
            append(engineLabel)
            nudeNet?.let { append("  ").append(it.lastInferenceMs).append("ms") }
            append('\n')
            if (lastLabels.isNotEmpty()) append(lastLabels).append('\n')
            append(pkg)
        }
    }

    private fun avgMsFast(): Float =
        if (framesProcessed == 0L) 0f
        else (processNsTotal.toDouble() / framesProcessed / 1_000_000.0).toFloat()

    // ------------------------------------------------------------------ settings

    private fun applySettings(s: AppSettings) {
        stateMachine.updateConfig(
            MaskConfig(releasePolicy = parseReleasePolicy(s.releasePolicy))
        )
        applyEngine(s.detectorEngine)
    }

    /**
     * Detektor dvigatelini almashtiradi.
     *
     * Model 12 MB va uni yuklash sekin, shuning uchun qurish capture oqimida
     * bajariladi va natija bir marta keshlanadi. [TwoStageDetector.close]
     * bu yerda ATAYLAB chaqirilmaydi — u asosidagi sessiyani yopib yuborardi
     * va orqaga qaytishda modelni qaytadan yuklashga majbur qilardi.
     */
    private fun applyEngine(engine: String) {
        if (engine == currentEngine) return
        currentEngine = engine
        captureHandler.post {
            detector = if (engine == ENGINE_NUDENET) {
                val nn = nudeNet ?: runCatching {
                    // Capture Tier B da ishlaydi (CaptureConfig.TIER_B) — detektor ham shunga mos.
                    NudeNetDetector(applicationContext, DetectorConfig.TIER_B)
                }
                    .onFailure { Log.e(TAG, "NudeNet yuklanmadi", it) }
                    .getOrNull()
                if (nn != null && nn.isReady) {
                    nudeNet = nn
                    engineLabel = ENGINE_NUDENET
                    TwoStageDetector(skinPrescreen, nn)
                } else {
                    // Fail-safe: model yuklanmasa evristikaga qaytamiz, lekin
                    // buni foydalanuvchiga ko'rsatamiz — jimgina ishlamay
                    // qolish eng yomon holat (C-13 dan olingan saboq).
                    engineLabel = "NUDENET (xato: ${nn?.loadError ?: "yuklanmadi"})"
                    Log.e(TAG, "NudeNet tayyor emas — evristikaga qaytildi")
                    TwoStageDetector(heuristic, heuristic)
                }
            } else {
                engineLabel = ENGINE_HEURISTIC
                TwoStageDetector(heuristic, heuristic)
            }
            Log.i(TAG, "Detektor: $engineLabel obj=${System.identityHashCode(nudeNet)}")
        }
    }

    private fun blurSpec(s: AppSettings) = BlurSpec(
        style = runCatching { BlurStyle.valueOf(s.blurStyle) }.getOrDefault(BlurStyle.GAUSSIAN),
        intensity = s.blurIntensity,
    )

    private fun parseSensitivity(v: String) =
        runCatching { Sensitivity.valueOf(v) }.getOrDefault(Sensitivity.MEDIUM)

    private fun parseReleasePolicy(v: String) =
        runCatching { ReleasePolicy.valueOf(v) }.getOrDefault(ReleasePolicy.PROBE)

    // ------------------------------------------------------------------ helpers

    /** Bildirishnomani yangilaydi. FGS holatiga tegmaydi — xavfsiz. */
    private fun updateNotification(running: Boolean) {
        if (!isForeground) return
        getSystemService(NotificationManager::class.java)
            .notify(Notifications.ID_FOREGROUND, Notifications.foreground(this, running))
    }

    private fun promoteToForeground(running: Boolean) {
        val notification = Notifications.foreground(this, running)
        if (!isForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    Notifications.ID_FOREGROUND,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                )
            } else {
                startForeground(Notifications.ID_FOREGROUND, notification)
            }
            isForeground = true
        } else {
            getSystemService(NotificationManager::class.java)
                .notify(Notifications.ID_FOREGROUND, notification)
        }
    }

    /**
     * Himoya o'chiq bo'lsa-yu foydalanuvchi himoyalangan ilovani ochsa —
     * butun ekranni yopamiz (fail-closed). TZ FR-103.
     */
    private fun updateShieldForCurrentApp() {
        mainHandler.removeCallbacks(shieldWatcher)
        if (ProtectionState.status.value != ProtectionState.Status.SESSION_LOST) return
        if (!settings.shieldWhenOff) { overlay.setShield(false); return }

        overlay.attach()
        val pkg = activePackages.current(SystemClock.elapsedRealtime(), minIntervalMs = 0)
        val set = settings.protectedPackages
        val inScope = set.isNotEmpty() && pkg != null && pkg in set
        overlay.setShield(inScope, if (inScope) getString(R.string.shield_text) else null)
        mainHandler.postDelayed(shieldWatcher, SHIELD_POLL_MS)
    }

    private val shieldWatcher = Runnable { updateShieldForCurrentApp() }

    /**
     * `ACTION_USER_PRESENT` manifestdan qabul qilinmaydi (Android 8+ implicit
     * broadcast cheklovi) — shuning uchun u dinamik ro'yxatdan o'tadi va
     * xizmat sessiyasiz ham tirik turishi shart bo'ladi.
     */
    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "broadcast: ${intent.action} status=${ProtectionState.status.value} desired=${settings.protectionDesired}")
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> {
                    if (ProtectionState.status.value == ProtectionState.Status.RUNNING) return
                    if (!settings.protectionDesired) return
                    Log.i(TAG, "Qulf ochildi — tiklash so'rovi chiqarilmoqda")
                    getSystemService(NotificationManager::class.java).notify(
                        Notifications.ID_ACTION,
                        Notifications.actionRequired(
                            this@ProtectionService,
                            R.string.notif_resume_title,
                            R.string.notif_resume_text,
                            urgent = true,
                        ),
                    )
                    updateShieldForCurrentApp()
                }

                Intent.ACTION_SCREEN_OFF -> {
                    // Android 15 QPR1+ da projection shu yerda avtomatik to'xtaydi (TZ C-02).
                    Log.i(TAG, "Ekran o'chdi — sessiya uzilishi kutilmoqda")
                }
            }
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            val d = currentDisplay()
            val newSize = "${d.width}x${d.height}/${d.rotation}"
            if (newSize == captureSize) return
            captureSize = newSize
            capturer?.resize(d.width, d.height, d.dpi, d.rotation)
            stateMachine.reset()
            signals.reset()
            Log.i(TAG, "Displey o'zgardi: $newSize")
        }
    }

    private data class DisplayInfo(val width: Int, val height: Int, val dpi: Int, val rotation: Int)

    private fun displayManager() = getSystemService(DisplayManager::class.java)

    private fun currentDisplay(): DisplayInfo {
        val display = displayManager().getDisplay(Display.DEFAULT_DISPLAY)
        val rotation = display?.rotation ?: 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = getSystemService(WindowManager::class.java)
            val bounds = wm.maximumWindowMetrics.bounds
            DisplayInfo(bounds.width(), bounds.height(), resources.configuration.densityDpi, rotation)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display?.getRealMetrics(metrics)
            DisplayInfo(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, rotation)
        }
    }

    /**
     * `ACTION_USER_PRESENT` va `ACTION_SCREEN_OFF` — **himoyalangan (protected)
     * tizim broadcast'lari**: ularni faqat tizim yubora oladi.
     *
     * `RECEIVER_NOT_EXPORTED` bilan ro'yxatdan o'tkazilganda ular yetib
     * kelmadi (F0 da amalda kuzatildi: qulf ochilgach hech narsa bo'lmadi).
     * Himoyalangan broadcast uchun `RECEIVER_EXPORTED` xavfsiz — boshqa
     * ilova baribir bu action'ni yubora olmaydi.
     */
    private fun receiverFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_EXPORTED else 0

    private fun CoroutineScope.launchSet(block: suspend () -> Unit) {
        launch(Dispatchers.IO) { runCatching { block() } }
    }

    companion object {
        private const val TAG = "ProtectionService"

        const val ACTION_START_SESSION = "com.haramhide.app.action.START_SESSION"
        const val ACTION_STOP = "com.haramhide.app.action.STOP"
        const val ACTION_SHIELD_CHECK = "com.haramhide.app.action.SHIELD_CHECK"
        const val ACTION_RESET_STATS = "com.haramhide.app.action.RESET_STATS"
        const val ACTION_BENCHMARK = "com.haramhide.app.action.BENCHMARK"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        /** Shu qiymatdan past o'zgarishda detektor umuman ishga tushmaydi. */
        private const val GATE1_DELTA_THRESHOLD = 2

        /** Scroll holatiga KIRISH chegarasi (TZ FR-108). */
        private const val SCROLL_DELTA_ENTER = 22

        /** Scroll holatidan CHIQISH chegarasi — kirishdan past (gisterezis). */
        private const val SCROLL_DELTA_EXIT = 9

        /** Kirish uchun shuncha ketma-ket kadr talab qilinadi. */
        private const val SCROLL_ENTER_FRAMES = 2

        private const val STATS_INTERVAL_MS = 500L
        private const val METRIC_LOG_INTERVAL_MS = 1_000L
        private const val METRIC_TAG = "HaramHideMetrics"

        const val ENGINE_NUDENET = "NUDENET"
        const val ENGINE_HEURISTIC = "HEURISTIC"
        private const val SHIELD_POLL_MS = 1_000L

        fun startSession(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ProtectionService::class.java)
                .setAction(ACTION_START_SESSION)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            context.startForegroundService(intent)
        }

        fun benchmark(context: Context) {
            context.startService(
                Intent(context, ProtectionService::class.java).setAction(ACTION_BENCHMARK)
            )
        }

        fun resetStats(context: Context) {
            context.startService(
                Intent(context, ProtectionService::class.java).setAction(ACTION_RESET_STATS)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ProtectionService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
