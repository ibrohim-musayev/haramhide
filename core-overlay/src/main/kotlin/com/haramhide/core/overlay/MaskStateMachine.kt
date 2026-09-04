package com.haramhide.core.overlay

import android.util.Log
import com.haramhide.core.detect.Detection

/**
 * Kadr uchun kirish signallari.
 */
class FrameContext(
    val nowMs: Long,
    val detections: List<Detection>,
    val packageChanged: Boolean,
    val globalDelta: Int,
    val scrolling: Boolean,
    /** Berilgan to'rtburchak ATROFIDAGI halqada o'zgarish (0..255). */
    val ringDelta: (Detection) -> Int,
)

/**
 * **TZ FR-105 — Mask State Machine.**
 *
 * Muammo (TZ C-04): overlay oynasi ham VirtualDisplay'ga tushadi. Blur qo'yilgach
 * detektor blur'langan hududni ko'radi, "NSFW emas" deydi, blur olinadi, kontent
 * yana ochiladi, blur qaytadi — miltillash.
 *
 * Yechim: mask qo'yilgan hudud **ko'r zona** deb belgilanadi va u yerdagi
 * detektsiya natijasi mask'ni bo'shatish uchun ishlatilmaydi. Mask faqat
 * quyidagi hodisalarda bo'shatiladi:
 *   - faol ilova (package) o'zgardi
 *   - butun kadr sezilarli o'zgardi (ekran almashdi / scroll)
 *   - mask atrofidagi halqada o'zgarish (kontent siljidi)
 *   - `holdTimeout` tugadi va tanlangan [ReleasePolicy] bo'shatishga ruxsat berdi
 *
 * Asosiy assimetriya: **blur qo'yish tez, olib tashlash sekin.** Xato blur
 * bezovta qiladi, xato ochilish esa mahsulotni butunlay ma'nosiz qiladi.
 */
