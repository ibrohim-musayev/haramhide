package com.haramhide.core.capture

import kotlin.math.abs

/**
 * Kadrlar orasidagi o'zgarish signallari.
 *
 * Ikki vazifani bajaradi:
 *  1. **Gate 1** (TZ 4.2) — kadr deyarli o'zgarmagan bo'lsa uni butunlay tashlash.
 *  2. **Mask release sharti** (TZ FR-105) — mask atrofidagi halqada o'zgarish
 *     bo'lsa, demak kontent siljidi va mask'ni bo'shatish kerak.
 *
 * Kadrni [grid]x[grid] luma to'riga siqib, SAD (sum of absolute differences)
 * hisoblaydi. 64x64 uchun bu ~0.3 ms.
 */
class FrameSignals(private val grid: Int = 64) {

    private val cur = IntArray(grid * grid)
    private val prev = IntArray(grid * grid)
    private var hasPrev = false

    /** Yangi kadrni to'rga siqadi. [commit] chaqirilmaguncha [prev] o'zgarmaydi. */
    fun update(analysis: AnalysisBuffer) {
        val aw = analysis.width
        val ah = analysis.height
        if (aw <= 0 || ah <= 0) return
        val luma = analysis.luma
        for (gy in 0 until grid) {
            // To'r katagiga to'g'ri keladigan manba qatorlari
            val y0 = gy * ah / grid
            val y1 = ((gy + 1) * ah / grid).coerceAtLeast(y0 + 1)
            for (gx in 0 until grid) {
                val x0 = gx * aw / grid
                val x1 = ((gx + 1) * aw / grid).coerceAtLeast(x0 + 1)
                var sum = 0
                var n = 0
                var y = y0
                while (y < y1) {
                    val rowBase = y * aw
                    var x = x0
                    while (x < x1) {
                        sum += luma[rowBase + x]
                        n++
                        x++
                    }
                    y++
                }
                cur[gy * grid + gx] = if (n > 0) sum / n else 0
            }
        }
    }

    /** Joriy kadrni "oldingi" deb belgilaydi. Har kadrda ishlov tugagach chaqiriladi. */
    fun commit() {
        System.arraycopy(cur, 0, prev, 0, cur.size)
        hasPrev = true
    }

    /** Butun kadr bo'yicha o'rtacha o'zgarish (0..255). Birinchi kadrda katta qiymat. */
    fun globalDelta(): Int {
        if (!hasPrev) return Int.MAX_VALUE
        var sum = 0L
        for (i in cur.indices) sum += abs(cur[i] - prev[i])
        return (sum / cur.size).toInt()
    }

    /**
     * Berilgan normallashtirilgan (0..1) to'rtburchak ATROFIDAGI halqada o'zgarish.
     * Mask'ning o'zi "ko'r zona" — u yerga qaralmaydi (TZ FR-105).
     *
     * @param ring halqa qalinligi, to'rtburchak o'lchamiga nisbatan (0.2 = 20%)
     */
    fun ringDelta(l: Float, t: Float, r: Float, b: Float, ring: Float = 0.2f): Int {
        if (!hasPrev) return 0
        val w = r - l
        val h = b - t
        val ol = ((l - w * ring) * grid).toInt().coerceIn(0, grid - 1)
        val ot = ((t - h * ring) * grid).toInt().coerceIn(0, grid - 1)
        val or = ((r + w * ring) * grid).toInt().coerceIn(0, grid - 1)
        val ob = ((b + h * ring) * grid).toInt().coerceIn(0, grid - 1)
        val il = (l * grid).toInt().coerceIn(0, grid - 1)
        val it = (t * grid).toInt().coerceIn(0, grid - 1)
        val ir = (r * grid).toInt().coerceIn(0, grid - 1)
        val ib = (b * grid).toInt().coerceIn(0, grid - 1)

        var sum = 0L
        var n = 0
        for (gy in ot..ob) {
            for (gx in ol..or) {
                // Ichkarisini o'tkazib yuboramiz — u mask ostida, ishonchsiz
                if (gx in il..ir && gy in it..ib) continue
                val i = gy * grid + gx
                sum += abs(cur[i] - prev[i])
                n++
            }
        }
        return if (n > 0) (sum / n).toInt() else 0
    }

    fun reset() {
        hasPrev = false
        java.util.Arrays.fill(prev, 0)
    }
}
