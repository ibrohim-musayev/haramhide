package com.haramhide.core.overlay

import com.haramhide.core.detect.Detection

/**
 * Mask holatlari. TZ FR-105.
 *
 * `ACTIVE → HOLD → (PROBING) → FADING → RELEASED`
 */
enum class MaskState {
    /** Detektor hozirgina tasdiqladi. */
    ACTIVE,

    /** Ko'r zonada ushlab turilyapti — dalil yo'q, lekin bo'shatish sharti ham yo'q. */
    HOLD,

    /** Sinov: overlay shu mask uchun vaqtincha yashirildi, ostiga qaralyapti. */
    PROBING,

    /** So'nib bormoqda. */
    FADING,

    /** Bo'shatildi — ro'yxatdan olib tashlanadi. */
    RELEASED,
}

class Mask(
    val id: Long,
    var rect: Detection,
    var state: MaskState,
    var createdAtMs: Long,
    var stateSinceMs: Long,
    var lastPositiveMs: Long,
    var alpha: Float = 1f,
    /** PROBING tugaydigan vaqt. */
    var probeUntilMs: Long = 0L,
    /** Probe boshlangan kadr raqami — kutish kadrda o'lchanadi, vaqtda emas. */
    var probeStartFrame: Long = 0L,
    var probeCount: Int = 0,
) {
    /**
     * Ekranda biror ko'rinishda chiziladimi.
     *
     * PROBING da mask **butunlay yo'qolmaydi** — faqat markazi ochiladi
     * (ADR-007). Shuning uchun u ham ko'rinadigan hisoblanadi.
     */
    val isVisible: Boolean
        get() = state != MaskState.RELEASED && alpha > 0.01f

    /** Sinov paytidami — chizishda markaz ochiq qoldiriladi. */
    val isProbing: Boolean get() = state == MaskState.PROBING

    val isAlive: Boolean get() = state != MaskState.RELEASED
}

/**
 * Mask'ni bo'shatish siyosati — **ADR-003 ning asosiy savoli**.
 *
 * F0 ning maqsadi shu uch variantni haqiqiy qurilmada o'lchab, birini tanlash.
 */
enum class ReleasePolicy {
    /**
     * Timeout tugagach overlay qisqa vaqtga yashiriladi va ostiga qaraladi.
     * Aniq, lekin foydalanuvchi kontentni ko'rib qolishi mumkin (probe oynasi).
     */
    PROBE,

    /**
     * Timeout tugagach hech narsa tekshirilmasdan bo'shatiladi.
     * Sinov oynasi yo'q, lekin kontent hali joyida bo'lsa u ochilib ketadi.
     */
    TIMEOUT_ONLY,

    /**
     * Timeout umuman ishlamaydi — mask faqat harakat (scroll / ilova almashish /
     * halqa o'zgarishi) bo'lganda bo'shatiladi. Eng xavfsiz, lekin statik
     * ekranda mask abadiy qolishi mumkin.
     */
    MOTION_ONLY,
}

data class MaskConfig(
    /** Dalilsiz ushlab turish muddati. TZ FR-105: default 3 s. */
    val holdTimeoutMs: Long = 3_000L,

    /** So'nish davomiyligi. */
    val fadeDurationMs: Long = 200L,

    /**
     * Sinov paytida mask markazining ochiladigan ulushi (chiziqli o'lcham).
     *
     * **ADR-007.** Oldin sinov paytida mask butunlay olib tashlanardi va
     * kontent ~600 ms davomida to'liq ochiq qolardi. Endi faqat markaziy
     * qism ochiladi: 0.45 chiziqli ulush = maydonning ~20 %.
     *
     * Bu detektorga yetarli signal berishi kerak (NSFW hududning markazi
     * odatda eng ma'lumotli joyi), lekin foydalanuvchi uchun ochilish
     * sezilarli darajada kamayadi.
     *
     * 0 qilib qo'yilsa eski xatti-harakat qaytadi (to'liq ochish).
     */
    val probeHoleFraction: Float = 0.45f,

    /**
     * PROBING ning MINIMAL davomiyligi.
     *
     * Buning o'zi yetarli emas: F0 o'lchovida capture 1-5 fps da ishladi, ya'ni
     * bitta kadr 200-1000 ms. 120 ms lik oyna bitta kadrdan ham qisqa bo'lib
     * chiqdi va probe hali blur'langan eski kadrni ko'rib "kontent yo'q" degan
     * xulosaga keldi -> mask bo'shatildi -> miltillash. Shuning uchun
     * [probeFrames] bilan birga ishlatiladi.
     */
    val probeWindowMs: Long = 120L,

    /**
     * PROBING tugashi uchun overlay yashiringan holatda shuncha kadr
     * kelishi shart. Capture konveyeridagi kechikishni qoplaydi.
     */
    val probeFrames: Int = 3,

    /** Mask atrofidagi halqada shu o'zgarishdan katta bo'lsa — kontent siljidi. */
    val ringDeltaThreshold: Int = 8,

    /** Butun kadr shu qiymatdan ko'p o'zgarsa — ekran almashdi. */
    val globalDeltaThreshold: Int = 18,

    /** Detektsiya to'rtburchagini shu ulushga kengaytirish. */
    val expandFraction: Float = 0.10f,

    /** Shu IoU dan yuqori masklar birlashtiriladi. */
    val mergeIou: Float = 0.15f,

    /**
     * Gisterezis: mask kamida shuncha vaqt turadi va bu davrda harakat
     * sabab bo'shatilmaydi. Blur chizilishining o'zi halqani biroz
     * o'zgartirishi mumkin — bu shundan himoya qiladi.
     */
    val minVisibleMs: Long = 500L,

    val releasePolicy: ReleasePolicy = ReleasePolicy.PROBE,
)
