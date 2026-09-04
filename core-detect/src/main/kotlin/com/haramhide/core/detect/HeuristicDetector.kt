package com.haramhide.core.detect

import com.haramhide.core.capture.Frame
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * **F0 uchun soxta detektor.** Haqiqiy ML modeli emas va shunday bo'lishga
 * da'vo ham qilmaydi — uning yagona vazifasi TZ C-04 (overlay↔capture qayta
 * aloqa halqasi) muammosini **haqiqiy ko'rinishda** qayta hosil qilish.
 *
 * ### Nega oddiy "doim shu to'rtburchak" soxta detektor yaramaydi
 * Agar detektor pikselga qaramasdan doim bir xil natija qaytarsa, blur qo'yilgach
 * ham natija o'zgarmaydi — demak flicker umuman yuzaga kelmaydi va biz F0 da
 * hech narsani tekshirmagan bo'lamiz. Muammo prototipda emas, F2 da chiqadi.
 *
 * ### Shuning uchun bu detektor ikki signalga tayanadi
 *  1. **Teri rangi ulushi** — blur buni deyarli o'zgartirmaydi (o'rtacha rang saqlanadi).
 *  2. **Chekka energiyasi (edge energy)** — blur buni **yo'q qiladi**.
 *
 * Haqiqiy NSFW modellari ham shakl va teksturaga tayanadi, blur esa aynan shuni
 * buzadi. Ya'ni bu soxta detektor haqiqiy modelning flicker nuqtai nazaridan
 * eng muhim xossasini to'g'ri taqlid qiladi: **blur qo'yilgach aniqlash yo'qoladi.**
 *
 * F1 da bu klass o'rniga [StageAClassifier] ga MobileNetV2 va [StageBDetector] ga
 * NudeNet 320n keladi — interfeys o'zgarmaydi.
 */
class HeuristicDetector(
    /** Tahlil buferidagi katak o'lchami (piksel). */
    private val cellPx: Int = 16,
    /** Shu qiymatdan katta chekka energiyasi "to'liq tekstura" hisoblanadi. */
    private val edgeReference: Float = 12f,
    /** Komponentda shundan kam katak bo'lsa — shovqin deb tashlanadi. */
    private val minCells: Int = 2,
) : StageAClassifier, StageBDetector {

    private var cols = 0
    private var rows = 0
    private var scores = FloatArray(0)
    private var computedStamp = -1L

    /** Diagnostika — oxirgi kadrdagi o'rtacha chekka energiyasi. */
    @Volatile var lastEdgeAverage: Float = 0f; private set

    /** Diagnostika — oxirgi kadrdagi eng yuqori teri-rangi ulushi. */
    @Volatile var lastSkinPeak: Float = 0f; private set

    /** Ikkala interfeys ham `close()` beradi — Kotlin aniq override talab qiladi. */
    override fun close() {
        scores = FloatArray(0)
        computedStamp = -1L
    }

    override fun score(frame: Frame): Float {
        compute(frame)
        var best = 0f
        for (s in scores) if (s > best) best = s
        return best
    }

    override fun detect(frame: Frame, minConfidence: Float): List<Detection> {
        compute(frame)
        if (cols == 0 || rows == 0) return emptyList()

        val visited = BooleanArray(cols * rows)
        val out = ArrayList<Detection>(4)
        val stack = ArrayDeque<Int>()

        for (start in scores.indices) {
            if (visited[start] || scores[start] < minConfidence) continue

            var minCx = Int.MAX_VALUE; var minCy = Int.MAX_VALUE
            var maxCx = Int.MIN_VALUE; var maxCy = Int.MIN_VALUE
            var peak = 0f
            var count = 0

            stack.addLast(start)
            visited[start] = true
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                val cx = i % cols
                val cy = i / cols
                count++
                if (cx < minCx) minCx = cx
                if (cy < minCy) minCy = cy
                if (cx > maxCx) maxCx = cx
                if (cy > maxCy) maxCy = cy
                if (scores[i] > peak) peak = scores[i]

                // 4 qo'shni
                if (cx > 0) tryPush(stack, visited, i - 1, minConfidence)
                if (cx < cols - 1) tryPush(stack, visited, i + 1, minConfidence)
                if (cy > 0) tryPush(stack, visited, i - cols, minConfidence)
                if (cy < rows - 1) tryPush(stack, visited, i + cols, minConfidence)
            }

            if (count < minCells) continue
            out += Detection(
                left = minCx.toFloat() / cols,
                top = minCy.toFloat() / rows,
                right = (maxCx + 1).toFloat() / cols,
                bottom = (maxCy + 1).toFloat() / rows,
                score = peak,
                label = "heuristic",
            )
        }
        return out
    }

    private fun tryPush(stack: ArrayDeque<Int>, visited: BooleanArray, i: Int, minConf: Float) {
        if (!visited[i] && scores[i] >= minConf) {
            visited[i] = true
            stack.addLast(i)
        }
    }

    /** Kadr uchun katak ballarini hisoblaydi. Bir kadr uchun bir marta. */
    private fun compute(frame: Frame) {
        if (computedStamp == frame.timestampMs) return
        computedStamp = frame.timestampMs

        val a = frame.analysis
        val aw = a.width
        val ah = a.height
        if (aw <= 0 || ah <= 0) { cols = 0; rows = 0; return }

        val newCols = (aw + cellPx - 1) / cellPx
        val newRows = (ah + cellPx - 1) / cellPx
        if (newCols != cols || newRows != rows || scores.size != newCols * newRows) {
            cols = newCols
            rows = newRows
            scores = FloatArray(cols * rows)
        }

        val px = a.pixels
        val luma = a.luma
        var edgeAccum = 0.0
        var edgeCells = 0
        var skinPeak = 0f

        for (cy in 0 until rows) {
            val y0 = cy * cellPx
            val y1 = min(y0 + cellPx, ah)
            for (cx in 0 until cols) {
                val x0 = cx * cellPx
                val x1 = min(x0 + cellPx, aw)

                var skin = 0
                var total = 0
                var edgeSum = 0L
                var edgeN = 0

                var y = y0
                while (y < y1) {
                    val row = y * aw
                    val nextRow = if (y + 1 < ah) row + aw else row
                    var x = x0
                    while (x < x1) {
                        val i = row + x
                        if (isSkin(px[i])) skin++
                        total++

                        val l = luma[i]
                        val rx = if (x + 1 < aw) luma[row + x + 1] else l
                        val ry = luma[nextRow + x]
                        edgeSum += abs(rx - l) + abs(ry - l)
                        edgeN++
                        x++
                    }
                    y++
                }

                if (total == 0) { scores[cy * cols + cx] = 0f; continue }

                val skinRatio = skin.toFloat() / total
                val edgeAvg = if (edgeN > 0) edgeSum.toFloat() / edgeN else 0f
                edgeAccum += edgeAvg
                edgeCells++
                if (skinRatio > skinPeak) skinPeak = skinRatio

                // Blur chekka energiyasini yo'q qiladi -> edgeFactor tushadi -> ball tushadi.
                val edgeFactor = min(1f, edgeAvg / edgeReference)
                scores[cy * cols + cx] = skinRatio * edgeFactor
            }
        }

        lastEdgeAverage = if (edgeCells > 0) (edgeAccum / edgeCells).toFloat() else 0f
        lastSkinPeak = skinPeak
    }

    /**
     * Oddiy teri rangi qoidasi (Kovac va boshq., kunduzgi yorug'lik uchun).
     * Bu **qo'pol evristika** — haqiqiy mahsulotda ishlatilmaydi, faqat F0 uchun.
     */
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
