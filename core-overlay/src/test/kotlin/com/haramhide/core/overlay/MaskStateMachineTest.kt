package com.haramhide.core.overlay

import com.haramhide.core.detect.Detection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MaskStateMachine] testlari.
 *
 * Bu klass loyihadagi eng nozik mantiq: u C-04 (miltillash) muammosini hal
 * qiladi va uning xatosi mahsulotni ishlatib bo'lmaydigan qiladi. Shuning uchun
 * testlar aynan **haqiqiy stsenariylarni** takrorlaydi, sun'iy holatlarni emas.
 */
class MaskStateMachineTest {

    private val det = Detection(0.3f, 0.3f, 0.6f, 0.6f, score = 0.9f)

    private fun ctx(
        now: Long,
        detections: List<Detection> = emptyList(),
        packageChanged: Boolean = false,
        globalDelta: Int = 0,
        scrolling: Boolean = false,
        ringDelta: Int = 0,
    ) = FrameContext(now, detections, packageChanged, globalDelta, scrolling) { ringDelta }

    // ---------------------------------------------------------------- yaratish

    @Test
    fun `detektsiya mask yaratadi`() {
        val sm = MaskStateMachine()
        val masks = sm.update(ctx(0, listOf(det)))
        assertEquals(1, masks.size)
        assertEquals(MaskState.ACTIVE, masks[0].state)
        assertTrue(masks[0].isVisible)
    }

    @Test
    fun `mask detektsiyadan kengroq boladi`() {
        val sm = MaskStateMachine(MaskConfig(expandFraction = 0.10f))
        val m = sm.update(ctx(0, listOf(det))).single()
        assertTrue("chap chekka kengaymadi", m.rect.left < det.left)
        assertTrue("o'ng chekka kengaymadi", m.rect.right > det.right)
    }

    // ------------------------------------------------------------- ko'r zona

    /**
     * **C-04 ning yadrosi.** Blur qo'yilgach detektor hududni ko'rmay qoladi.
     * Mask darhol yo'qolmasligi shart — aks holda miltillash boshlanadi.
     */
    @Test
    fun `detektsiya yoqolganda mask darhol boshatilmaydi`() {
        val sm = MaskStateMachine(MaskConfig(releasePolicy = ReleasePolicy.MOTION_ONLY))
        sm.update(ctx(0, listOf(det)))

        // Keyingi 2 soniya davomida detektsiya yo'q (blur uni yashirdi)
        var masks = emptyList<Mask>()
        for (t in 100L..2000L step 100) {
            masks = sm.update(ctx(t))
        }
        assertEquals("mask yo'qolib ketdi", 1, masks.size)
        assertTrue(masks[0].isVisible)
        assertEquals(0, sm.flickerEvents)
    }

    @Test
    fun `mask ostidagi detektsiya yangi mask yaratmaydi`() {
        val sm = MaskStateMachine()
        sm.update(ctx(0, listOf(det)))
        // Xuddi shu joyda yana aniqlandi — bu ko'r zona, yangi mask bo'lmasligi kerak
        val masks = sm.update(ctx(100, listOf(det)))
        assertEquals(1, masks.size)
        assertEquals(1, sm.totalMasksCreated)
    }

    // ------------------------------------------------------------- boshatish

    @Test
    fun `ilova almashishi maskni darhol boshatadi`() {
        val sm = MaskStateMachine()
        sm.update(ctx(0, listOf(det)))
        val masks = sm.update(ctx(50, packageChanged = true))
        assertTrue("ilova almashgach mask qoldi", masks.isEmpty())
    }

    @Test
    fun `halqa ozgarishi maskni boshatadi`() {
        val cfg = MaskConfig(minVisibleMs = 100, ringDeltaThreshold = 8, fadeDurationMs = 50)
        val sm = MaskStateMachine(cfg)
        sm.update(ctx(0, listOf(det)))
        sm.update(ctx(200, ringDelta = 30))   // -> FADING
        val masks = sm.update(ctx(300, ringDelta = 30))
        assertTrue("halqa o'zgarganda mask bo'shalmadi", masks.isEmpty())
    }

    /** Gisterezis: blur chizilishining o'zi halqani biroz o'zgartirishi mumkin. */
    @Test
    fun `yosh mask harakat sababli boshatilmaydi`() {
        val cfg = MaskConfig(minVisibleMs = 500, ringDeltaThreshold = 8)
        val sm = MaskStateMachine(cfg)
        sm.update(ctx(0, listOf(det)))
        val masks = sm.update(ctx(100, ringDelta = 100))  // minVisibleMs ichida
        assertEquals("gisterezis ishlamadi", 1, masks.size)
        assertTrue(masks[0].isVisible)
    }

    // ----------------------------------------------------------- siyosatlar

    @Test
    fun `TIMEOUT_ONLY timeoutdan keyin boshatadi`() {
        val cfg = MaskConfig(
            releasePolicy = ReleasePolicy.TIMEOUT_ONLY,
            holdTimeoutMs = 500,
            fadeDurationMs = 50,
        )
        val sm = MaskStateMachine(cfg)
        sm.update(ctx(0, listOf(det)))
        var masks = listOf<Mask>()
        for (t in 100L..1000L step 100) masks = sm.update(ctx(t))
        assertTrue("timeout bo'shatmadi", masks.isEmpty())
    }

