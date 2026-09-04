package com.haramhide.core.capture

/**
 * Capture parametrlari. TZ 6.1 — qurilma tier'iga qarab tanlanadi.
 */
data class CaptureConfig(
    /** Uzun tomon bo'yicha maksimal capture o'lchami (piksel). Tier A: 1280, B: 960, C: 640. */
    val maxCaptureDimension: Int = 1280,
    /** Sekundiga maksimal kadr. Tier A: 12, B: 8, C: 4. */
    val targetFps: Int = 8,
    /** Tahlil buferining uzun tomoni. Detektsiya shu o'lchamda ishlaydi. */
    val analysisDimension: Int = 256,
) {
    val minFrameIntervalMs: Long get() = (1000L / targetFps.coerceAtLeast(1))

    companion object {
        val TIER_A = CaptureConfig(maxCaptureDimension = 1280, targetFps = 12, analysisDimension = 320)
        val TIER_B = CaptureConfig(maxCaptureDimension = 960, targetFps = 8, analysisDimension = 256)
        val TIER_C = CaptureConfig(maxCaptureDimension = 640, targetFps = 4, analysisDimension = 192)
    }
}
