package com.haramhide.core.detect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
// LiteRT AAR eski paket nomini saqlab qolgan: org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Interpreter
import com.haramhide.core.capture.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * **Ochiq kiyim detektori.**
 *
 * NudeNet yalang'ochlikni biladi, lekin "ochiq kiyingan" holatni emas —
 * unda oyoq, son yoki yelka uchun sinf umuman yo'q. Bu detektor shu
 * bo'shliqni to'ldiradi.
 *
 * MediaPipe Selfie Multiclass Segmentation (Apache-2.0) kadrni 6 sinfga
 * ajratadi: `background`, `hair`, `body-skin`, `face-skin`, `clothes`,
 * `others`. Keyin oddiy nisbat hisoblanadi:
 *
 * ```
 * ochiqlik = body-skin / (body-skin + clothes)
 * ```
 *
 * **Yuz ochiqlik hisoblanmaydi** — `face-skin` alohida sinf. Bu muhim:
 * aks holda har qanday portret ochiq deb belgilanardi.
 *
 * ### O'lchangan qiymatlar (golden set, mediana)
 * ```
 * hijob            4.7 %
 * portret          3.7 %
 * kundalik UI      9.2 %
 * bola            14.6 %      <- e'tibor bering
 * ochiq kiyim     28.6 %
 * yalang'och ko'krak 46.2 %
 * sport           79.9 %
 * ```
 *
 * Default chegara 25 % — hijobga tegmaydi, ochiq kiyimni ushlaydi.
 *
 * ### Ma'lum xavf
 * Yozgi kiyimdagi bolalar 14–30 % oralig'ida bo'ladi, ya'ni chegara ularga
 * yaqin. Bu butunlay yo'q qilib bo'lmaydigan xavf va u hujjatlashtirilgan.
 */
