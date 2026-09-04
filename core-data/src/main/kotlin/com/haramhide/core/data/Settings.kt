package com.haramhide.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("haramhide")

/**
 * Ilova sozlamalari. Barchasi **faqat qurilmada** saqlanadi (TZ 9-bo'lim: server yo'q).
 *
 * Enum'lar bu yerda satr sifatida saqlanadi — `:core-data` boshqa modullarga
 * bog'liq bo'lmasligi uchun. Aylantirish `:app` ning ishi.
 */
data class AppSettings(
    /** "NUDENET" (haqiqiy model) | "HEURISTIC" (F0 soxta detektori). */
    val detectorEngine: String = "NUDENET",
    /** "LOW" | "MEDIUM" | "STRICT" — TZ FR-201. */
    val sensitivity: String = "MEDIUM",
    /** "GAUSSIAN" | "PIXELATE" | "SOLID" — TZ FR-202. */
    val blurStyle: String = "GAUSSIAN",
    /** 10..100 — TZ FR-203. */
    val blurIntensity: Int = 70,
    /** "PROBE" | "TIMEOUT_ONLY" | "MOTION_ONLY" — ADR-003. */
    val releasePolicy: String = "PROBE",
    /** "FAIL_OPEN" | "FAIL_CLOSED" — TZ FR-104. */
    val securePolicy: String = "FAIL_OPEN",
    /** Himoyalanadigan ilovalar. Bo'sh bo'lsa — hammasi. TZ FR-204. */
    val protectedPackages: Set<String> = emptySet(),
    /** TZ FR-108. */
    val scrollShield: Boolean = true,
    /** Himoya o'chiq bo'lsa himoyalangan ilovada ekranni yopish. TZ FR-103. */
    val shieldWhenOff: Boolean = true,
    /** Foydalanuvchi himoyani yoqishni xohlaydimi (xizmat holati emas, niyat). */
    val protectionDesired: Boolean = false,
    /** F0 diagnostika qatlamini ko'rsatish. */
    val debugOverlay: Boolean = true,
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            detectorEngine = p[KEY_ENGINE] ?: "NUDENET",
            sensitivity = p[KEY_SENSITIVITY] ?: "MEDIUM",
            blurStyle = p[KEY_BLUR_STYLE] ?: "GAUSSIAN",
            blurIntensity = p[KEY_BLUR_INTENSITY] ?: 70,
            releasePolicy = p[KEY_RELEASE_POLICY] ?: "PROBE",
            securePolicy = p[KEY_SECURE_POLICY] ?: "FAIL_OPEN",
            protectedPackages = p[KEY_PACKAGES] ?: emptySet(),
            scrollShield = p[KEY_SCROLL_SHIELD] ?: true,
            shieldWhenOff = p[KEY_SHIELD_WHEN_OFF] ?: true,
            protectionDesired = p[KEY_PROTECTION_DESIRED] ?: false,
            debugOverlay = p[KEY_DEBUG] ?: true,
        )
    }

    suspend fun setDetectorEngine(v: String) = edit { it[KEY_ENGINE] = v }
    suspend fun setSensitivity(v: String) = edit { it[KEY_SENSITIVITY] = v }
    suspend fun setBlurStyle(v: String) = edit { it[KEY_BLUR_STYLE] = v }
    suspend fun setBlurIntensity(v: Int) = edit { it[KEY_BLUR_INTENSITY] = v.coerceIn(10, 100) }
    suspend fun setReleasePolicy(v: String) = edit { it[KEY_RELEASE_POLICY] = v }
    suspend fun setSecurePolicy(v: String) = edit { it[KEY_SECURE_POLICY] = v }
    suspend fun setProtectedPackages(v: Set<String>) = edit { it[KEY_PACKAGES] = v }
    suspend fun setScrollShield(v: Boolean) = edit { it[KEY_SCROLL_SHIELD] = v }
    suspend fun setShieldWhenOff(v: Boolean) = edit { it[KEY_SHIELD_WHEN_OFF] = v }
    suspend fun setProtectionDesired(v: Boolean) = edit { it[KEY_PROTECTION_DESIRED] = v }
    suspend fun setDebugOverlay(v: Boolean) = edit { it[KEY_DEBUG] = v }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val KEY_ENGINE = stringPreferencesKey("detector_engine")
        val KEY_SENSITIVITY = stringPreferencesKey("sensitivity")
        val KEY_BLUR_STYLE = stringPreferencesKey("blur_style")
        val KEY_BLUR_INTENSITY = intPreferencesKey("blur_intensity")
        val KEY_RELEASE_POLICY = stringPreferencesKey("release_policy")
        val KEY_SECURE_POLICY = stringPreferencesKey("secure_policy")
        val KEY_PACKAGES = stringSetPreferencesKey("protected_packages")
        val KEY_SCROLL_SHIELD = booleanPreferencesKey("scroll_shield")
        val KEY_SHIELD_WHEN_OFF = booleanPreferencesKey("shield_when_off")
        val KEY_PROTECTION_DESIRED = booleanPreferencesKey("protection_desired")
        val KEY_DEBUG = booleanPreferencesKey("debug_overlay")
    }
}
