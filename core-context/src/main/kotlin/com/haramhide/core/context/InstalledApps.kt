package com.haramhide.core.context

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * Foydalanuvchi ochishi mumkin bo'lgan ilovalar ro'yxati — whitelist ekrani
 * uchun (TZ FR-204).
 */
object InstalledApps {

    data class Entry(
        val packageName: String,
        val label: String,
        val isSystem: Boolean,
    )

    /**
     * Ishga tushirish mumkin bo'lgan ilovalar, nom bo'yicha saralangan.
     * O'z ilovamiz ro'yxatga kirmaydi.
     */
    fun list(context: Context): List<Entry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = runCatching {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())

        return resolved.asSequence()
            .mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                val pkg = ai.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                Entry(
                    packageName = pkg,
                    label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(pkg),
                    isSystem = (ai.applicationInfo?.flags ?: 0) and
                        android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /**
     * Default himoyalanadigan ilovalar — TZ FR-204: "ijtimoiy tarmoq +
     * brauzerlar ON, boshqalar OFF".
     *
     * Ro'yxat qat'iy paket nomlari bilan emas, kalit so'zlar bilan tanlanadi —
     * shunda mahalliy va muqobil ilovalar ham tushadi (masalan turli brauzer
     * forklari yoki mintaqaviy ijtimoiy tarmoqlar).
     */
    fun suggestDefaults(apps: List<Entry>): Set<String> {
        val keywords = listOf(
            "instagram", "facebook", "tiktok", "snapchat", "twitter", "x.android",
            "reddit", "tumblr", "pinterest", "telegram", "whatsapp", "vk", "odnoklassniki",
            "chrome", "firefox", "opera", "browser", "brave", "duckduckgo", "samsung.internet",
            "yandex", "edge", "vivaldi", "kiwibrowser",
            "youtube", "vimeo", "twitch",
        )
        return apps.asSequence()
            .filter { e -> keywords.any { e.packageName.contains(it, ignoreCase = true) } }
            .map { it.packageName }
            .toSet()
    }
}