class SkinExposureDetector(
    context: Context,
    private val threads: Int = 4,
    /**
     * Segmentatsiya shu oraliqdan tez-tez ishlamaydi.
     *
     * Real qurilmada (Helio G99) bitta chaqiruv ~400 ms turadi — har kadrda
     * ishlatib bo'lmaydi, fps 1 ga tushadi. Kiyim holati esa kadrdan kadrga
     * o'zgarmaydi, shuning uchun natija keshlanadi va qayta ishlatiladi.
     */
    private val minIntervalMs: Long = DEFAULT_INTERVAL_MS,
) {
    private var interpreter: Interpreter? = null

    /** Ochiqlik chegarasi, 0..1. Sozlamalardan keladi. */
    @Volatile var threshold: Float = DEFAULT_THRESHOLD

    @Volatile var lastRatio: Float = -1f; private set

    /** Oxirgi hisoblangan natija — keshdan qaytariladi. */
    @Volatile private var cachedDetection: Detection? = null
    @Volatile private var lastRunAtMs: Long = 0L
    @Volatile var lastPersonPixels: Int = 0; private set
    @Volatile var lastInferenceMs: Long = 0L; private set
    @Volatile var isReady: Boolean = false; private set
    @Volatile var loadError: String? = null; private set

    // Qayta ishlatiladigan buferlar
    private val inputBitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    private val inputCanvas = Canvas(inputBitmap)
    private val pixels = IntArray(SIZE * SIZE)
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val outputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(SIZE * SIZE * CLASSES * 4).order(ByteOrder.nativeOrder())
    private val labels = ByteArray(SIZE * SIZE)
    private val component = BooleanArray(SIZE * SIZE)
    private val visited = BooleanArray(SIZE * SIZE)
    private val queue = IntArray(SIZE * SIZE)
    private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val dstRect = Rect()

    init {
        try {
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buf.put(bytes).rewind()
            interpreter = Interpreter(
                buf,
                Interpreter.Options()
                    .setNumThreads(threads)
                    .setUseXNNPACK(true),
            )
            isReady = true
            Log.i(TAG, "Segmentatsiya modeli yuklandi (${bytes.size / 1024} KB)")
        } catch (t: Throwable) {
            loadError = t.message ?: t::class.java.simpleName
            Log.e(TAG, "Segmentatsiya modelini yuklab bo'lmadi", t)
        }
    }

    /**
     * @return ochiq kiyim aniqlansa [Detection], aks holda null.
     */
    fun detect(frame: Frame): Detection? {
        val itp = interpreter ?: return null
        if (frame.bitmap.isRecycled || frame.width <= 0 || frame.height <= 0) return null

        // Throttle: og'ir model, kiyim holati esa sekin o'zgaradi
        val now = SystemClock.elapsedRealtime()
        if (now - lastRunAtMs < minIntervalMs) return cachedDetection
        lastRunAtMs = now

        // Letterbox: nisbatni saqlab, markazga
        val scale = min(SIZE.toFloat() / frame.width, SIZE.toFloat() / frame.height)
        val cw = (frame.width * scale).toInt().coerceAtLeast(1)
        val ch = (frame.height * scale).toInt().coerceAtLeast(1)
        val ox = (SIZE - cw) / 2
        val oy = (SIZE - ch) / 2

        inputCanvas.drawColor(Color.BLACK)
        srcRect.set(0, 0, frame.width, frame.height)
        dstRect.set(ox, oy, ox + cw, oy + ch)
        inputCanvas.drawBitmap(frame.bitmap, srcRect, dstRect, scalePaint)
        inputBitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)

        inputBuffer.rewind()
        for (p in pixels) {
            inputBuffer.putFloat(((p shr 16) and 0xFF) * INV_255)
            inputBuffer.putFloat(((p shr 8) and 0xFF) * INV_255)
            inputBuffer.putFloat((p and 0xFF) * INV_255)
        }
        inputBuffer.rewind()
        outputBuffer.rewind()

        val t0 = SystemClock.elapsedRealtimeNanos()
        try {
            itp.run(inputBuffer, outputBuffer)
        } catch (t: Throwable) {
            Log.e(TAG, "Segmentatsiya xatosi", t)
            return null
        }
        lastInferenceMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000

        outputBuffer.rewind()
        argmax()
        val personPixels = largestComponent()
        lastPersonPixels = personPixels
        if (personPixels < MIN_PERSON_PIXELS) {
            lastRatio = -1f
            cachedDetection = null
            return null
        }

        var skin = 0
        var cloth = 0
        var minX = SIZE; var minY = SIZE; var maxX = -1; var maxY = -1
        for (i in labels.indices) {
            if (!component[i]) continue
            when (labels[i].toInt()) {
                BODY_SKIN -> skin++
                CLOTHES -> cloth++
            }
            val x = i % SIZE
            val y = i / SIZE
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
        val denom = skin + cloth
        if (denom == 0 || maxX < 0) { lastRatio = -1f; cachedDetection = null; return null }

        val ratio = skin.toFloat() / denom
        lastRatio = ratio
        if (ratio < threshold) { cachedDetection = null; return null }

        // Letterbox ofsetini qaytarib, normallashtirilgan koordinataga
        val l = ((minX - ox).toFloat() / cw).coerceIn(0f, 1f)
        val t = ((minY - oy).toFloat() / ch).coerceIn(0f, 1f)
        val r = ((maxX + 1 - ox).toFloat() / cw).coerceIn(0f, 1f)
        val b = ((maxY + 1 - oy).toFloat() / ch).coerceIn(0f, 1f)
        if (r <= l || b <= t) return null

        return Detection(l, t, r, b, score = ratio, label = "SKIN_EXPOSURE")
            .also { cachedDetection = it }
    }

    /** Chiqishdagi 6 kanaldan eng kattasini tanlaydi. */
    private fun argmax() {
        val fb = outputBuffer.asFloatBuffer()
        var idx = 0
        for (i in labels.indices) {
            var best = fb.get(idx)
            var bestC = 0
            for (c in 1 until CLASSES) {
                val v = fb.get(idx + c)
                if (v > best) { best = v; bestC = c }
            }
            labels[i] = bestC.toByte()
            idx += CLASSES
        }
    }

    /**
     * Eng katta bog'langan odam komponenti.
     *
     * Kerak, chunki model ilova interfeysining ba'zi qismlarini ham teri deb
     * belgilaydi — YouTube'ning yon paneli `face-skin` sifatida chiqqani
     * amalda kuzatilgan. Faqat eng katta bo'lak — ya'ni odamning o'zi —
     * hisobga olinadi.
     */
    private fun largestComponent(): Int {
        java.util.Arrays.fill(visited, false)
        java.util.Arrays.fill(component, false)
        var bestStart = -1
        var bestSize = 0
        val order = IntArray(labels.size)
        var orderN: Int

        for (start in labels.indices) {
            if (visited[start] || labels[start].toInt() == BACKGROUND) continue
            var head = 0; var tail = 0
            queue[tail++] = start
            visited[start] = true
            orderN = 0
            while (head < tail) {
                val i = queue[head++]
                order[orderN++] = i
                val x = i % SIZE
                val y = i / SIZE
                if (x > 0) push(i - 1)
                if (x < SIZE - 1) push(i + 1)
                if (y > 0) push(i - SIZE)
                if (y < SIZE - 1) push(i + SIZE)
                tail = qTail
            }
            if (orderN > bestSize) { bestSize = orderN; bestStart = start }
        }
        if (bestStart < 0) return 0

        // Eng katta komponentni qayta bosib chiqamiz
        java.util.Arrays.fill(visited, false)
        var head = 0
        qTail = 0
        queue[qTail++] = bestStart
        visited[bestStart] = true
        var count = 0
        while (head < qTail) {
            val i = queue[head++]
            component[i] = true
            count++
            val x = i % SIZE
            val y = i / SIZE
            if (x > 0) push(i - 1)
            if (x < SIZE - 1) push(i + 1)
            if (y > 0) push(i - SIZE)
            if (y < SIZE - 1) push(i + SIZE)
        }
        return count
    }

    private var qTail = 0

    private fun push(i: Int) {
        if (!visited[i] && labels[i].toInt() != BACKGROUND) {
            visited[i] = true
            queue[qTail++] = i
        }
    }

    fun close() {
        runCatching { interpreter?.close() }
        interpreter = null
        isReady = false
        if (!inputBitmap.isRecycled) inputBitmap.recycle()
    }

    companion object {
        private const val TAG = "SkinExposure"
        private const val MODEL_ASSET = "selfie_multiclass_256.tflite"
        private const val SIZE = 256
        private const val CLASSES = 6
        private const val INV_255 = 1f / 255f

        private const val BACKGROUND = 0
        const val BODY_SKIN = 2
        const val CLOTHES = 4

        /** Shundan kichik "odam" e'tiborga olinmaydi (shovqin). */
        private const val MIN_PERSON_PIXELS = 400

        /** Golden set o'lchoviga asoslangan default (docs/OCHIQ-KIYIM.md). */
        const val DEFAULT_THRESHOLD = 0.25f

        /** Segmentatsiya chaqiruvlari orasidagi minimal oraliq. */
        const val DEFAULT_INTERVAL_MS = 700L
    }
}
