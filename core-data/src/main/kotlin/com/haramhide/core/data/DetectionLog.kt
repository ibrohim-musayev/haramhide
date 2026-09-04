package com.haramhide.core.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * **TZ FR-302 / FR-303 / FR-304 — lokal aniqlash jurnali.**
 *
 * ### Nimalar YOZILADI
 * Faqat metama'lumot: vaqt, paket nomi, sezgirlik, model klasslari, ball va
 * normallashtirilgan koordinatalar.
 *
 * ### Nimalar YOZILMAYDI
 * **Hech qanday piksel.** Skrinshot ham, kichraytirilgan nusxa ham, hech nima.
 * Bu TZ FR-301 ning qat'iy talabi va `verifyPrivacy` Gradle vazifasi buni
 * har build'da tekshiradi.
 *
 * ### Nima uchun kerak
 * Foydalanuvchi xato blur qo'yilganini belgilashi va o'sha hududni bir soatga
 * o'chirib qo'yishi mumkin (FR-304). Shuningdek jurnalni eksport qilib
 * GitHub Issue ga qo'lda yuborishi mumkin (FR-303) — avtomatik yuborish yo'q,
 * chunki server ham yo'q.
 *
 * JSON uchun `org.json` ishlatiladi — u Android ichida bor, ya'ni qo'shimcha
 * bog'liqlik kerak emas (F-Droid uchun muhim).
 */
class DetectionLog(context: Context) {

    private val file = File(context.filesDir, "detections.json")
    private val suppressionFile = File(context.filesDir, "suppressions.json")

    data class Record(
        val id: Long,
        val timestampMs: Long,
        val packageName: String,
        val sensitivity: String,
        val labels: String,
        val score: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val falsePositive: Boolean = false,
    )

    data class Suppression(
        val packageName: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val untilMs: Long,
    ) {
        fun covers(pkg: String, cx: Float, cy: Float): Boolean =
            pkg == packageName && cx in left..right && cy in top..bottom
    }

    @Synchronized
    fun append(record: Record) {
        val list = read().toMutableList()
        list += record
        // Halqa bufer: eng eskilarini tashlaymiz
        while (list.size > MAX_RECORDS) list.removeAt(0)
        write(list)
    }

    @Synchronized
    fun recent(limit: Int = 50): List<Record> = read().takeLast(limit).reversed()

    /** TZ FR-304: hududni [SUPPRESSION_MS] davomida blur qilmaslik. */
    @Synchronized
    fun markFalsePositive(id: Long, nowMs: Long = System.currentTimeMillis()) {
        val list = read().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val r = list[idx].copy(falsePositive = true)
        list[idx] = r
        write(list)

        val sup = readSuppressions().toMutableList()
        sup.removeAll { it.untilMs <= nowMs }
        sup += Suppression(r.packageName, r.left, r.top, r.right, r.bottom, nowMs + SUPPRESSION_MS)
        writeSuppressions(sup)
    }

    @Synchronized
    fun activeSuppressions(nowMs: Long = System.currentTimeMillis()): List<Suppression> =
        readSuppressions().filter { it.untilMs > nowMs }

    @Synchronized
    fun clear() {
        runCatching { file.delete() }
        runCatching { suppressionFile.delete() }
    }

    /**
     * TZ FR-303 — eksport uchun matn. Foydalanuvchi uni o'zi ulashadi.
     * Avtomatik yuborish YO'Q.
     */
    @Synchronized
    fun exportText(): String {
        val records = read()
        return buildString {
            appendLine("HaramHide — aniqlash jurnali")
            appendLine("Yozuvlar: ${records.size}")
            appendLine("DIQQAT: bu faylda hech qanday rasm yo'q, faqat metama'lumot.")
            appendLine()
            records.forEach { r ->
                appendLine(
                    "${r.timestampMs}\t${r.packageName}\t${r.sensitivity}\t" +
                        "${r.labels}\t%.2f\t[%.3f,%.3f,%.3f,%.3f]\t%s".format(
                            r.score, r.left, r.top, r.right, r.bottom,
                            if (r.falsePositive) "XATO" else "",
                        )
                )
            }
        }
    }

    // ------------------------------------------------------------- fayl I/O

    private fun read(): List<Record> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Record(
                    id = o.getLong("id"),
                    timestampMs = o.getLong("t"),
                    packageName = o.optString("pkg"),
                    sensitivity = o.optString("sens"),
                    labels = o.optString("labels"),
                    score = o.optDouble("score").toFloat(),
                    left = o.optDouble("l").toFloat(),
                    top = o.optDouble("t0").toFloat(),
                    right = o.optDouble("r").toFloat(),
                    bottom = o.optDouble("b").toFloat(),
                    falsePositive = o.optBoolean("fp"),
                )
            }
        }.onFailure { Log.w(TAG, "Jurnalni o'qib bo'lmadi: $it") }.getOrDefault(emptyList())
    }

    private fun write(list: List<Record>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { r ->
                arr.put(
                    JSONObject().apply {
                        put("id", r.id); put("t", r.timestampMs); put("pkg", r.packageName)
                        put("sens", r.sensitivity); put("labels", r.labels)
                        put("score", r.score.toDouble())
                        put("l", r.left.toDouble()); put("t0", r.top.toDouble())
                        put("r", r.right.toDouble()); put("b", r.bottom.toDouble())
                        put("fp", r.falsePositive)
                    }
                )
            }
            file.writeText(arr.toString())
        }.onFailure { Log.w(TAG, "Jurnalni yozib bo'lmadi: $it") }
    }

    private fun readSuppressions(): List<Suppression> {
        if (!suppressionFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(suppressionFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Suppression(
                    packageName = o.optString("pkg"),
                    left = o.optDouble("l").toFloat(),
                    top = o.optDouble("t").toFloat(),
                    right = o.optDouble("r").toFloat(),
                    bottom = o.optDouble("b").toFloat(),
                    untilMs = o.getLong("until"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeSuppressions(list: List<Suppression>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { s ->
                arr.put(
                    JSONObject().apply {
                        put("pkg", s.packageName)
                        put("l", s.left.toDouble()); put("t", s.top.toDouble())
                        put("r", s.right.toDouble()); put("b", s.bottom.toDouble())
                        put("until", s.untilMs)
                    }
                )
            }
            suppressionFile.writeText(arr.toString())
        }
    }

    private companion object {
        const val TAG = "DetectionLog"
        const val MAX_RECORDS = 200

        /** TZ FR-304: xato deb belgilangan hudud shuncha vaqt blur qilinmaydi. */
        const val SUPPRESSION_MS = 60 * 60 * 1000L
    }
}
