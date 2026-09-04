package com.haramhide.core.data

/**
 * **TZ FR-205 — cool-down.**
 *
 * Tahdid modelida (TZ 1.2) raqib — foydalanuvchining o'zi, irodasi zaiflashgan
 * paytdagi. Shuning uchun himoyani **kuchaytirish** darhol bajariladi, ammo
 * **zaiflashtirish** kechikish bilan.
 *
 * Bu ataylab noqulaylik: qaror qabul qilingan payt bilan u kuchga kiradigan
 * payt orasiga masofa qo'yiladi.
 */
data class PendingChange(
    val type: Type,
    /** Yangi qiymat. [Type.STOP] uchun bo'sh. */
    val value: String,
    /** `System.currentTimeMillis()` — mutlaq vaqt, ilova qayta ishga tushsa ham saqlanadi. */
    val availableAtMs: Long,
) {
    enum class Type {
        /** Sezgirlikni pasaytirish. */
        SENSITIVITY,

        /** Himoyalanadigan ilovalar ro'yxatidan olib tashlash. */
        PACKAGES,

        /** Himoyani butunlay to'xtatish. */
        STOP,

        /** Detektorni evristikaga (zaifroq) o'tkazish. */
        ENGINE,
    }

    fun remainingMs(nowMs: Long = System.currentTimeMillis()): Long =
        (availableAtMs - nowMs).coerceAtLeast(0)

    fun isDue(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs >= availableAtMs

    companion object {
        /** Default kechikish — TZ FR-205: 30 daqiqa. */
        const val DEFAULT_COOL_DOWN_MS = 30 * 60 * 1000L
    }
}

/**
 * Sezgirlik darajalarining kuchi. Katta son = kuchliroq himoya.
 * Bu yerda satr sifatida taqqoslanadi, chunki `:core-data` `:core-detect` ga
 * bog'liq emas.
 */
fun sensitivityRank(v: String): Int = when (v) {
    "STRICT" -> 3
    "MEDIUM" -> 2
    "LOW" -> 1
    else -> 2
}

/**
 * Cool-down qarorlari — sof funksiyalar.
 *
 * Ular [SettingsRepository] dan ajratilgan, chunki mantiq nozik va uni
 * DataStore va Android Context'siz sinash mumkin bo'lishi kerak.
 */
object CoolDownPolicy {

    /** Sezgirlikni pasaytirish himoyani zaiflashtiradi. */
    fun isSensitivityWeakening(from: String, to: String): Boolean =
        sensitivityRank(to) < sensitivityRank(from)

    /**
     * Ilovalar ro'yxatining o'zgarishi zaiflashtiradimi.
     *
     * **Bo'sh ro'yxat "HAMMA ilova" degani.** Shuning uchun:
     *  - bo'sh -> aniq ro'yxat = qamrov toraydi = zaiflashtirish
     *  - aniq ro'yxat -> bo'sh = qamrov kengayadi = kuchaytirish
     *  - ro'yxatdan olib tashlash = zaiflashtirish
     */
    fun isPackagesWeakening(from: Set<String>, to: Set<String>): Boolean = when {
        from.isEmpty() -> to.isNotEmpty()
        to.isEmpty() -> false
        else -> (from - to).isNotEmpty()
    }

    /** Evristik detektorga o'tish zaiflashtirish (u NSFW ni aniqlamaydi). */
    fun isEngineWeakening(to: String): Boolean = to != "NUDENET"

    /**
     * Kechikish kerakmi.
     *
     * Kechikish faqat foydalanuvchi himoyani **yoqqan** bo'lsa qo'llanadi.
     * Dastlabki sozlash paytida u yo'q — odam hali hech narsaga majburiyat
     * olmagan va sozlamalarni erkin o'zgartira olishi kerak.
     */
    fun requiresCoolDown(weakening: Boolean, committed: Boolean): Boolean =
        weakening && committed
}
