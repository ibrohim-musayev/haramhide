package com.haramhide.core.detect

/**
 * Sezgirlik rejimlari. TZ 8.3-jadval.
 *
 * Foydalanuvchiga foizda ko'rsatilmaydi (TZ FR-201) — faqat nom.
 * Qiymatlar F1 fazasida golden set bo'yicha kalibrlanadi.
 */
enum class Sensitivity(
    /** Stage A darvozasi: shundan past bo'lsa Stage B umuman ishga tushmaydi. */
    val tLow: Float,
    /** Stage B bbox ishonch chegarasi. */
    val tDet: Float,
) {
    LOW(tLow = 0.75f, tDet = 0.60f),
    MEDIUM(tLow = 0.50f, tDet = 0.45f),
    STRICT(tLow = 0.30f, tDet = 0.30f),
}
