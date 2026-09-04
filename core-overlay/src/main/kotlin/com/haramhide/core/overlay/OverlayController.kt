package com.haramhide.core.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.haramhide.core.capture.Frame
import kotlin.math.max

/**
 * Overlay oynasini boshqaradi: WindowManager'ga qo'shish, kadr bo'yicha
 * blur manbasini tayyorlash, qalqonni yoqish/o'chirish.
 *
 * TZ C-10: ba'zi OEM'larda (Xiaomi/MIUI, Huawei) `SYSTEM_ALERT_WINDOW`
 * qo'shimcha qo'lda yoqishni talab qiladi — [canDrawOverlays] shuni tekshiradi.
 */
class OverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: OverlayView? = null
    private val srcRect = Rect()
    private val dstRect = Rect()
    private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    val isAttached: Boolean get() = view != null

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun attach(): Boolean {
        if (view != null) return true
        if (!canDrawOverlays()) {
            Log.w(TAG, "Overlay ruxsati yo'q (SYSTEM_ALERT_WINDOW)")
            return false
        }
        val v = OverlayView(context)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        return try {
            windowManager.addView(v, params)
            view = v
            Log.i(TAG, "Overlay qo'shildi")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Overlay qo'shib bo'lmadi", t)
            false
        }
    }

    fun detach() {
        val v = view ?: return
        view = null
        runCatching { windowManager.removeViewImmediate(v) }
            .onFailure { Log.w(TAG, "Overlay olib tashlashda xato: $it") }
    }

    /**
     * Kadr natijasini chizadi.
     *
     * Ko'rinadigan mask bo'lmasa blur manbasi umuman tayyorlanmaydi —
     * bu kadrlarning katta qismida bepul o'tishni beradi.
     */
    fun render(
        frame: Frame,
        masks: List<Mask>,
        spec: BlurSpec,
        scrollShield: Boolean = false,
        debugText: String? = null,
    ) {
        val v = view ?: return
        val needsSource = masks.any { it.isVisible } || scrollShield
        if (!needsSource) {
            v.submit(null, masks, spec, false, debugText)
            return
        }
        v.submit(buildBlurSource(frame, spec), masks, spec, scrollShield, debugText)
    }

    /** Butun ekranni yopish. TZ FR-103 (himoya o'chiq) va FR-104 (fail-closed). */
    fun setShield(active: Boolean, text: String? = null) {
        view?.setShield(active, text)
    }

    fun clear() {
        view?.clear()
    }

    /**
     * Blur manbai: kadrni [BlurSpec.downscale] marta kichraytirish.
     * Kichraytirib-kattalashtirish detallarni yo'qotadi — bu bizning "blur"imiz.
     * RenderEffect'dan farqli o'laroq API 26 dan boshlab ishlaydi (TZ FR-202 fallback).
     */
    private fun buildBlurSource(frame: Frame, spec: BlurSpec): Bitmap? {
        if (frame.bitmap.isRecycled) return null
        val ds = spec.downscale
        val sw = max(1, frame.width / ds)
        val sh = max(1, frame.height / ds)
        return try {
            val small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(small)
            // Faqat foydali kenglik — rowStride padding tashlab yuboriladi
            srcRect.set(0, 0, frame.width, frame.height)
            dstRect.set(0, 0, sw, sh)
            canvas.drawBitmap(frame.bitmap, srcRect, dstRect, scalePaint)
            small
        } catch (t: Throwable) {
            Log.e(TAG, "Blur manbasini tayyorlab bo'lmadi", t)
            null
        }
    }

    companion object {
        private const val TAG = "OverlayController"
    }
}
