package com.haramhide.core.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/**
 * **TZ FR-208 — tap-to-unblur.**
 *
 * Mask burchagida paydo bo'ladigan kichik tugma. 2 soniya bosib turilsa,
 * blur 5 soniyaga ochiladi. Kunlik limit bilan cheklangan.
 *
 * ### Nega butun mask emas, kichik tugma
 * Overlay oynasini teginiladigan qilish tegishni ostidagi ilovaga o'tkazmaydi —
 * Android'da bunday imkoniyat yo'q. Ya'ni butun mask teginiladigan bo'lsa,
 * uning ostidagi kontentni scroll qilib ham bo'lmaydi va mask katta bo'lsa
 * ilova amalda ishlamay qoladi.
 *
 * Shuning uchun teginiladigan hudud 48 dp bilan cheklangan — bu Material
 * minimal tegish o'lchami (TZ 7-bo'lim, accessibility talabi) va u scroll'ga
 * xalaqit bermaydi.
 */
@SuppressLint("ViewConstructor")
class UnblurHandle(
    context: Context,
    private val holdDurationMs: Long,
    private val onUnblurRequested: () -> Unit,
) : View(context) {

    private var pressStartMs = 0L
    private var progress = 0f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 20, 20, 24)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(220, 255, 255, 255)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }

    private val tick = object : Runnable {
        override fun run() {
            if (pressStartMs == 0L) return
            val held = SystemClock.elapsedRealtime() - pressStartMs
            progress = (held.toFloat() / holdDurationMs).coerceIn(0f, 1f)
            invalidate()
            if (progress >= 1f) {
                pressStartMs = 0L
                progress = 0f
                invalidate()
                onUnblurRequested()
            } else {
                postOnAnimation(this)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressStartMs = SystemClock.elapsedRealtime()
                postOnAnimation(tick)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressStartMs = 0L
                progress = 0f
                invalidate()
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 4f

        canvas.drawCircle(cx, cy, r, bgPaint)

        // "Ko'z" belgisi
        val e = r * 0.55f
        canvas.drawArc(cx - e, cy - e * 0.62f, cx + e, cy + e * 0.62f, 0f, 360f, false, iconPaint)
        canvas.drawCircle(cx, cy, e * 0.22f, iconPaint)

        // Bosib turish jarayoni
        if (progress > 0f) {
            canvas.drawArc(
                cx - r + 3f, cy - r + 3f, cx + r - 3f, cy + r - 3f,
                -90f, 360f * progress, false, ringPaint,
            )
        }
    }
}