    @Test
    fun `MOTION_ONLY timeoutda boshatmaydi`() {
        val cfg = MaskConfig(releasePolicy = ReleasePolicy.MOTION_ONLY, holdTimeoutMs = 200)
        val sm = MaskStateMachine(cfg)
        sm.update(ctx(0, listOf(det)))
        var masks = listOf<Mask>()
        for (t in 100L..3000L step 100) masks = sm.update(ctx(t))
        assertEquals("MOTION_ONLY bo'shatib yubordi", 1, masks.size)
    }

    /**
     * **F0 da topilgan xato.** Probe kutishi kadrda o'lchanishi shart.
     * Faqat vaqtga tayanilganda past FPS'da probe hali blur'langan eski kadrni
     * baholab, mask'ni noto'g'ri bo'shatgan edi.
     */
    @Test
    fun `PROBE kamida probeFrames kadr kutadi`() {
        val cfg = MaskConfig(
            releasePolicy = ReleasePolicy.PROBE,
            holdTimeoutMs = 200,
            probeWindowMs = 50,
            probeFrames = 3,
        )
        val sm = MaskStateMachine(cfg)
        sm.update(ctx(0, listOf(det)))
        sm.update(ctx(300))                       // timeout -> PROBING
        assertEquals(1, sm.totalProbes)

        // Vaqt allaqachon o'tdi, lekin kadr yetarli emas -> hali ham PROBING
        val afterOneFrame = sm.update(ctx(1000)).single()
        assertEquals(MaskState.PROBING, afterOneFrame.state)
        assertFalse("probe kadr kutmadi", afterOneFrame.isVisible)
    }

    @Test
    fun `PROBE tasdiqlansa mask ACTIVE ga qaytadi`() {
        val cfg = MaskConfig(
            releasePolicy = ReleasePolicy.PROBE,
            holdTimeoutMs = 200,
            probeWindowMs = 50,
            probeFrames = 2,
        )
        val sm = MaskStateMachine(cfg)
        sm.update(ctx(0, listOf(det)))
        sm.update(ctx(300))                        // -> PROBING
        sm.update(ctx(400))                        // 1-kadr
        val m = sm.update(ctx(500, listOf(det))).single()   // 2-kadr, kontent joyida
        assertEquals(MaskState.ACTIVE, m.state)
        assertEquals(1, sm.probesConfirmed)
        assertEquals("tasdiqlangan probe miltillash sanalmasin", 0, sm.flickerEvents)
    }

    // -------------------------------------------------------------- miltillash

    /** Bo'shatilgandan darhol keyin xuddi shu joyda mask paydo bo'lsa — miltillash. */
    @Test
    fun `qayta paydo bolgan mask miltillash deb sanaladi`() {
        val cfg = MaskConfig(
            releasePolicy = ReleasePolicy.TIMEOUT_ONLY,
            holdTimeoutMs = 200,
            fadeDurationMs = 50,
            minVisibleMs = 0,
        )
        val sm = MaskStateMachine(cfg)
        sm.update(ctx(0, listOf(det)))
        for (t in 100L..500L step 100) sm.update(ctx(t))   // timeout -> bo'shatildi
        assertEquals(0, sm.flickerEvents)

        sm.update(ctx(600, listOf(det)))                   // xuddi shu joyda qaytdi
        assertEquals("miltillash sanalmadi", 1, sm.flickerEvents)
    }

    @Test
    fun `boshqa joyda paydo bolgan mask miltillash emas`() {
        val cfg = MaskConfig(
            releasePolicy = ReleasePolicy.TIMEOUT_ONLY,
            holdTimeoutMs = 200,
            fadeDurationMs = 50,
            minVisibleMs = 0,
        )
        val sm = MaskStateMachine(cfg)
        sm.update(ctx(0, listOf(det)))
        for (t in 100L..500L step 100) sm.update(ctx(t))

        val boshqaJoy = Detection(0.05f, 0.05f, 0.15f, 0.15f, score = 0.9f)
        sm.update(ctx(600, listOf(boshqaJoy)))
        assertEquals(0, sm.flickerEvents)
    }

    // ------------------------------------------------------------ birlashtirish

    @Test
    fun `ustma-ust detektsiyalar bitta maskka birlashadi`() {
        val sm = MaskStateMachine()
        val a = Detection(0.30f, 0.30f, 0.50f, 0.50f, score = 0.9f)
        val b = Detection(0.40f, 0.40f, 0.60f, 0.60f, score = 0.8f)
        val masks = sm.update(ctx(0, listOf(a, b)))
        assertEquals(1, masks.size)
        assertTrue(masks[0].rect.left <= a.left)
        assertTrue(masks[0].rect.right >= b.right)
    }

    @Test
    fun `reset hamma maskni tozalaydi`() {
        val sm = MaskStateMachine()
        sm.update(ctx(0, listOf(det)))
        sm.reset()
        assertTrue(sm.masks().isEmpty())
    }
}
