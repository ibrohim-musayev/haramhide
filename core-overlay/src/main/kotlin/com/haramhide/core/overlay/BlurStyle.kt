package com.haramhide.core.overlay

/** Blur uslubi. TZ FR-202. */
enum class BlurStyle {
    /** Kichraytirib-kattalashtirish + bilinear filtr — Gauss'ga yaqin natija. */
    GAUSSIAN,

    /** Xuddi shu, lekin filtrsiz — kvadratchalar. */
    PIXELATE,

    /** To'liq qoplovchi rang. */
    SOLID,
}

/**
 * Blur parametrlari.
 *
 * [intensity] 10..100 (TZ FR-203). U kichraytirish darajasiga aylantiriladi:
 * qanchalik kuchli kichraytirilsa, shunchalik ko'p detal yo'qoladi.
 */
data class BlurSpec(
    val style: BlurStyle = BlurStyle.GAUSSIAN,
    val intensity: Int = 70,
) {
    /** Kadr shu koeffitsientga kichraytiriladi. */
    val downscale: Int
        get() = (4 + (intensity.coerceIn(10, 100) - 10) * 36 / 90).coerceIn(4, 40)
}
