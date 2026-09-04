package com.haramhide.core.detect

import com.haramhide.core.capture.Frame

/**
 * Stage A — yengil "darvoza" klassifikatori (TZ 4.2).
 * Kadrlarning ~95% shu yerda tugaydi, shuning uchun u arzon bo'lishi shart.
 *
 * F1 da bu yerga GantMan/nsfw_model (MobileNetV2, MIT) INT8 keladi.
 */
interface StageAClassifier {
    /** @return 0..1 oralig'ida NSFW ehtimoli. */
    fun score(frame: Frame): Float

    fun close() {}
}

/**
 * Stage B — detektor, bbox qaytaradi (TZ 4.2).
 * Faqat Stage A darvozadan o'tganda ishga tushadi.
 *
 * F1 da bu yerga NudeNet v3 `320n.onnx` (AGPL-3.0) keladi.
 */
interface StageBDetector {
    fun detect(frame: Frame, minConfidence: Float): List<Detection>

    fun close() {}
}

/**
 * Ikki bosqichni birlashtiruvchi. Stage A darvozasi Stage B ni kadrlarning
 * atigi ~5% ida ishga tushiradi — TZ 4.2 ga ko'ra bu energiyani 4-6 barobar tejaydi.
 */
class TwoStageDetector(
    private val stageA: StageAClassifier,
    private val stageB: StageBDetector,
) {
    /** Diagnostika: Stage B necha marta ishga tushdi. */
    @Volatile var stageBRuns: Long = 0L; private set
    @Volatile var totalRuns: Long = 0L; private set
    @Volatile var lastStageAScore: Float = 0f; private set

    fun run(frame: Frame, sensitivity: Sensitivity): List<Detection> {
        totalRuns++
        val a = stageA.score(frame)
        lastStageAScore = a
        if (a < sensitivity.tLow) return emptyList()
        stageBRuns++
        return stageB.detect(frame, sensitivity.tDet)
    }

    /** Stage B ishga tushish ulushi — batareya baholash uchun (TZ 6.2). */
    fun stageBRatio(): Float =
        if (totalRuns == 0L) 0f else stageBRuns.toFloat() / totalRuns

    fun close() {
        stageA.close()
        stageB.close()
    }
}
