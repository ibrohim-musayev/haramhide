package com.haramhide.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * **TZ FR-305 — lokal statistika.**
 *
 * Faqat qurilmada saqlanadi va hech qayerga yuborilmaydi (server yo'q).
 * Bugungi kun uchun hisoblanadi; kun almashsa hisoblagichlar nolga tushadi.
 *
 * Ataylab **minimal**: himoya qancha ishladi, necha marta blur qo'yildi,
 * necha marta sessiya uzildi va necha marta ochish ishlatildi. Ko'proq
 * ma'lumot to'plash bu ilovaning maqsadiga zid — u kuzatuv vositasi emas.
 */
data class DayStats(
    val day: Long = 0,
    val activeSeconds: Long = 0,
    val masksCreated: Long = 0,
    val sessionDrops: Int = 0,
    val unblursUsed: Int = 0,
)

class DailyStatsRepository(private val context: Context) {

    val today: Flow<DayStats> = context.statsStore.data.map { p ->
        val day = todayIndex()
        if ((p[KEY_DAY] ?: 0L) != day) DayStats(day = day)
        else DayStats(
            day = day,
            activeSeconds = p[KEY_ACTIVE] ?: 0,
            masksCreated = p[KEY_MASKS] ?: 0,
            sessionDrops = p[KEY_DROPS] ?: 0,
            unblursUsed = p[KEY_UNBLURS] ?: 0,
        )
    }

    suspend fun addActiveSeconds(seconds: Long) = update { p, day ->
        p[KEY_ACTIVE] = (if (p[KEY_DAY] == day) p[KEY_ACTIVE] ?: 0 else 0) + seconds
    }

    suspend fun addMasks(count: Long) = update { p, day ->
        p[KEY_MASKS] = (if (p[KEY_DAY] == day) p[KEY_MASKS] ?: 0 else 0) + count
    }

    suspend fun addSessionDrop() = update { p, day ->
        p[KEY_DROPS] = (if (p[KEY_DAY] == day) p[KEY_DROPS] ?: 0 else 0) + 1
    }

    suspend fun addUnblur() = update { p, day ->
        p[KEY_UNBLURS] = (if (p[KEY_DAY] == day) p[KEY_UNBLURS] ?: 0 else 0) + 1
    }

    private suspend fun update(
        block: (androidx.datastore.preferences.core.MutablePreferences, Long) -> Unit,
    ) {
        val day = todayIndex()
        context.statsStore.edit { p ->
            block(p, day)
            p[KEY_DAY] = day
        }
    }

    private companion object {
        val KEY_DAY = longPreferencesKey("day")
        val KEY_ACTIVE = longPreferencesKey("active_seconds")
        val KEY_MASKS = longPreferencesKey("masks_created")
        val KEY_DROPS = intPreferencesKey("session_drops")
        val KEY_UNBLURS = intPreferencesKey("unblurs_used")
    }
}

/** Mahalliy vaqt zonasidagi kun raqami emas — UTC kun. Soddaligi uchun yetarli. */
private fun todayIndex(): Long = System.currentTimeMillis() / 86_400_000L

private val Context.statsStore by
    androidx.datastore.preferences.preferencesDataStore("haramhide_stats")
