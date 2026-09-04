package com.haramhide.app

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import kotlin.math.min

/**
 * **F0 test namunasi.** TZ FR-105 qabul mezonini o'lchash uchun.
 *
 * Qabul mezoni: "statik NSFW rasm ustida 10 s davomida 0 ta miltillash".
 * Haqiqiy NSFW material bilan test qilish huquqiy va etik jihatdan mumkin emas
 * (TZ 8.6 — qat'iy taqiq), shuning uchun bu yerda **sintetik namuna** chiziladi:
 *
 *  - teri rangiga yaqin katta soha (detektorning 1-signali)
 *  - uning ichida mayda tekstura (detektorning 2-signali — chekka energiyasi)
 *
 * Bu [HeuristicDetector] ni aynan haqiqiy modeldagidek ishga tushiradi va
 * blur qo'yilgach chekka energiyasi yo'qolib, C-04 halqasini keltirib chiqaradi.
 * Ya'ni bu namuna **muammoni ko'rsatish uchun ataylab qiyin** qilib tanlangan.
 *
 * Ekran mutlaqo statik — ilovaning o'z UI'si o'lchovni ifloslantirmaydi.
 */
class TestPatternActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val photo = intent?.getStringExtra(EXTRA_PHOTO)
        setContentView(
            if (photo != null) PhotoView(this, photo) else PatternView(this)
        )
    }

    /**
     * Haqiqiy rasmni to'liq ekranda ko'rsatadi — modelni sinash uchun.
     *
     * Rasm ilovaning `filesDir` idan o'qiladi va **repozitoriyaga qo'shilmaydi**.
     * Sinov uchun quyidagicha joylanadi:
     * ```
     * adb shell "cat /data/local/tmp/x.jpg | run-as com.haramhide.app.debug tee files/x.jpg >/dev/null"
     * ```
     */
    private class PhotoView(context: Context, name: String) : View(context) {
        private val bitmap: Bitmap? = runCatching {
            BitmapFactory.decodeFile(java.io.File(context.filesDir, name).absolutePath)
        }.getOrNull()

        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val src = Rect()
        private val dst = Rect()
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            val bmp = bitmap
            if (bmp == null) {
                label.textSize = width * 0.04f
                canvas.drawText("Rasm topilmadi", width / 2f, height / 2f, label)
                return
            }
            // Nisbatni saqlab, markazga joylashtirish
            val scale = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
            val w = (bmp.width * scale).toInt()
            val h = (bmp.height * scale).toInt()
            src.set(0, 0, bmp.width, bmp.height)
            dst.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            canvas.drawBitmap(bmp, src, dst, paint)
        }
    }

    companion object {
        const val EXTRA_PHOTO = "photo"
    }

    private class PatternView(context: Context) : View(context) {
        private var bitmap: Bitmap? = null
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val src = Rect()
        private val dst = Rect()
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) bitmap = buildPattern(w / 2, h / 2)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(BACKGROUND)
            val bmp = bitmap ?: return
            src.set(0, 0, bmp.width, bmp.height)
            dst.set(0, 0, width, height)
            canvas.drawBitmap(bmp, src, dst, paint)

            label.textSize = width * 0.032f
            canvas.drawText("F0 test namunasi — statik", width / 2f, height * 0.06f, label)
            canvas.drawText("10 s kuting, MILTILLASH ni tekshiring", width / 2f, height * 0.10f, label)
        }

        /**
         * Deterministik namuna — har ishga tushishda bir xil, ya'ni
         * o'lchovlar takrorlanadigan bo'ladi.
         */
        private fun buildPattern(w: Int, h: Int): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(w * h)

            val cx = w / 2f
            val cy = h / 2f
            val rx = w * 0.30f
            val ry = h * 0.22f

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val nx = (x - cx) / rx
                    val ny = (y - cy) / ry
                    val inside = nx * nx + ny * ny <= 1f

                    val color = if (inside) {
                        // Teri rangi + mayda tekstura.
                        // Tekstura chekka energiyasini beradi — blur uni yo'q qiladi.
                        val noise = (((x * 37 + y * 61) xor (x * 17)) and 0x3F) - 32
                        val r = (SKIN_R + noise).coerceIn(96, 255)
                        val g = (SKIN_G + noise / 2).coerceIn(41, 255)
                        val b = (SKIN_B + noise / 3).coerceIn(21, 255)
                        Color.rgb(r, g, b)
                    } else {
                        // Neytral fon — detektor bunga reaksiya qilmasligi kerak
                        val v = 70 + ((x / 24 + y / 24) and 1) * 8
                        Color.rgb(v, v + 6, v + 14)
                    }
                    pixels[y * w + x] = color
                }
            }
            bmp.setPixels(pixels, 0, w, 0, 0, w, h)
            return bmp
        }

        private companion object {
            const val BACKGROUND = 0xFF35404A.toInt()

            // isSkin() qoidasidan o'tadigan qiymatlar (R>95, G>40, B>20, R>G>B, |R-G|>15)
            const val SKIN_R = 208
            const val SKIN_G = 158
            const val SKIN_B = 128
        }
    }
}
