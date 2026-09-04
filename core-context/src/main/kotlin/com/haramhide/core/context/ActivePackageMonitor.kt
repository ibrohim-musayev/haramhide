package com.haramhide.core.context

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log

/**
 * Faol (oldinda turgan) ilova paketini aniqlaydi.
 *
 * TZ FR-106 / C-05: **birlamchi mexanizm — [UsageStatsManager]**, AccessibilityService emas.
 * Sabab: Google Play siyosati accessibility API dan faqat haqiqiy yordamchi
 * texnologiyalarga ruxsat beradi, Android 17 Advanced Protection esa uni
 * butunlay bloklaydi. AccessibilityService keyinroq **ixtiyoriy** modul
 * sifatida qo'shiladi (scroll hodisalari uchun), lekin usiz ham ilova to'liq ishlaydi.
 */
class ActivePackageMonitor(private val context: Context) {

    private val usm =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private var lastPackage: String? = null
    private var lastQueryMs = 0L

    /** Ruxsat berilganmi (`PACKAGE_USAGE_STATS` — Settings orqali beriladi). */
    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        // unsafeCheckOpNoThrow faqat API 29+ da bor; minSdk 26 da eski usul kerak.
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Joriy faol paket. Kesh bilan — [minIntervalMs] dan tez-tez so'ralmaydi.
     * TZ FR-106 qabul mezoni: ilova almashganda <= 500 ms ichida aniqlanadi.
     */
    fun current(nowMs: Long, minIntervalMs: Long = 400L): String? {
        if (nowMs - lastQueryMs < minIntervalMs) return lastPackage
        lastQueryMs = nowMs
        val pkg = query()
        if (pkg != null) lastPackage = pkg
        return lastPackage
    }

    private fun query(): String? {
        val end = System.currentTimeMillis()
        val begin = end - LOOKBACK_MS
        return try {
            val events = usm.queryEvents(begin, end)
            val event = UsageEvents.Event()
            var latest: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    latest = event.packageName
                }
            }
            latest
        } catch (t: Throwable) {
            Log.w(TAG, "UsageStats so'rovida xato: $t")
            null
        }
    }

    fun reset() {
        lastPackage = null
        lastQueryMs = 0L
    }

    companion object {
        private const val TAG = "ActivePackageMonitor"
        private const val LOOKBACK_MS = 10_000L
    }
}