class MaskStateMachine(
    private var config: MaskConfig = MaskConfig(),
) {
    private val masks = ArrayList<Mask>(8)
    private var nextId = 1L

    /** Qayta ishlangan kadrlar soni — probe kutishini kadrda o'lchash uchun. */
    private var frameCounter = 0L

    /** Yangi mask yaratilganda chaqiriladi — lokal jurnal uchun (TZ FR-302). */
    var onMaskCreated: ((Mask) -> Unit)? = null

    /**
     * Berilgan detektsiya bo'yicha mask yaratmaslik kerakmi (TZ FR-304).
     * Foydalanuvchi xato deb belgilagan hududlar shu orqali o'tkazib yuboriladi.
     */
    var isSuppressed: ((Detection) -> Boolean)? = null

    /** Yaqinda bo'shatilgan masklar — flicker o'lchash uchun. */
    private val recentlyReleased = ArrayDeque<ReleasedRecord>()

    /** **F0 ning qabul mezoni**: 10 s ichida 0 ta miltillash (TZ FR-105). */
    @Volatile var flickerEvents: Long = 0L; private set

    @Volatile var totalMasksCreated: Long = 0L; private set

    /** Foydalanuvchi xato deb belgilagani uchun o'tkazib yuborilgan detektsiyalar. */
    @Volatile var suppressedCount: Long = 0L; private set
    @Volatile var totalProbes: Long = 0L; private set
    @Volatile var probesConfirmed: Long = 0L; private set

    private class ReleasedRecord(val rect: Detection, val atMs: Long)

    fun updateConfig(newConfig: MaskConfig) {
        config = newConfig
    }

    fun config(): MaskConfig = config

    /** Joriy masklar (o'qish uchun). */
    fun masks(): List<Mask> = masks

    fun reset() {
        masks.clear()
        recentlyReleased.clear()
        frameCounter = 0
    }

    fun resetStats() {
        flickerEvents = 0
        totalMasksCreated = 0
        suppressedCount = 0
        totalProbes = 0
        probesConfirmed = 0
    }

    /**
     * Bitta kadrni qayta ishlaydi va yangilangan mask ro'yxatini qaytaradi.
     */
    fun update(ctx: FrameContext): List<Mask> {
        val now = ctx.nowMs
        frameCounter++
        pruneReleasedRecords(now)

        // 1. Ilova almashdi -> hamma mask darhol bekor. Eski kontent ustidagi
        //    mask yangi kontentga aloqasiz va uni yashirib turishi noto'g'ri.
        if (ctx.packageChanged) {
            for (m in masks) releaseNow(m, now, "package-changed")
            masks.removeAll { !it.isAlive }
        }

        // 2. Mavjud masklarni yangilash
        for (m in masks) {
            if (!m.isAlive) continue
            when (m.state) {
                MaskState.PROBING -> updateProbing(m, ctx)
                MaskState.FADING -> updateFading(m, now)
                MaskState.ACTIVE, MaskState.HOLD -> updateHolding(m, ctx)
                MaskState.RELEASED -> {}
            }
        }

        // 3. Ko'r zona: mask ustidagi detektsiyalar yangi mask yaratmaydi.
        //    Ammo ular ijobiy dalil — blur ostidan ham ko'rinsa, kontent aniq joyida.
        val fresh = ArrayList<Detection>(ctx.detections.size)
        for (d in ctx.detections) {
            // PROBING maskni bu yerda o'tkazib yuboramiz — uning natijasini
            // updateProbing() alohida baholaydi.
            val covering = masks.firstOrNull {
                it.isAlive && it.state != MaskState.PROBING && overlaps(it.rect, d)
            }
            if (covering != null) {
                covering.lastPositiveMs = now
                if (covering.state == MaskState.HOLD || covering.state == MaskState.FADING) {
                    covering.state = MaskState.ACTIVE
                    covering.stateSinceMs = now
                    covering.alpha = 1f
                }
            } else {
                fresh += d
            }
        }

        // 4. Yangi masklar
        val suppress = isSuppressed
        for (d in mergeDetections(fresh)) {
            if (suppress != null && suppress(d)) {
                suppressedCount++
                continue
            }
            createMask(d.expand(config.expandFraction), now)
        }

        // 5. Bir-birini qoplagan masklarni birlashtirish
        mergeMasks(now)

        masks.removeAll { !it.isAlive }
        return masks
    }

    private fun updateHolding(m: Mask, ctx: FrameContext) {
        val now = ctx.nowMs
        val age = now - m.createdAtMs

        // Gisterezis: yosh mask harakat sababli bo'shatilmaydi.
        if (age >= config.minVisibleMs) {
            if (ctx.packageChanged) { releaseNow(m, now, "package"); return }
            if (ctx.globalDelta > config.globalDeltaThreshold) { startFade(m, now, "global-change"); return }
            if (ctx.scrolling) { startFade(m, now, "scroll"); return }
            if (ctx.ringDelta(m.rect) > config.ringDeltaThreshold) { startFade(m, now, "ring-change"); return }
        }

        // ACTIVE -> HOLD: dalil eskirdi
        if (m.state == MaskState.ACTIVE && now - m.lastPositiveMs > HOLD_AFTER_POSITIVE_MS) {
            m.state = MaskState.HOLD
            m.stateSinceMs = now
        }

        // Timeout
        if (now - m.lastPositiveMs >= config.holdTimeoutMs) {
            when (config.releasePolicy) {
                ReleasePolicy.PROBE -> startProbe(m, now)
                ReleasePolicy.TIMEOUT_ONLY -> startFade(m, now, "timeout")
                ReleasePolicy.MOTION_ONLY -> {
                    // Hech narsa: faqat harakat bo'shatadi.
                }
            }
        }
    }

    private fun startProbe(m: Mask, now: Long) {
        m.state = MaskState.PROBING
        m.stateSinceMs = now
        m.probeUntilMs = now + config.probeWindowMs
        m.probeStartFrame = frameCounter
        m.probeCount++
        totalProbes++
        Log.d(TAG, "probe boshlandi mask=${m.id} (${m.probeCount}-marta)")
    }

    private fun updateProbing(m: Mask, ctx: FrameContext) {
        val now = ctx.nowMs
        // Sinov oynasi tugamaguncha kutamiz. IKKI shart ham bajarilishi kerak:
        // vaqt VA kadr soni. Faqat vaqtga tayanish F0 da xato natija berdi —
        // past fps'da 120 ms bitta kadrdan ham qisqa bo'lib, probe hali
        // blur'langan eski kadrni baholab, mask'ni noto'g'ri bo'shatgan edi.
        if (now < m.probeUntilMs) return
        if (frameCounter - m.probeStartFrame < config.probeFrames) return

        // Faqat ochilgan markaziy hududdagi detektsiya hisobga olinadi.
        // Chekkalar hali blur ostida — u yerdagi natija ishonchsiz.
        val hole = holeOf(m.rect)
        val stillThere = ctx.detections.any { overlaps(hole, it) || overlaps(m.rect, it) }
        if (stillThere) {
            probesConfirmed++
            m.state = MaskState.ACTIVE
            m.stateSinceMs = now
            m.lastPositiveMs = now
            m.alpha = 1f
            Log.d(TAG, "probe: kontent joyida, mask=${m.id} saqlandi")
        } else {
            startFade(m, now, "probe-negative")
        }
    }

    private fun updateFading(m: Mask, now: Long) {
        val elapsed = now - m.stateSinceMs
        val t = elapsed.toFloat() / config.fadeDurationMs.coerceAtLeast(1)
        m.alpha = (1f - t).coerceIn(0f, 1f)
        if (m.alpha <= 0.01f) releaseNow(m, now, "faded")
    }

    private fun startFade(m: Mask, now: Long, reason: String) {
        if (m.state == MaskState.FADING) return
        m.state = MaskState.FADING
        m.stateSinceMs = now
        Log.d(TAG, "mask=${m.id} so'nmoqda ($reason)")
    }

    private fun releaseNow(m: Mask, now: Long, reason: String) {
        if (m.state == MaskState.RELEASED) return
        m.state = MaskState.RELEASED
        m.alpha = 0f
        recentlyReleased.addLast(ReleasedRecord(m.rect, now))
        Log.d(TAG, "mask=${m.id} bo'shatildi ($reason), yashagan=${now - m.createdAtMs}ms")
    }

    private fun createMask(rect: Detection, now: Long) {
        // Flicker o'lchovi: yaqinda xuddi shu joyda mask bo'lganmi?
        val flicker = recentlyReleased.any {
            now - it.atMs <= FLICKER_WINDOW_MS && it.rect.iou(rect) >= FLICKER_IOU
        }
        if (flicker) {
            flickerEvents++
            Log.w(TAG, "MILTILLASH aniqlandi (jami=$flickerEvents) rect=$rect")
        }
        val mask = Mask(
            id = nextId++,
            rect = rect,
            state = MaskState.ACTIVE,
            createdAtMs = now,
            stateSinceMs = now,
            lastPositiveMs = now,
        )
        masks += mask
        totalMasksCreated++
        onMaskCreated?.invoke(mask)
    }

    private fun mergeMasks(now: Long) {
        var i = 0
        while (i < masks.size) {
            var j = i + 1
            while (j < masks.size) {
                val a = masks[i]
                val b = masks[j]
                if (a.isAlive && b.isAlive &&
                    a.state != MaskState.PROBING && b.state != MaskState.PROBING &&
                    a.rect.iou(b.rect) >= config.mergeIou
                ) {
                    a.rect = union(a.rect, b.rect)
                    a.lastPositiveMs = maxOf(a.lastPositiveMs, b.lastPositiveMs)
                    a.createdAtMs = minOf(a.createdAtMs, b.createdAtMs)
                    if (b.state == MaskState.ACTIVE) {
                        a.state = MaskState.ACTIVE
                        a.alpha = 1f
                    }
                    b.state = MaskState.RELEASED
                    b.alpha = 0f
                    masks.removeAt(j)
                } else {
                    j++
                }
            }
            i++
        }
    }

    /** Yangi detektsiyalarni o'zaro birlashtirish (bir necha katak = bitta hudud). */
    private fun mergeDetections(list: List<Detection>): List<Detection> {
        if (list.size <= 1) return list
        val out = ArrayList<Detection>(list.size)
        for (d in list) {
            val hit = out.indexOfFirst { it.iou(d) >= config.mergeIou }
            if (hit >= 0) out[hit] = union(out[hit], d) else out += d
        }
        return out
    }

    private fun pruneReleasedRecords(now: Long) {
        while (recentlyReleased.isNotEmpty() && now - recentlyReleased.first().atMs > FLICKER_WINDOW_MS) {
            recentlyReleased.removeFirst()
        }
    }

    private fun overlaps(a: Detection, d: Detection): Boolean =
        a.contains(d.centerX, d.centerY) || a.iou(d) > 0.05f

    /** Sinov paytida ochiladigan markaziy to'rtburchak (ADR-007). */
    private fun holeOf(r: Detection): Detection {
        val f = config.probeHoleFraction.coerceIn(0f, 1f)
        if (f <= 0f) return r
        val hw = r.width * f / 2f
        val hh = r.height * f / 2f
        return r.copy(
            left = r.centerX - hw,
            top = r.centerY - hh,
            right = r.centerX + hw,
            bottom = r.centerY + hh,
        )
    }

    private fun union(a: Detection, b: Detection) = Detection(
        left = minOf(a.left, b.left),
        top = minOf(a.top, b.top),
        right = maxOf(a.right, b.right),
        bottom = maxOf(a.bottom, b.bottom),
        score = maxOf(a.score, b.score),
        label = a.label,
    )

    companion object {
        private const val TAG = "MaskStateMachine"

        /** Ijobiy dalildan keyin ACTIVE holatda qolish muddati. */
        private const val HOLD_AFTER_POSITIVE_MS = 400L

        /** Bo'shatilgandan keyin shu vaqt ichida yangi mask paydo bo'lsa — miltillash. */
        private const val FLICKER_WINDOW_MS = 1_500L

        /** Miltillash deb hisoblash uchun minimal ustma-ustlik. */
        private const val FLICKER_IOU = 0.4f
    }
}
