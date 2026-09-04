package com.haramhide.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.haramhide.core.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * **TZ FR-102.**
 *
 * v1.0 hujjatida "boot'da xizmatni avtomatik tiklash" talabi bor edi. Bu
 * **bajarilmaydi**: Android 14+ da MediaProjection tokeni har sessiya uchun
 * yangi rozilik talab qiladi va uni faqat foydalanuvchi Activity orqali beradi
 * (TZ C-01). Shuning uchun bu yerda faqat bildirishnoma chiqadi.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val desired = SettingsRepository(appContext).settings.first().protectionDesired
                if (!desired) {
                    Log.i(TAG, "Himoya so'ralmagan — bildirishnoma chiqarilmaydi")
                    return@launch
                }
                Notifications.ensureChannels(appContext)
                appContext.getSystemService(NotificationManager::class.java).notify(
                    Notifications.ID_ACTION,
                    Notifications.actionRequired(
                        appContext,
                        R.string.notif_boot_title,
                        R.string.notif_boot_text,
                        urgent = false,
                    ),
                )
                Log.i(TAG, "Boot bildirishnomasi chiqarildi")
            } catch (t: Throwable) {
                Log.e(TAG, "Boot ishlovida xato", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
