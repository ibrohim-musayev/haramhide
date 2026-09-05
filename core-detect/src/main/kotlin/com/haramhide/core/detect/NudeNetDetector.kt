package com.haramhide.core.detect

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.haramhide.core.capture.Frame
import java.nio.FloatBuffer
import kotlin.math.min

/**
 * **Stage B — haqiqiy detektor.** NudeNet v3 `320n.onnx` (YOLOv8n asosida).
 *
 * Model litsenziyasi AGPL-3.0 — `assets/MODEL_NOTICE.txt` va ADR-002 ga qarang.
 *
 * ### Nega kirish kvadrat emas
 * NudeNet ning Python kodi rasmni kvadratga to'ldirib (pad), keyin 320x320 ga
 * kichraytiradi. Telefon ekrani uchun bu falokat: 1080x2400 ekran 2400x2400
 * kvadratga to'ldiriladi va kontent 320 px dan atigi **144 px** kenglikda qoladi.
 *
 * Model kirishi dinamik (`images: [batch, 3, height, width]`), shuning uchun
 * bu yerda nisbatga mos to'rtburchak kirish ishlatiladi (default 320x640).
 * O'sha ekran uchun kontent 288x640 bo'ladi — ikki barobar ko'p detal.
 * Hisoblash narxi 320² ga nisbatan ~2x, lekin aniqlik farqi bundan kattaroq.
 *
 * ### Kvantizatsiya
 * Bajarilmagan. INT8 kalibrlash uchun haqiqiy rasmlar to'plami kerak, u hozircha
 * yo'q (TZ 8.5). FP32 12 MB APK'ga sig'adi. Golden set yig'ilgach qayta ko'riladi.
 */
