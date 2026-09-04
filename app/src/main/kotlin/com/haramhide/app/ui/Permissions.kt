package com.haramhide.app.ui

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.haramhide.core.context.ActivePackageMonitor

/** Ruxsatlar holati. TZ FR-002. */
data class PermissionState(
    val overlay: Boolean,
    val usageStats: Boolean,
    val notifications: Boolean,
    val batteryExempt: Boolean,
) {
    /**
     * Batareya ozodligi majburiy emas — usiz ham ishlaydi, faqat ba'zi OEM'lar
     * xizmatni o'ldirishi mumkin (TZ C-12).
     */
    val allGranted: Boolean get() = overlay && usageStats
}

fun checkPermissions(context: Context): PermissionState {
    val nm = context.getSystemService(NotificationManager::class.java)
    val pm = context.getSystemService(PowerManager::class.java)
    return PermissionState(
        overlay = Settings.canDrawOverlays(context),
        usageStats = ActivePackageMonitor(context).hasPermission(),
        notifications = nm.areNotificationsEnabled(),
        batteryExempt = pm.isIgnoringBatteryOptimizations(context.packageName),
    )
}

/** TZ FR-003 / C-12: OEM battery killer'lari uchun yo'riqnoma. */
object Oem {
    fun hintFor(manufacturer: String = Build.MANUFACTURER): String? =
        when (manufacturer.lowercase()) {
            "xiaomi", "redmi", "poco" ->
                "Sozlamalar → Ilovalar → HaramHide → Batareyani tejash: «Cheklov yo'q». " +
                    "«Avtomatik ishga tushirish» (Autostart) ni yoqing va «Boshqa ilova " +
                    "ustida ko'rsatish» ruxsatini alohida bering."
            "huawei", "honor" ->
                "Telefon menejeri → Ishga tushirish → HaramHide → qo'lda boshqarish: " +
                    "«Avtomatik ishga tushirish» va «Fonda ishlash» — ikkalasini yoqing."
            "oppo", "realme", "oneplus" ->
                "Sozlamalar → Batareya → Energiya tejash → HaramHide → «Cheklov yo'q». " +
                    "«Fon holatida ishlashga ruxsat» ni ham yoqing."
            "vivo", "iqoo" ->
                "iManager → Ilovalar menejeri → Avtomatik ishga tushirish → HaramHide ni yoqing."
            "samsung" ->
                "Sozlamalar → Batareya → Fon foydalanish chegaralari → HaramHide " +
                    "«Uxlaydigan ilovalar» ro'yxatida BO'LMASLIGI kerak."
            else -> null
        }
}
