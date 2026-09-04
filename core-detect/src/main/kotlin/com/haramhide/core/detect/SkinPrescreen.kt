package com.haramhide.core.detect

import com.haramhide.core.capture.Frame
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * **Stage A — arzon darvoza.**
 *
 * Bu NSFW klassifikatori EMAS va shunday bo'lishga da'vo qilmaydi. Uning yagona
 * savoli: *og'ir detektorni ishga tushirishga arziydimi?*
 *
 * ### Nega model emas
 * TZ 8.1 da Stage A uchun MobileNetV2 (GantMan/nsfw_model, MIT) rejalashtirilgan
 * edi. F1 da qidiruv shuni ko'rsatdi: tayyor ONNX ko'rinishida, ruxsat beruvchi
 * litsenziyali va **mobil uchun yetarlicha yengil** NSFW klassifikatori mavjud
 * emas. Mavjud Apache-2.0 variantlar (Falconsai, AdamCodd) — ViT-base, ya'ni
 * 86M parametr: har kadrda ishlaydigan darvoza uchun 10-30 barobar og'ir.
 * GantMan'niki esa Keras formatida va konversiya talab qiladi.
 *
 * Shuning uchun Stage A bu yerda teri rangi mavjudligini tekshiruvchi arzon
 * evristika. U allaqachon hisoblangan tahlil buferi ustida ishlaydi, qo'shimcha
 * xotira olmaydi va ~0.3 ms turadi.
 *
 * ### Nega bu ishlaydi
 * Yalang'ochlik teri ko'rinishini nazarda tutadi. Ya'ni bu darvoza **yolg'on
 * salbiy** (o'tkazib yuborish) berish ehtimoli past — aynan darvozadan
 * talab qilinadigan xossa. Yolg'on ijobiylar (yuz, qo'l, yog'och, non) esa
 * zararsiz: ular shunchaki Stage B ni ishga tushiradi, u esa haqiqiy qarorni
 * qabul qiladi.
 *
 * ### Nima o'chirib tashlaydi
 * Matn ekranlari, kod, xaritalar, qorong'i UI, o'yin grafikasi, hujjatlar —
 * ya'ni kunlik foydalanishning katta qismi.
 *
 * ### To'yinganlik tuzog'i (fail-open)
 * Teri qoidasi `R > G > B` va `|R-G| > 15` ga tayanadi. Kulrang (qora-oq) yoki
 * kuchli rang filtri qo'yilgan kadrda bu shartlar hech qachon bajarilmaydi va
 * darvoza **butun kadrni o'tkazmay tashlab yuborardi** — ya'ni qora-oq
 * kontent umuman tekshirilmay qolardi.
 *
 * Shuning uchun kadrning o'rtacha rang to'yinganligi o'lchanadi. U juda past
 * bo'lsa, qoida ishonchsiz deb belgilanadi va darvoza **ochiq qoladi**
 * (ball = 1.0). Bu Stage B ni ortiqcha ishga tushiradi, lekin darvozaning
 * xatosi faqat bitta yo'nalishda bo'lishi kerak: o'tkazib yuborish emas.
 */
class SkinPrescreen(
    /** Tahlil buferini shuncha blokka bo'ladi (har o'lchov bo'yicha). */
    private val gridSize: Int = 8,
    /**
     * Blokdagi teri ulushi shu qiymatga yetganda ball 1.0 bo'ladi.
     * Past qiymat = ko'proq kadr o'tadi = yuqoriroq recall.
     */
    private val saturationRatio: Float = 0.20f,
) : StageAClassifier {

    /** Diagnostika: oxirgi kadrdagi eng yuqori blok ulushi. */
    @Volatile var lastPeakRatio: Float = 0f; private set

    /** Diagnostika: oxirgi kadr rangsiz deb topildimi (fail-open holati). */
    @Volatile var lastLowSaturation: Boolean = false; private set

    override fun score(frame: Frame): Float {
        val a = frame.analysis
        val w = a.width
        val h = a.height
        if (w <= 0 || h <= 0) return 0f

        val px = a.pixels

        // Rang to'yinganligi — teri qoidasi umuman ishlata oladimi?
        var chromaSum = 0L
        var sampled = 0
        var i = 0
        while (i < px.size) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            chromaSum += max(r, max(g, b)) - min(r, min(g, b))
            sampled++
            i += SATURATION_STRIDE
        }
        val meanChroma = if (sampled > 0) chromaSum.toFloat() / sampled else 0f
        if (meanChroma < MIN_MEAN_CHROMA) {
            lastLowSaturation = true
            lastPeakRatio = 0f
            return 1f   // fail-open: qoida ishonchsiz, darvoza ochiq
        }
        lastLowSaturation = false

        var peak = 0f

        for (gy in 0 until gridSize) {
            val y0 = gy * h / gridSize
            val y1 = ((gy + 1) * h / gridSize).coerceAtLeast(y0 + 1)
            for (gx in 0 until gridSize) {
                val x0 = gx * w / gridSize
                val x1 = ((gx + 1) * w / gridSize).coerceAtLeast(x0 + 1)

                var skin = 0
                var total = 0
                var y = y0
                while (y < y1) {
                    val row = y * w
                    var x = x0
                    while (x < x1) {
                        if (isSkin(px[row + x])) skin++
                        total++
                        x++
                    }
                    y++
                }
                if (total > 0) {
                    val ratio = skin.toFloat() / total
                    if (ratio > peak) peak = ratio
                }
            }
        }

        lastPeakRatio = peak
        return min(1f, peak / saturationRatio)
    }

    /**
     * Teri rangi qoidasi (Kovac va boshq., kunduzgi yorug'lik uchun).
     * Qo'pol, lekin darvoza uchun aynan shu kerak — u qaror qabul qilmaydi.
     */
    private companion object {
        /** Har nechanchi pikselni to'yinganlik uchun tekshirish (tezlik uchun). */
        const val SATURATION_STRIDE = 7

        /**
         * O'rtacha xromadan past bo'lsa kadr amalda kulrang hisoblanadi.
         * Qora-oq foto ~0-3, oddiy rangli ekran 25-60 atrofida bo'ladi.
         */
        const val MIN_MEAN_CHROMA = 8f
    }

    private fun isSkin(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        if (r <= 95 || g <= 40 || b <= 20) return false
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        if (mx - mn <= 15) return false
        if (abs(r - g) <= 15) return false
        return r > g && r > b
    }
}
