package com.haramhide.core.capture

import android.graphics.Bitmap

/**
 * Bitta ekran kadri.
 *
 * MUHIM: [bitmap] va [analysis] qayta ishlatiladigan buferlar. Ular faqat
 * `onFrame` callback ichida amal qiladi — callback qaytgandan keyin ularning
 * mazmuni keyingi kadr bilan almashadi. Saqlash kerak bo'lsa nusxa oling.
 *
 * TZ FR-301: bu buferlar hech qachon diskka yozilmaydi.
 */
class Frame(
    /** Capture o'lchamidagi bitmap. Kengligi [paddedWidth] ga teng (rowStride padding). */
    val bitmap: Bitmap,
    /** Haqiqiy foydali kenglik — bitmap'ning faqat shu qismi to'g'ri. */
    val width: Int,
    val height: Int,
    /** rowStride tufayli bitmap'ning haqiqiy kengligi (>= [width]). */
    val paddedWidth: Int,
    /** Detektsiya uchun kichraytirilgan bufer. */
    val analysis: AnalysisBuffer,
    val timestampMs: Long,
    /** [android.view.Surface] rotatsiyasi: 0 / 1 / 2 / 3. */
    val rotation: Int,
)

/**
 * Kichraytirilgan tahlil buferi. Barcha detektsiya shu ustida ishlaydi —
 * to'liq kadr ustida emas. Bu TZ 4.2 dagi "Preprocess" bosqichining F0 ko'rinishi.
 */
class AnalysisBuffer(val width: Int, val height: Int) {
    /** ARGB piksellar. */
    val pixels = IntArray(width * height)

    /** Yorug'lik (luma) 0..255 — chekka energiyasi va frame-diff uchun. */
    val luma = IntArray(width * height)

    fun computeLuma() {
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // ITU-R BT.601, butun sonli yaqinlashish
            luma[i] = (r * 77 + g * 150 + b * 29) shr 8
        }
    }
}
