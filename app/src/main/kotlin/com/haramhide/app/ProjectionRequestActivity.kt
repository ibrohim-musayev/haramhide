package com.haramhide.app

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Ko'rinmas aktiviti — yagona vazifasi MediaProjection roziligini so'rash.
 *
 * TZ C-01: rozilik **har sessiyada** kerak va uni faqat Activity so'ray oladi.
 * Shuning uchun bildirishnomadan ham, asosiy ekrandan ham shu aktiviti chaqiriladi —
 * foydalanuvchi uchun bu "bitta bosish" bo'lib qoladi (FR-103 qabul mezoni).
 */
class ProjectionRequestActivity : ComponentActivity() {

    private val consentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                Log.i(TAG, "Rozilik olindi, sessiya boshlanmoqda")
                ProtectionService.startSession(this, result.resultCode, result.data!!)
            } else {
                Log.i(TAG, "Foydalanuvchi rozilik bermadi")
                Toast.makeText(this, R.string.notif_off_text, Toast.LENGTH_SHORT).show()
            }
            finish()
            overridePendingTransition(0, 0)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        Notifications.cancelAction(this)

        val mpm = getSystemService(MediaProjectionManager::class.java)
        if (mpm == null) {
            Log.e(TAG, "MediaProjectionManager mavjud emas")
            finish()
            return
        }
        try {
            consentLauncher.launch(buildCaptureIntent(mpm))
        } catch (t: Throwable) {
            Log.e(TAG, "Rozilik dialogini ochib bo'lmadi", t)
            finish()
        }
    }

    /**
     * **Butun ekranni yozib olishni MAJBURLAYDI.**
     *
     * Android 14+ da rozilik dialogi default holda «Share one app» ni taklif
     * qiladi. Agar foydalanuvchi shuni tanlasa, tizim `RECORD_CONTENT_TASK`
     * rejimida faqat bitta ilova oynasini uzatadi — bizning ilovamiz esa
     * jimgina **butunlay foydasiz** bo'lib qoladi: boshqa ilovalardan kadr
     * umuman kelmaydi, xato ham chiqmaydi.
     *
     * Bu F0 prototipida amalda kuzatildi (`dumpsys media_projection` →
     * `contentToRecord = RECORD_CONTENT_TASK`). Kontent filtri uchun bu holat
     * ma'nosiz, shuning uchun API 34+ da `createConfigForDefaultDisplay()`
     * bilan tanlov imkoniyati butunlay olib tashlanadi.
     *
     * API 33 va undan pastda bunday config yo'q — u yerda foydalanuvchiga
     * onboarding'da tushuntirish va [ProtectionService] dagi aniqlash qoladi.
     */
    private fun buildCaptureIntent(mpm: MediaProjectionManager) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mpm.createScreenCaptureIntent()
        }

    private companion object {
        const val TAG = "ProjectionRequest"
    }
}
