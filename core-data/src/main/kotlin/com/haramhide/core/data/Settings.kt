package com.haramhide.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    /**
     * Yalang'och erkak ko'kragini ham blur qilish.
     *
     * Erkak avrati (`MALE_GENITALIA_EXPOSED`) bunga BOG'LIQ EMAS —
     * u har doim, har qanday sozlamada blur qilinadi. Bu sozlama faqat
     * yalang'och ko'krakka tegishli (sportchi, suzuvchi, bodibilder).
     *
     * Default yoniq: himoyani jimgina zaiflashtirmaslik uchun.
     */
    val blurMaleChest: Boolean = true,
    /**
     * Ochiq kiyimni ham yopish (yalang'och oyoq, yelka va h.k.).
     *
     * NudeNet buni qila olmaydi — unda bunday sinf yo'q. Alohida
     * segmentatsiya modeli ishlatiladi (docs/OCHIQ-KIYIM.md).
     */
    val blurRevealingClothes: Boolean = true,
    /** Ochiq teri ulushi chegarasi, foizda. O'lchovga asoslangan default 25. */
    val revealingThreshold: Int = 25,
    /** TZ FR-108. */
    val scrollShield: Boolean = true,
    /** Himoya o'chiq bo'lsa himoyalangan ilovada ekranni yopish. TZ FR-103. */
    val shieldWhenOff: Boolean = true,
    /** Foydalanuvchi himoyani yoqishni xohlaydimi (xizmat holati emas, niyat). */
    val protectionDesired: Boolean = false,
    /**
     * Diagnostika qatlami (ekranda yashil matn) va o'lchovlar kartasi.
     *
     * **Default FALSE.** F0/F1 da bu `true` edi — ishlab chiqish uchun qulay,
     * lekin oddiy foydalanuvchi ekranida yashil matn chiqishi mahsulot xatosi.
     * Kerak bo'lsa sozlamalardan yoqiladi.
     */
    val debugOverlay: Boolean = false,
    /** Cool-down kechikishi (ms). TZ FR-205: default 30 daqiqa. */
    val coolDownMs: Long = PendingChange.DEFAULT_COOL_DOWN_MS,
    /** Kutayotgan zaiflashtirish so'rovi, agar bo'lsa. */
    val pending: PendingChange? = null,
    /** Kuniga necha marta tap-to-unblur ishlatish mumkin. TZ FR-208. */
    val unblurLimitPerDay: Int = 5,
    /** Bugun nechta ishlatildi (kun `unblurDay` bilan belgilanadi). */
    val unblurUsedToday: Int = 0,
    /** Hisoblagich qaysi kunga tegishli (epoch kun). */
    val unblurDay: Long = 0,
    /** Onboarding tugatilganmi (TZ FR-001 — prominent disclosure). */
    val onboardingDone: Boolean = false,
    /** Aniqlangan qurilma tier'i: "A" | "B" | "C". Bo'sh — hali o'lchanmagan (TZ NFR-201). */
    val detectorTier: String? = null,
    /** Qo'lda majburlangan tier (debug/tajriba uchun). Bo'sh — avtomatik. */
    val forcedTier: String? = null,
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
            blurMaleChest = p[KEY_MALE_CHEST] ?: true,
            blurRevealingClothes = p[KEY_REVEALING] ?: true,
            revealingThreshold = p[KEY_REVEALING_T] ?: 25,
            scrollShield = p[KEY_SCROLL_SHIELD] ?: true,
            shieldWhenOff = p[KEY_SHIELD_WHEN_OFF] ?: true,
            protectionDesired = p[KEY_PROTECTION_DESIRED] ?: false,
            debugOverlay = p[KEY_DEBUG] ?: false,
            coolDownMs = p[KEY_COOLDOWN_MS] ?: PendingChange.DEFAULT_COOL_DOWN_MS,
            pending = readPending(p),
            unblurLimitPerDay = p[KEY_UNBLUR_LIMIT] ?: 5,
            unblurUsedToday = p[KEY_UNBLUR_USED] ?: 0,
            unblurDay = p[KEY_UNBLUR_DAY] ?: 0,
            onboardingDone = p[KEY_ONBOARDING] ?: false,
            detectorTier = p[KEY_TIER],
            forcedTier = p[KEY_FORCED_TIER],
        )
    }

    private fun readPending(p: Preferences): PendingChange? {
        val type = p[KEY_PENDING_TYPE] ?: return null
        val at = p[KEY_PENDING_AT] ?: return null
        return runCatching {
            PendingChange(PendingChange.Type.valueOf(type), p[KEY_PENDING_VALUE] ?: "", at)
        }.getOrNull()
    }

    // ------------------------------------------------------------- cool-down

    /**
     * Sezgirlikni o'zgartirish so'rovi.
     *
     * Kuchaytirish darhol bajariladi. Zaiflashtirish [AppSettings.coolDownMs]
     * kechikish bilan navbatga qo'yiladi (TZ FR-205).
     *
     * @return true — darhol qo'llandi; false — navbatga qo'yildi
     */
    suspend fun requestSensitivity(v: String): Boolean {
        val current = settings.first()
        val weakening = CoolDownPolicy.isSensitivityWeakening(current.sensitivity, v)
        return if (!CoolDownPolicy.requiresCoolDown(weakening, isCommitted(current))) {
            setSensitivity(v); true
        } else {
            schedule(PendingChange.Type.SENSITIVITY, v, current.coolDownMs); false
        }
    }

    /**
     * Cool-down faqat foydalanuvchi himoyani **yoqqan** bo'lsa ishlaydi.
     *
     * Tahdid modelida (TZ 1.2) raqib — irodasi zaiflashgan paytdagi
     * foydalanuvchi. Dastlabki sozlash paytida bunday holat yo'q: odam hali
     * himoyani yoqmagan va sozlamalarni erkin o'zgartira olishi kerak.
     * Kechikish qaror qabul qilingandan KEYIN ma'noga ega bo'ladi.
     */
    private fun isCommitted(s: AppSettings): Boolean = s.protectionDesired

    /**
     * Himoyalanadigan ilovalar ro'yxatini o'zgartirish.
     * Ro'yxatdan olib tashlash — zaiflashtirish, demak kechikish bilan.
     */
    suspend fun requestPackages(v: Set<String>): Boolean {
        val current = settings.first()
        val weakening = CoolDownPolicy.isPackagesWeakening(current.protectedPackages, v)
        return if (!CoolDownPolicy.requiresCoolDown(weakening, isCommitted(current))) {
            setProtectedPackages(v); true
        } else {
            schedule(PendingChange.Type.PACKAGES, v.joinToString(","), current.coolDownMs); false
        }
    }

    /** Detektorni almashtirish. Evristikaga o'tish — zaiflashtirish. */
    suspend fun requestEngine(v: String): Boolean {
        val current = settings.first()
        val weakening = CoolDownPolicy.isEngineWeakening(v)
        return if (!CoolDownPolicy.requiresCoolDown(weakening, isCommitted(current))) {
            setDetectorEngine(v); true
        } else {
            schedule(PendingChange.Type.ENGINE, v, current.coolDownMs); false
        }
    }

    /** Himoyani to'xtatish so'rovi. Har doim kechikish bilan. */
    suspend fun requestStop(): Boolean {
        val current = settings.first()
        schedule(PendingChange.Type.STOP, "", current.coolDownMs)
        return false
    }

    /** Kutayotgan so'rovni bekor qilish — bu himoyani kuchaytiradi, darhol. */
    suspend fun cancelPending() = edit {
        it.remove(KEY_PENDING_TYPE); it.remove(KEY_PENDING_VALUE); it.remove(KEY_PENDING_AT)
    }

    /**
     * Muddati yetgan so'rovni qo'llaydi.
     * @return qo'llangan so'rov, yoki null
     */
    suspend fun applyDuePending(nowMs: Long = System.currentTimeMillis()): PendingChange? {
        val p = settings.first().pending ?: return null
        if (!p.isDue(nowMs)) return null
        when (p.type) {
            PendingChange.Type.SENSITIVITY -> setSensitivity(p.value)
            PendingChange.Type.ENGINE -> setDetectorEngine(p.value)
            PendingChange.Type.PACKAGES ->
                setProtectedPackages(p.value.split(",").filter { it.isNotBlank() }.toSet())
            PendingChange.Type.MALE_CHEST -> edit { it[KEY_MALE_CHEST] = p.value.toBoolean() }
            PendingChange.Type.REVEALING -> edit { it[KEY_REVEALING] = p.value.toBoolean() }
            PendingChange.Type.STOP -> setProtectionDesired(false)
        }
        cancelPending()
        return p
    }

    private suspend fun schedule(type: PendingChange.Type, value: String, coolDownMs: Long) = edit {
        it[KEY_PENDING_TYPE] = type.name
        it[KEY_PENDING_VALUE] = value
        // Mavjud so'rov bo'lsa taymer QAYTA BOSHLANMAYDI (TZ FR-205).
        if (it[KEY_PENDING_AT] == null || it[KEY_PENDING_TYPE] != type.name) {
            it[KEY_PENDING_AT] = System.currentTimeMillis() + coolDownMs
        }
    }

    suspend fun setCoolDownMs(v: Long) = edit { it[KEY_COOLDOWN_MS] = v }

    // ------------------------------------------------------- tap-to-unblur

    /**
     * Tap-to-unblur limitini tekshiradi va hisoblagichni oshiradi (TZ FR-208).
     * @return true — ruxsat berildi
     */
    suspend fun consumeUnblur(nowMs: Long = System.currentTimeMillis()): Boolean {
        val today = nowMs / 86_400_000L
        val s = settings.first()
        val used = if (s.unblurDay == today) s.unblurUsedToday else 0
        if (used >= s.unblurLimitPerDay) return false
        edit { it[KEY_UNBLUR_DAY] = today; it[KEY_UNBLUR_USED] = used + 1 }
        return true
    }

    suspend fun setUnblurLimit(v: Int) = edit { it[KEY_UNBLUR_LIMIT] = v.coerceIn(0, 50) }

    suspend fun setOnboardingDone(v: Boolean) = edit { it[KEY_ONBOARDING] = v }

    suspend fun setDetectorTier(v: String) = edit { it[KEY_TIER] = v }

    suspend fun setForcedTier(v: String) = edit { it[KEY_FORCED_TIER] = v }

    suspend fun setDetectorEngine(v: String) = edit { it[KEY_ENGINE] = v }
    suspend fun setSensitivity(v: String) = edit { it[KEY_SENSITIVITY] = v }
    suspend fun setBlurStyle(v: String) = edit { it[KEY_BLUR_STYLE] = v }
    suspend fun setBlurIntensity(v: Int) = edit { it[KEY_BLUR_INTENSITY] = v.coerceIn(10, 100) }
    suspend fun setReleasePolicy(v: String) = edit { it[KEY_RELEASE_POLICY] = v }
    suspend fun setSecurePolicy(v: String) = edit { it[KEY_SECURE_POLICY] = v }
    suspend fun setProtectedPackages(v: Set<String>) = edit { it[KEY_PACKAGES] = v }
    suspend fun setScrollShield(v: Boolean) = edit { it[KEY_SCROLL_SHIELD] = v }

    suspend fun setRevealingThreshold(v: Int) = edit { it[KEY_REVEALING_T] = v.coerceIn(5, 90) }

    /** O'chirish himoyani zaiflashtiradi — cool-down orqali (TZ FR-205). */
    suspend fun requestRevealingClothes(v: Boolean): Boolean {
        val current = settings.first()
        val weakening = !v && current.blurRevealingClothes
        return if (!CoolDownPolicy.requiresCoolDown(weakening, isCommitted(current))) {
            edit { it[KEY_REVEALING] = v }; true
        } else {
            schedule(PendingChange.Type.REVEALING, v.toString(), current.coolDownMs); false
        }
    }

    /**
     * Erkak ko'kragi sozlamasi. O'chirish himoyani zaiflashtiradi,
     * shuning uchun cool-down orqali o'tadi (TZ FR-205).
     */
    suspend fun requestBlurMaleChest(v: Boolean): Boolean {
        val current = settings.first()
        val weakening = !v && current.blurMaleChest
        return if (!CoolDownPolicy.requiresCoolDown(weakening, isCommitted(current))) {
            edit { it[KEY_MALE_CHEST] = v }; true
        } else {
            schedule(PendingChange.Type.MALE_CHEST, v.toString(), current.coolDownMs); false
        }
    }
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
        val KEY_MALE_CHEST = booleanPreferencesKey("blur_male_chest")
        val KEY_REVEALING = booleanPreferencesKey("blur_revealing")
        val KEY_REVEALING_T = intPreferencesKey("revealing_threshold")
        val KEY_SCROLL_SHIELD = booleanPreferencesKey("scroll_shield")
        val KEY_SHIELD_WHEN_OFF = booleanPreferencesKey("shield_when_off")
        val KEY_PROTECTION_DESIRED = booleanPreferencesKey("protection_desired")
        val KEY_DEBUG = booleanPreferencesKey("debug_overlay")
        val KEY_COOLDOWN_MS = longPreferencesKey("cooldown_ms")
        val KEY_PENDING_TYPE = stringPreferencesKey("pending_type")
        val KEY_PENDING_VALUE = stringPreferencesKey("pending_value")
        val KEY_PENDING_AT = longPreferencesKey("pending_at")
        val KEY_UNBLUR_LIMIT = intPreferencesKey("unblur_limit")
        val KEY_UNBLUR_USED = intPreferencesKey("unblur_used")
        val KEY_UNBLUR_DAY = longPreferencesKey("unblur_day")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_done")
        val KEY_TIER = stringPreferencesKey("detector_tier")
        val KEY_FORCED_TIER = stringPreferencesKey("forced_tier")
    }
}
