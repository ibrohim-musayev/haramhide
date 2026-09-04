package com.haramhide.core.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.SystemClock
import android.util.Log

/**
 * Ekranni MediaProjection orqali oladi va kadrlarni [onFrame] ga uzatadi.
 *
 * TZ 4.2 pipeline'ining birinchi bo'g'ini. TZ C-01/C-08 ga rioya qiladi:
 * MediaProjection tokeni chaqiruvchi tomonidan bir marta olinadi va bu klass
 * uni faqat ishlatadi — qayta so'ramaydi.
 *
 * Android 14+ da `createVirtualDisplay()` dan OLDIN kamida bitta
 * `MediaProjection.Callback` ro'yxatdan o'tgan bo'lishi shart — [start] buni
 * o'zi bajaradi.
 */
class ScreenCapturer(
    private val projection: MediaProjection,
    private val config: CaptureConfig,
    private val handler: Handler,
    /** Sessiya tizim tomonidan to'xtatilganda (qulf, cast chip, boshqa ilova). TZ C-02. */
    private val onProjectionStopped: () -> Unit,
    private val onFrame: (Frame) -> Unit,
) {
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var captureBitmap: Bitmap? = null
    private var analysisBitmap: Bitmap? = null
    private var analysisCanvas: Canvas? = null
    private var analysisBuffer: AnalysisBuffer? = null

    private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val dstRect = Rect()

    private var lastFrameMs = 0L
    private var rotation = 0
    private var started = false

    /** Oxirgi kadr yetkazilgan vaqt — diagnostika uchun. */
    @Volatile var lastDeliveredAtMs: Long = 0L; private set

    /** Tashlab yuborilgan (throttle) kadrlar soni. */
    @Volatile var throttledFrames: Long = 0L; private set

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection to'xtadi (tizim tomonidan)")
            onProjectionStopped()
        }
    }

    fun start(displayWidth: Int, displayHeight: Int, densityDpi: Int, rotation: Int) {
        if (started) return
        this.rotation = rotation
        // Android 14+ talabi: callback createVirtualDisplay'dan oldin.
        projection.registerCallback(projectionCallback, handler)
        createPipeline(displayWidth, displayHeight, densityDpi)
        started = true
    }

    /** Rotatsiya / split-screen / foldable o'zgarishida. TZ FR-107. */
    fun resize(displayWidth: Int, displayHeight: Int, densityDpi: Int, rotation: Int) {
        if (!started) return
        this.rotation = rotation
        val (cw, ch) = computeCaptureSize(displayWidth, displayHeight)
        val vd = virtualDisplay ?: return
        val oldReader = imageReader
        val reader = ImageReader.newInstance(cw, ch, android.graphics.PixelFormat.RGBA_8888, MAX_IMAGES)
        reader.setOnImageAvailableListener(imageListener, handler)
        imageReader = reader
        vd.resize(cw, ch, densityDpi)
        vd.surface = reader.surface
        oldReader?.close()
        Log.i(TAG, "Capture o'lchami o'zgardi: ${cw}x$ch rot=$rotation")
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { projection.unregisterCallback(projectionCallback) }
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        captureBitmap?.recycle(); captureBitmap = null
        analysisBitmap?.recycle(); analysisBitmap = null
        analysisCanvas = null
        analysisBuffer = null
    }

    private fun createPipeline(displayWidth: Int, displayHeight: Int, densityDpi: Int) {
        val (cw, ch) = computeCaptureSize(displayWidth, displayHeight)
        val reader = ImageReader.newInstance(cw, ch, android.graphics.PixelFormat.RGBA_8888, MAX_IMAGES)
        reader.setOnImageAvailableListener(imageListener, handler)
        imageReader = reader
        virtualDisplay = projection.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            cw, ch, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        )
        Log.i(TAG, "Capture boshlandi: ${cw}x$ch dpi=$densityDpi")
    }

    private val imageListener = ImageReader.OnImageAvailableListener { reader ->
        // Backpressure: CONFLATE — faqat eng oxirgi kadr, eskilari tashlanadi (TZ 4.2).
        val image: Image = try {
            reader.acquireLatestImage()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "acquireLatestImage: $e"); null
        } ?: return@OnImageAvailableListener

        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameMs < config.minFrameIntervalMs) {
                throttledFrames++
                return@OnImageAvailableListener
            }
            lastFrameMs = now
            val frame = buildFrame(image, now)
            if (frame != null) {
                lastDeliveredAtMs = now
                onFrame(frame)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Kadrni ishlashda xato", t)
        } finally {
            image.close()
        }
    }

    private fun buildFrame(image: Image, nowMs: Long): Frame? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        if (pixelStride == 0) return null

        val w = image.width
        val h = image.height
        val paddedW = rowStride / pixelStride
        if (paddedW <= 0 || h <= 0) return null

        var bmp = captureBitmap
        if (bmp == null || bmp.width != paddedW || bmp.height != h) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(paddedW, h, Bitmap.Config.ARGB_8888)
            captureBitmap = bmp
        }
        buffer.rewind()
        bmp.copyPixelsFromBuffer(buffer)

        val analysis = ensureAnalysis(w, h) ?: return null
        srcRect.set(0, 0, w, h)
        dstRect.set(0, 0, analysis.width, analysis.height)
        analysisCanvas!!.drawBitmap(bmp, srcRect, dstRect, scalePaint)
        analysisBitmap!!.getPixels(
            analysis.pixels, 0, analysis.width, 0, 0, analysis.width, analysis.height,
        )
        analysis.computeLuma()

        return Frame(
            bitmap = bmp,
            width = w,
            height = h,
            paddedWidth = paddedW,
            analysis = analysis,
            timestampMs = nowMs,
            rotation = rotation,
        )
    }

    private fun ensureAnalysis(w: Int, h: Int): AnalysisBuffer? {
        val (aw, ah) = computeAnalysisSize(w, h)
        if (aw <= 0 || ah <= 0) return null
        var buf = analysisBuffer
        if (buf == null || buf.width != aw || buf.height != ah) {
            analysisBitmap?.recycle()
            val ab = Bitmap.createBitmap(aw, ah, Bitmap.Config.ARGB_8888)
            analysisBitmap = ab
            analysisCanvas = Canvas(ab)
            buf = AnalysisBuffer(aw, ah)
            analysisBuffer = buf
        }
        return buf
    }

    private fun computeCaptureSize(w: Int, h: Int): Pair<Int, Int> =
        fitInto(w, h, config.maxCaptureDimension)

    private fun computeAnalysisSize(w: Int, h: Int): Pair<Int, Int> =
        fitInto(w, h, config.analysisDimension)

    companion object {
        private const val TAG = "ScreenCapturer"
        private const val VIRTUAL_DISPLAY_NAME = "HaramHideCapture"
        private const val MAX_IMAGES = 2

        /** Nisbatni saqlab, uzun tomonni [maxDim] ga sig'diradi. Juft songa yaxlitlaydi. */
        fun fitInto(w: Int, h: Int, maxDim: Int): Pair<Int, Int> {
            if (w <= 0 || h <= 0) return 0 to 0
            val longer = maxOf(w, h)
            if (longer <= maxDim) return even(w) to even(h)
            val scale = maxDim.toFloat() / longer
            return even((w * scale).toInt()) to even((h * scale).toInt())
        }

        private fun even(v: Int): Int = (v - (v % 2)).coerceAtLeast(2)
    }
}
