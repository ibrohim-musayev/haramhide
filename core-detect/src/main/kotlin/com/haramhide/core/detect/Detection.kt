package com.haramhide.core.detect

/**
 * Bitta aniqlangan hudud. Koordinatalar **normallashtirilgan** (0..1),
 * capture fazosida. Ekran koordinatasiga o'tkazish overlay'ning ishi —
 * bu rotatsiya va o'lcham o'zgarishida masklarni saqlab qolish imkonini beradi
 * (TZ FR-107).
 */
data class Detection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val score: Float,
    val label: String = "nsfw",
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = width * height

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    fun iou(o: Detection): Float {
        val il = maxOf(left, o.left)
        val it = maxOf(top, o.top)
        val ir = minOf(right, o.right)
        val ib = minOf(bottom, o.bottom)
        if (ir <= il || ib <= it) return 0f
        val inter = (ir - il) * (ib - it)
        return inter / (area + o.area - inter)
    }

    /** Chetlarini [f] ulushga kengaytiradi (blur chekkasi ochiq qolmasligi uchun). */
    fun expand(f: Float): Detection {
        val dx = width * f
        val dy = height * f
        return copy(
            left = (left - dx).coerceIn(0f, 1f),
            top = (top - dy).coerceIn(0f, 1f),
            right = (right + dx).coerceIn(0f, 1f),
            bottom = (bottom + dy).coerceIn(0f, 1f),
        )
    }
}
