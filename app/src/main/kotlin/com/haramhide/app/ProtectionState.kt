package com.haramhide.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Xizmat va UI o'rtasidagi umumiy holat. Faqat xotirada — hech narsa saqlanmaydi.
 */
object ProtectionState {

    enum class Status {
        /** Xizmat ishlamayapti. */
        STOPPED,

        /** Xizmat ishlayapti, lekin MediaProjection sessiyasi yo'q (TZ C-01/C-02). */
        SESSION_LOST,

        /** To'liq ishlayapti. */
        RUNNING,
    }

    /** F0 o'lchovlari. TZ 6.2 va FR-105 qabul mezonlari shu yerdan o'qiladi. */
    data class Stats(
        val framesProcessed: Long = 0,
        val fps: Float = 0f,
        val avgProcessMs: Float = 0f,
        val maxProcessMs: Long = 0,
        val masksActive: Int = 0,
        val masksCreated: Long = 0,
        /** **FR-105 qabul mezoni:** statik rasm ustida 10 s da 0 bo'lishi kerak. */
        val flickerEvents: Long = 0,
        val probes: Long = 0,
        val probesConfirmed: Long = 0,
        val stageAScore: Float = 0f,
        val stageBRatio: Float = 0f,
        val secureFrames: Long = 0,
        val sessionLostCount: Int = 0,
        val activePackage: String? = null,
        val captureSize: String = "-",
        val edgeAverage: Float = 0f,
    )

    private val _status = MutableStateFlow(Status.STOPPED)
    val status: StateFlow<Status> = _status

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats

    internal fun setStatus(s: Status) { _status.value = s }
    internal fun setStats(s: Stats) { _stats.value = s }
    internal fun currentStats(): Stats = _stats.value
}
