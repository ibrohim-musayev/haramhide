package com.haramhide.core.capture

/**
 * FLAG_SECURE oynalarini aniqlash. TZ C-03 / FR-104.
 *
 * Bank ilovalari, parol menejerlari, DRM pleyerlar `FLAG_SECURE` qo'yadi va
 * VirtualDisplay'ga qora kadr keladi. Buni "kontent yo'q" bilan adashtirmaslik
 * kerak: bu "aniqlab bo'lmadi" holati va siyosat bo'yicha hal qilinadi.
 */
object BlackFrameDetector {

    /** Piksel shu qiymatdan past bo'lsa "qora" hisoblanadi. */
    private const val DARK_LEVEL = 8

    /** Shu ulushdan ko'p piksel qora bo'lsa — kadr qora deb belgilanadi. */
    private const val DARK_RATIO = 0.995f

    fun isBlackFrame(analysis: AnalysisBuffer): Boolean {
        val luma = analysis.luma
        if (luma.isEmpty()) return false
        var dark = 0
        for (v in luma) if (v < DARK_LEVEL) dark++
        return dark.toFloat() / luma.size >= DARK_RATIO
    }
}

/** Qora kadr aniqlanganda nima qilish. TZ FR-104. */
enum class SecurePolicy {
    /** Blur qo'yilmaydi, faqat lokal log. Default. */
    FAIL_OPEN,
    /** Butun ekran blur bo'ladi (Strict rejim). */
    FAIL_CLOSED,
}
