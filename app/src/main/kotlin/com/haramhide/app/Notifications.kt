package com.haramhide.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Bildirishnomalar. TZ FR-101 (o'chirilmaydigan foreground notification),
 * FR-102 (boot'dan keyin rozilik so'rovi), FR-103 (sessiya uzilganda tiklash).
 */
object Notifications {

    const val CHANNEL_PROTECTION = "protection"
    const val CHANNEL_ACTION = "action"

    const val ID_FOREGROUND = 1001
    const val ID_ACTION = 1002

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROTECTION,
                context.getString(R.string.notif_channel_protection),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                description = "Himoya xizmatining doimiy holati"
            }
        )

        // Yuqori muhimlik: qulf ochilgach darhol ko'rinishi kerak (TZ FR-103)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ACTION,
                context.getString(R.string.notif_channel_action),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setShowBadge(true)
                description = "Himoyani qayta yoqish uchun bir bosishlik so'rov"
            }
        )
    }

    /** Xizmat ishlayotganda ko'rsatiladigan doimiy bildirishnoma. */
    fun foreground(context: Context, running: Boolean): Notification {
        val title = if (running) R.string.notif_active_title else R.string.notif_off_title
        val text = if (running) R.string.notif_active_text else R.string.notif_off_text

        val builder = Notification.Builder(context, CHANNEL_PROTECTION)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setOngoing(true)
            .setContentIntent(openAppIntent(context))
            .setCategory(Notification.CATEGORY_SERVICE)

        if (running) {
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.action_stop),
                    servicePendingIntent(context, ProtectionService.ACTION_STOP, 10),
                ).build()
            )
        } else {
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.action_enable),
                    consentPendingIntent(context, 11),
                ).build()
            )
        }
        return builder.build()
    }

    /**
     * Bir bosishlik tiklash so'rovi. TZ FR-103 — qulf ochilgach darhol chiqadi,
     * FR-102 — boot'dan keyin chiqadi.
     */
    /**
     * Bir bosishlik tiklash so'rovi. TZ FR-103 — qulf ochilgach darhol chiqadi,
     * FR-102 — boot'dan keyin chiqadi.
     *
     * **`setFullScreenIntent` ATAYLAB ishlatilmaydi.** U `USE_FULL_SCREEN_INTENT`
     * ruxsatini talab qiladi, Android 14+ da esa bu ruxsat default holda faqat
     * qo'ng'iroq va budilnik ilovalariga beriladi — bizga foydalanuvchi uni
     * sozlamalardan qo'lda yoqishi kerak bo'lardi, Play siyosati ham buni
     * cheklaydi. [CHANNEL_ACTION] allaqachon `IMPORTANCE_HIGH` bo'lgani uchun
     * bildirishnoma baribir heads-up sifatida ko'rinadi va FR-103 talabi
     * (bir bosishda tiklash) bajariladi.
     *
     * @param urgent yuqori prioritet — qulf ochilgan payt uchun
     */
    fun actionRequired(context: Context, title: Int, text: Int, urgent: Boolean): Notification =
        Notification.Builder(context, CHANNEL_ACTION)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(if (urgent) Notification.PRIORITY_HIGH else Notification.PRIORITY_DEFAULT)
            .setContentIntent(consentPendingIntent(context, 12))
            .build()

    fun cancelAction(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_ACTION)
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

    /** MediaProjection roziligini so'raydigan shaffof aktiviti (TZ C-01). */
    private fun consentPendingIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, ProjectionRequestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun servicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context, requestCode,
            Intent(context, ProtectionService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )
}
