package com.haramhide.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CoolDownPolicy] testlari — TZ FR-205.
 *
 * Bu mantiqning xatosi jimgina bo'ladi: cool-down ishlamay qolsa hech narsa
 * buzilmaydi, shunchaki himoyaning asosiy mexanizmi yo'qoladi.
 */
class CoolDownPolicyTest {

    // ------------------------------------------------------------ sezgirlik

    @Test
    fun `sezgirlikni pasaytirish zaiflashtirish`() {
        assertTrue(CoolDownPolicy.isSensitivityWeakening("STRICT", "MEDIUM"))
        assertTrue(CoolDownPolicy.isSensitivityWeakening("MEDIUM", "LOW"))
        assertTrue(CoolDownPolicy.isSensitivityWeakening("STRICT", "LOW"))
    }

    @Test
    fun `sezgirlikni oshirish zaiflashtirish emas`() {
        assertFalse(CoolDownPolicy.isSensitivityWeakening("LOW", "MEDIUM"))
        assertFalse(CoolDownPolicy.isSensitivityWeakening("MEDIUM", "STRICT"))
        assertFalse(CoolDownPolicy.isSensitivityWeakening("MEDIUM", "MEDIUM"))
    }

    // ------------------------------------------------------------- ilovalar

    /**
     * **Eng oson yanglishadigan joy.** Bo'sh ro'yxat "hamma ilova" degani,
     * ya'ni bo'shdan aniq ro'yxatga o'tish qamrovni toraytiradi.
     */
    @Test
    fun `boshdan aniq royxatga otish zaiflashtirish`() {
        assertTrue(CoolDownPolicy.isPackagesWeakening(emptySet(), setOf("com.a")))
    }

    @Test
    fun `aniq royxatdan boshga otish kuchaytirish`() {
        assertFalse(CoolDownPolicy.isPackagesWeakening(setOf("com.a"), emptySet()))
    }

    @Test
    fun `royxatdan olib tashlash zaiflashtirish`() {
        assertTrue(CoolDownPolicy.isPackagesWeakening(setOf("com.a", "com.b"), setOf("com.a")))
    }

    @Test
    fun `royxatga qoshish zaiflashtirish emas`() {
        assertFalse(CoolDownPolicy.isPackagesWeakening(setOf("com.a"), setOf("com.a", "com.b")))
    }

    @Test
    fun `ozgarishsiz royxat zaiflashtirish emas`() {
        assertFalse(CoolDownPolicy.isPackagesWeakening(setOf("com.a"), setOf("com.a")))
        assertFalse(CoolDownPolicy.isPackagesWeakening(emptySet(), emptySet()))
    }

    // -------------------------------------------------------------- detektor

    @Test
    fun `evristikaga otish zaiflashtirish`() {
        assertTrue(CoolDownPolicy.isEngineWeakening("HEURISTIC"))
        assertFalse(CoolDownPolicy.isEngineWeakening("NUDENET"))
    }

    // ---------------------------------------------------------- kechikish

    @Test
    fun `kechikish faqat himoya yoqilgan bolsa`() {
        assertTrue(CoolDownPolicy.requiresCoolDown(weakening = true, committed = true))
        // Dastlabki sozlash — hali majburiyat yo'q
        assertFalse(CoolDownPolicy.requiresCoolDown(weakening = true, committed = false))
        // Kuchaytirish har doim darhol
        assertFalse(CoolDownPolicy.requiresCoolDown(weakening = false, committed = true))
    }

    // --------------------------------------------------------- taymer

    @Test
    fun `pending qolgan vaqtni togri hisoblaydi`() {
        val p = PendingChange(PendingChange.Type.STOP, "", availableAtMs = 10_000)
        assertEquals(4_000, p.remainingMs(nowMs = 6_000))
        assertEquals(0, p.remainingMs(nowMs = 20_000))
        assertFalse(p.isDue(nowMs = 9_999))
        assertTrue(p.isDue(nowMs = 10_000))
    }
}
