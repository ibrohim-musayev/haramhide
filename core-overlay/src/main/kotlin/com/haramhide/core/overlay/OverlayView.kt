package com.haramhide.core.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View

/**
 * Masklarni chizadigan to'liq ekranli shaffof View.
 *
 * Blur manbai — **joriy kadrning kichraytirilgan nusxasi**. Ya'ni biz ekranning
 * ostidagi kontentni blur qilmaymiz (bunga API yo'q), balki uni capture'dan olib,
 * blur qilib, ustiga qayta chizamiz.
 *
 * MUHIM: bu chizilgan blur keyingi capture kadriga ham tushadi — TZ C-04 dagi
 * qayta aloqa halqasi aynan shundan kelib chiqadi. Uni [MaskStateMachine] hal qiladi.
 */
@SuppressLint("ViewConstructor")
class OverlayView(context: Context) : View(context) {

    private class Item(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val alpha: Float,
        /** Sinov paytida markazi ochiq qoldiriladi (ADR-007). 0 = to'liq yopiq. */
        val holeFraction: Float,
    )

    private var blurBitmap: Bitmap? = null
    private var items: List<Item> = emptyList()
    private var spec: BlurSpec = BlurSpec()

    /** Butun ekranni yopuvchi qalqon — himoya o'chiq yoki FLAG_SECURE (TZ FR-103/104). */
    private var shieldActive = false
    private var shieldText: String? = null

    /** Scroll Shield — TZ FR-108. Tez scroll paytida butun kontentga yengil blur. */
    private var scrollShield = false

    /** F0 diagnostika qatori (chap yuqorida). Relizda o'chiriladi. */
    private var debugText: String? = null

    private val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { isAntiAlias = true }
    private val plainPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val solidPaint = Paint().apply { color = Color.rgb(24, 24, 28) }
    private val shieldPaint = Paint().apply { color = Color.rgb(12, 12, 16) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val srcRect = Rect()
    private val dstRect = RectF()
    private val holeRect = RectF()

    init {
        setWillNotDraw(false)
        // Overlay tizim oynasi — o'z fonini chizmasligi kerak
        setBackgroundColor(Color.TRANSPARENT)
    }

    /**
     * Yangi kadr natijasi. Fon oqimidan chaqiriladi.
     * [small] — shu chaqiruv uchun ajratilgan bitmap, View uni egallaydi.
     */
    /** [MaskConfig.probeHoleFraction] dan keladi. */
    var probeHoleFraction: Float = 0.45f

    fun submit(
        small: Bitmap?,
        masks: List<Mask>,
        spec: BlurSpec,
        scrollShield: Boolean = false,
        debugText: String? = null,
    ) {
        val list = ArrayList<Item>(masks.size)
        for (m in masks) {
            if (!m.isVisible) continue
            val r = m.rect
            list += Item(
                r.left, r.top, r.right, r.bottom, m.alpha,
                holeFraction = if (m.isProbing) probeHoleFraction else 0f,
            )
        }
        post {
            val old = blurBitmap
            blurBitmap = small
            items = list
            this.spec = spec
            this.scrollShield = scrollShield
            this.debugText = debugText
            if (old != null && old !== small) old.recycle()
            invalidate()
        }
    }

    fun setShield(active: Boolean, text: String? = null) {
        post {
            shieldActive = active
            shieldText = text
            invalidate()
        }
    }

    fun clear() {
        post {
            items = emptyList()
            blurBitmap?.recycle()
            blurBitmap = null
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (shieldActive) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shieldPaint)
            val t = shieldText
            if (!t.isNullOrEmpty()) {
                textPaint.textSize = width * 0.045f
                canvas.drawText(t, width / 2f, height / 2f, textPaint)
            }
            return
        }

        val list = items
        val bmp = blurBitmap

        // Scroll Shield: aniqlash kechikishi (~150-250 ms) tufayli tez scroll
        // paytida kontent blur qo'yilgunicha ko'rinib qoladi. Shuning uchun
        // butun kontentga yengil blur qo'yiladi (TZ 4.3 / FR-108).
        if (scrollShield && bmp != null && !bmp.isRecycled) {
            srcRect.set(0, 0, bmp.width, bmp.height)
            dstRect.set(0f, 0f, width.toFloat(), height.toFloat())
            filterPaint.alpha = SCROLL_SHIELD_ALPHA
            canvas.drawBitmap(bmp, srcRect, dstRect, filterPaint)
        }

        drawDebug(canvas)
        if (list.isEmpty()) return

        for (item in list) {
            dstRect.set(
                item.left * width,
                item.top * height,
                item.right * width,
                item.bottom * height,
            )
            val a = (item.alpha.coerceIn(0f, 1f) * 255).toInt()

            // Sinov paytida markaz ochiq qoldiriladi (ADR-007). clipOutRect
            // API 26 dan bor, ya'ni minSdk bilan mos.
            val hole = item.holeFraction
            val clipped = hole > 0f
            if (clipped) {
                val hw = dstRect.width() * hole / 2f
                val hh = dstRect.height() * hole / 2f
                val cx = dstRect.centerX()
                val cy = dstRect.centerY()
                holeRect.set(cx - hw, cy - hh, cx + hw, cy + hh)
                canvas.save()
                canvas.clipOutRect(holeRect)
            }

            if (bmp == null || bmp.isRecycled || spec.style == BlurStyle.SOLID) {
                solidPaint.alpha = a
                canvas.drawRect(dstRect, solidPaint)
            } else {
                srcRect.set(
                    (item.left * bmp.width).toInt().coerceIn(0, bmp.width - 1),
                    (item.top * bmp.height).toInt().coerceIn(0, bmp.height - 1),
                    (item.right * bmp.width).toInt().coerceIn(1, bmp.width),
                    (item.bottom * bmp.height).toInt().coerceIn(1, bmp.height),
                )
                if (srcRect.width() > 0 && srcRect.height() > 0) {
                    val paint = if (spec.style == BlurStyle.PIXELATE) plainPaint else filterPaint
                    paint.alpha = a
                    canvas.drawBitmap(bmp, srcRect, dstRect, paint)
                }
            }

            if (clipped) canvas.restore()
        }
    }

    private fun drawDebug(canvas: Canvas) {
        val t = debugText ?: return
        textPaint.textSize = width * 0.028f
        textPaint.textAlign = Paint.Align.LEFT
        var y = height * 0.10f
        for (line in t.split('\n')) {
            // O'qilishi uchun qora kontur
            textPaint.color = Color.BLACK
            textPaint.style = Paint.Style.STROKE
            textPaint.strokeWidth = 4f
            canvas.drawText(line, width * 0.03f, y, textPaint)
            textPaint.color = Color.GREEN
            textPaint.style = Paint.Style.FILL
            canvas.drawText(line, width * 0.03f, y, textPaint)
            y += textPaint.textSize * 1.25f
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
    }

    private companion object {
        /** Scroll Shield shaffofligi — default 40% blur (TZ 4.3). */
        const val SCROLL_SHIELD_ALPHA = 160
    }
}
