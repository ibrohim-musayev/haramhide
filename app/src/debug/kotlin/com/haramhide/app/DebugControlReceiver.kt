package com.haramhide.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.haramhide.core.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * **Faqat debug build'da.** F0 tajribalarini adb orqali boshqarish.
 *
 * ```
 * adb shell am broadcast -a com.haramhide.app.debug.CONTROL \
 *   -n com.haramhide.app.debug/com.haramhide.app.DebugControlReceiver \
 *   --es policy MOTION_ONLY --ez reset true --ez pattern true
 * ```
 *
 * Nega kerak: ADR-003 uchta siyosatni taqqoslashni talab qiladi. Har safar
 * UI'da chip bosib, ekranni aylantirib yurish o'lchovni takrorlanmaydigan
 * qiladi. Bu qabul qiluvchi tajribani bir buyruqqa tushiradi.
 */
class DebugControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        val repo = SettingsRepository(app)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                intent.getStringExtra("tier")?.let {
                    repo.setForcedTier(it); Log.i(TAG, "forcedTier=$it")
                }
                intent.getStringExtra("engine")?.let {
                    repo.setDetectorEngine(it); Log.i(TAG, "engine=$it")
                }
                intent.getStringExtra("policy")?.let {
                    repo.setReleasePolicy(it); Log.i(TAG, "policy=$it")
                }
                intent.getStringExtra("sensitivity")?.let {
                    repo.setSensitivity(it); Log.i(TAG, "sensitivity=$it")
                }
                intent.getStringExtra("blur")?.let {
                    repo.setBlurStyle(it); Log.i(TAG, "blur=$it")
                }
                intent.getIntExtra("intensity", -1).takeIf { it > 0 }?.let {
                    repo.setBlurIntensity(it); Log.i(TAG, "intensity=$it")
                }
                if (intent.hasExtra("scrollShield")) {
                    repo.setScrollShield(intent.getBooleanExtra("scrollShield", true))
                }
                if (intent.hasExtra("debugOverlay")) {
                    repo.setDebugOverlay(intent.getBooleanExtra("debugOverlay", true))
                }
                if (intent.getBooleanExtra("bench", false)) {
                    ProtectionService.benchmark(app)
                }
                if (intent.getBooleanExtra("reset", false)) {
                    ProtectionService.resetStats(app)
                    Log.i(TAG, "o'lchovlar tozalandi")
                }
                if (intent.getBooleanExtra("pattern", false)) {
                    app.startActivity(
                        Intent(app, TestPatternActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    Log.i(TAG, "test namunasi ochildi")
                }
                // Modelni haqiqiy rasmda sinash. Rasm ilovaning o'z files/
                // katalogidan o'qiladi va repozitoriyaga qo'shilmaydi.
                intent.getStringExtra("photo")?.let { name ->
                    app.startActivity(
                        Intent(app, TestPatternActivity::class.java)
                            .putExtra(TestPatternActivity.EXTRA_PHOTO, name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    Log.i(TAG, "test rasmi ochildi: $name")
                }
                if (intent.getBooleanExtra("home", false)) {
                    app.startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "boshqaruv xatosi", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "HaramHideDebug"
    }
}