class NudeNetDetector(
    context: Context,
    config: DetectorConfig = DetectorConfig.TIER_B,
) : StageBDetector {

    private val inputWidth = config.inputWidth
    private val inputHeight = config.inputHeight
    private val threads = config.threads

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var inputName: String = "images"

    // Qayta ishlatiladigan buferlar — har kadrda ajratish GC bosimini beradi
    private val inputBitmap =
        Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
    private val inputCanvas = Canvas(inputBitmap)
    private val pixels = IntArray(inputWidth * inputHeight)
    private val tensorBuffer: FloatBuffer =
        FloatBuffer.allocate(3 * inputWidth * inputHeight)
    private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val dstRect = Rect()

    /**
     * Chiqish uchun qayta ishlatiladigan massiv.
     * 320x640 da chiqish 22 x 4200 = 92 400 float = 370 KB. Uni har kadrda
     * ajratish 5 fps da sekundiga ~1.8 MB axlat degani.
     */
    private var outputBuffer = FloatArray(0)

    @Volatile var lastInferenceMs: Long = 0L; private set
    @Volatile var isReady: Boolean = false; private set
    @Volatile var loadError: String? = null; private set

    /**
     * Yalang'och erkak ko'kragini blur qilish (`MALE_BREAST_EXPOSED`).
     * Sozlamalardan keladi. Erkak avrati bunga bog'liq emas —
     * u har doim blur qilinadi.
     */
    @Volatile var blurMaleChest: Boolean = true

    /**
     * Diagnostika: chegaradan QAT'I NAZAR eng yuqori 3 ta klass.
     *
     * "Model hech narsa ko'rmayapti" va "model ko'ryapti, lekin chegaradan
     * past" — bular butunlay boshqa muammolar va ularni faqat shu ma'lumot
     * ajratadi. Metrikada `top=` sifatida chiqadi.
     */
    @Volatile var lastTopClasses: String = ""; private set

    /** Ishga tushishdagi mikro-benchmark medianasi (TZ NFR-201). */
    @Volatile var benchmarkMs: Long = -1L; private set

    /** Bosqichma-bosqich vaqt — sekinlik qayerdan kelayotganini ajratish uchun. */
    @Volatile var lastPreprocessMs: Long = 0L; private set
    @Volatile var lastRunMs: Long = 0L; private set
    @Volatile var lastPostprocessMs: Long = 0L; private set

    // Yugurib boruvchi statistika. "Oxirgi qiymat" aldamchi: statik ekranda
    // Stage B kam ishlaydi va bitta chetdagi namuna log'da muzlab qoladi.
    @Volatile private var runCount = 0L
    @Volatile private var runSumMs = 0L
    @Volatile var runMinMs: Long = Long.MAX_VALUE; private set
    @Volatile var runMaxMs: Long = 0L; private set

    val runAvgMs: Long get() = if (runCount == 0L) 0L else runSumMs / runCount
    val runSamples: Long get() = runCount

    fun resetStats() {
        runCount = 0; runSumMs = 0; runMinMs = Long.MAX_VALUE; runMaxMs = 0
    }

    init {
        try {
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(threads)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val s = env.createSession(bytes, options)
            inputName = s.inputNames.firstOrNull() ?: "images"
            session = s
            isReady = true
            Log.i(TAG, "Model yuklandi (${bytes.size / 1024} KB), kirish=$inputName ${inputWidth}x$inputHeight, oqim=$threads")
            benchmark(s)
        } catch (t: Throwable) {
            loadError = t.message ?: t::class.java.simpleName
            Log.e(TAG, "Modelni yuklab bo'lmadi", t)
        }
    }

    /**
     * Mikro-benchmark (TZ NFR-201). Birinchi chaqiruv har doim sekin —
     * xotira ajratish, kernel tanlash, kesh isishi. Shuning uchun 1 ta isitish
     * va 3 ta o'lchov qilinadi, natija — mediana.
     *
     * Bu qiymat qurilma tier'ini aniqlash uchun ishlatiladi (TZ 6.1).
     */
    /**
     * Ishlayotgan pipeline ustida qayta o'lchash.
     *
     * **Oxirgi haqiqiy kadr ma'lumoti** bilan ishlaydi, nol bufer bilan emas —
     * aks holda benchmark boshqa ishni o'lchagan bo'lardi.
     */
    fun benchmarkNow(runs: Int = 5) {
        session?.let { benchmark(it, runs, useLastFrame = true) }
    }

    /**
     * Bir nechta kirish o'lchamini taqqoslaydi.
     *
     * Model kirishi dinamik (`[batch, 3, height, width]`), shuning uchun buni
     * bitta sessiya bilan qilish mumkin — qayta yuklash shart emas.
     * Natija kirish o'lchamini tanlash uchun asos bo'ladi.
     */
    fun sweep(runs: Int = 3) {
        val s = session ?: return
        for ((w, h) in SWEEP_SIZES) {
            try {
                val buf = FloatBuffer.allocate(3 * w * h)
                val times = ArrayList<Long>(runs)
                repeat(runs + 1) { i ->
                    buf.rewind()
                    val t0 = SystemClock.elapsedRealtimeNanos()
                    OnnxTensor.createTensor(env, buf, longArrayOf(1, 3, h.toLong(), w.toLong()))
                        .use { input ->
                            s.run(mapOf(inputName to input)).use { r ->
                                val out = r[0] as OnnxTensor
                                val fb = out.floatBuffer
                                val tmp = FloatArray(fb.remaining())
                                fb.get(tmp)
                            }
                        }
                    if (i > 0) times += (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
                }
                times.sort()
                Log.i(TAG, "Sweep ${w}x$h: mediana=${times[times.size / 2]}ms hammasi=$times")
            } catch (t: Throwable) {
                Log.w(TAG, "Sweep ${w}x$h bajarilmadi: $t")
            }
        }
    }

    private fun benchmark(s: OrtSession, runs: Int = 3, useLastFrame: Boolean = false) {
        try {
            val buf = if (useLastFrame) tensorBuffer
            else FloatBuffer.allocate(3 * inputWidth * inputHeight)
            val times = ArrayList<Long>(runs)
            repeat(runs + 1) { i ->
                buf.rewind()
                val t0 = SystemClock.elapsedRealtimeNanos()
                OnnxTensor.createTensor(
                    env, buf, longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong()),
                ).use { input ->
                    s.run(mapOf(inputName to input)).use { result ->
                        // Chiqishni O'QIYMIZ — aks holda benchmark detect() dan
                        // kamroq ish qilib, aldamchi past raqam beradi.
                        val out = result[0] as OnnxTensor
                        val fb = out.floatBuffer
                        val n = fb.remaining()
                        if (outputBuffer.size != n) outputBuffer = FloatArray(n)
                        fb.get(outputBuffer)
                    }
                }
                val ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
                if (i > 0) times += ms   // birinchisi — isitish, hisobga olinmaydi
            }
            times.sort()
            benchmarkMs = times[times.size / 2]
            Log.i(
                TAG,
                "Benchmark ${inputWidth}x$inputHeight (${if (useLastFrame) "haqiqiy kadr" else "nol"}): " +
                    "mediana=${benchmarkMs}ms hammasi=$times | " +
                    "detect o'rtacha=${runAvgMs}ms min=${if (runMinMs == Long.MAX_VALUE) 0 else runMinMs} " +
                    "max=${runMaxMs}ms namuna=$runCount",
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Benchmark bajarilmadi: $t")
        }
    }

    override fun detect(
        frame: Frame,
        minConfidence: Float,
        sensitivity: Sensitivity,
    ): List<Detection> {
        val s = session ?: return emptyList()
        if (frame.bitmap.isRecycled || frame.width <= 0 || frame.height <= 0) return emptyList()

        // --- Letterbox: nisbatni saqlab, chap yuqoriga joylashtirish
        val scale = min(
            inputWidth.toFloat() / frame.width,
            inputHeight.toFloat() / frame.height,
        )
        val contentW = (frame.width * scale)
        val contentH = (frame.height * scale)

        val tPre = SystemClock.elapsedRealtimeNanos()
        inputCanvas.drawColor(Color.BLACK)
        srcRect.set(0, 0, frame.width, frame.height)
        dstRect.set(0, 0, contentW.toInt().coerceAtLeast(1), contentH.toInt().coerceAtLeast(1))
        inputCanvas.drawBitmap(frame.bitmap, srcRect, dstRect, scalePaint)
        inputBitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        // --- NCHW float32, RGB, 1/255
        val plane = inputWidth * inputHeight
        val buf = tensorBuffer
        buf.clear()
        val arr = buf.array()
        for (i in 0 until plane) {
            val p = pixels[i]
            arr[i] = ((p shr 16) and 0xFF) * INV_255              // R
            arr[plane + i] = ((p shr 8) and 0xFF) * INV_255       // G
            arr[2 * plane + i] = (p and 0xFF) * INV_255           // B
        }
        buf.rewind()
        lastPreprocessMs = (SystemClock.elapsedRealtimeNanos() - tPre) / 1_000_000

        val t0 = SystemClock.elapsedRealtimeNanos()
        val anchors: Int
        try {
            OnnxTensor.createTensor(
                env, buf, longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong()),
            ).use { input ->
                s.run(mapOf(inputName to input)).use { result ->
                    val out = result[0] as OnnxTensor
                    anchors = out.info.shape[2].toInt()   // [1, 22, N]
                    val fb = out.floatBuffer
                    val n = fb.remaining()
                    if (outputBuffer.size != n) outputBuffer = FloatArray(n)
                    fb.get(outputBuffer)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Inference xatosi", t)
            return emptyList()
        }
        val raw = outputBuffer
        lastRunMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
        runCount++
        runSumMs += lastRunMs
        if (lastRunMs < runMinMs) runMinMs = lastRunMs
        if (lastRunMs > runMaxMs) runMaxMs = lastRunMs

        val tPost = SystemClock.elapsedRealtimeNanos()
        val out = postprocess(raw, anchors, contentW, contentH, minConfidence, sensitivity)
        lastPostprocessMs = (SystemClock.elapsedRealtimeNanos() - tPost) / 1_000_000
        lastInferenceMs = lastPreprocessMs + lastRunMs + lastPostprocessMs
        return out
    }

    /**
     * Chiqish `[1, 4 + 18, N]` — kanal bo'yicha joylashgan.
     * Anker `i` uchun: `cx = raw[0*N+i]`, `cy = raw[1*N+i]`, `w`, `h`,
     * klass `j` bali `raw[(4+j)*N+i]`.
     *
     * NudeNet argmax'ni **barcha** klasslar ustidan oladi, keyin chegara qo'yadi.
     * Biz ham shunday qilamiz: avval g'olib klassni topamiz, keyin uni
     * blur qilish kerakmi yo'qmi deb qaraymiz. Filtrni argmax'dan OLDIN
     * qo'yish soxta ijobiylar beradi — masalan yuz eng yuqori ball olgan
     * ankerda past ballli boshqa klass g'olib bo'lib qolardi.
     */
    private fun postprocess(
        raw: FloatArray,
        anchors: Int,
        contentW: Float,
        contentH: Float,
        minConfidence: Float,
        sensitivity: Sensitivity,
    ): List<Detection> {
        if (anchors <= 0 || contentW <= 0f || contentH <= 0f) return emptyList()

        // Diagnostika: har bir klass bo'yicha eng yuqori ball (chegarasiz)
        val perClassMax = FloatArray(NudeNetLabels.COUNT)
        for (c in 0 until NudeNetLabels.COUNT) {
            var best = 0f
            val base = (4 + c) * anchors
            for (i in 0 until anchors) {
                val v = raw[base + i]
                if (v > best) best = v
            }
            perClassMax[c] = best
        }
        lastTopClasses = perClassMax.indices
            .sortedByDescending { perClassMax[it] }
            .take(3)
            .joinToString(",") { "${NudeNetLabels.nameOf(it)}=%.2f".format(perClassMax[it]) }

        val threshold = minConfidence.coerceAtLeast(MIN_SCORE)
        val candidates = ArrayList<Detection>(16)

        for (i in 0 until anchors) {
            var bestScore = 0f
            var bestClass = -1
            for (c in 0 until NudeNetLabels.COUNT) {
                val v = raw[(4 + c) * anchors + i]
                if (v > bestScore) { bestScore = v; bestClass = c }
            }
            if (bestClass < 0 || bestScore < threshold) continue
            if (!NudeNetLabels.isBlurred(bestClass, sensitivity, blurMaleChest)) continue

            val cx = raw[i]
            val cy = raw[anchors + i]
            val w = raw[2 * anchors + i]
            val h = raw[3 * anchors + i]

            // Kirish fazasidan kontent fazasiga, so'ng normallashtirishga
            val left = ((cx - w / 2f) / contentW).coerceIn(0f, 1f)
            val top = ((cy - h / 2f) / contentH).coerceIn(0f, 1f)
            val right = ((cx + w / 2f) / contentW).coerceIn(0f, 1f)
            val bottom = ((cy + h / 2f) / contentH).coerceIn(0f, 1f)
            if (right <= left || bottom <= top) continue

            candidates += Detection(
                left, top, right, bottom,
                score = bestScore,
                label = NudeNetLabels.nameOf(bestClass),
            )
        }

        return nms(candidates, NMS_IOU)
    }

    /** Greedy NMS. NudeNet Python kodidagi IoU chegarasi 0.45. */
    private fun nms(input: List<Detection>, iouThreshold: Float): List<Detection> {
        if (input.size <= 1) return input
        val sorted = input.sortedByDescending { it.score }
        val kept = ArrayList<Detection>(sorted.size)
        val suppressed = BooleanArray(sorted.size)
        for (i in sorted.indices) {
            if (suppressed[i]) continue
            val a = sorted[i]
            kept += a
            for (j in i + 1 until sorted.size) {
                if (!suppressed[j] && a.iou(sorted[j]) > iouThreshold) suppressed[j] = true
            }
        }
        return kept
    }

    override fun close() {
        runCatching { session?.close() }
        session = null
        isReady = false
        if (!inputBitmap.isRecycled) inputBitmap.recycle()
    }

    companion object {
        private const val TAG = "NudeNetDetector"
        private const val MODEL_ASSET = "nudenet_320n.onnx"
        private const val INV_255 = 1f / 255f

        /** NudeNet Python kodidagi qiymatlar. */
        private const val MIN_SCORE = 0.20f
        private const val NMS_IOU = 0.45f

        /**
         * Default kirish — 1:2 nisbat, zamonaviy telefon ekraniga yaqin.
         * 32 ga karrali bo'lishi shart (YOLOv8 stride talabi).
         */
        /** Taqqoslash uchun o'lchamlar. Hammasi 32 ga karrali. */
        private val SWEEP_SIZES = listOf(
            192 to 384,
            224 to 448,
            256 to 512,
            320 to 320,
            320 to 640,
        )
    }
}
