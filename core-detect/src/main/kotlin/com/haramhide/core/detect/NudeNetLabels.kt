package com.haramhide.core.detect

/**
 * NudeNet v3 (`320n.onnx`) chiqarishidagi 18 ta klass.
 *
 * Tartib **modelning o'zi bilan belgilangan** va o'zgartirilmasligi kerak —
 * u chiqish tenzoridagi kanal indeksiga to'g'ridan-to'g'ri mos keladi.
 * Manba: `nudenet/nudenet.py`, `__labels` ro'yxati.
 */
object NudeNetLabels {

    val NAMES = arrayOf(
        "FEMALE_GENITALIA_COVERED",  // 0
        "FACE_FEMALE",               // 1
        "BUTTOCKS_EXPOSED",          // 2
        "FEMALE_BREAST_EXPOSED",     // 3
        "FEMALE_GENITALIA_EXPOSED",  // 4
        "MALE_BREAST_EXPOSED",       // 5
        "ANUS_EXPOSED",              // 6
        "FEET_EXPOSED",              // 7
        "BELLY_COVERED",             // 8
        "FEET_COVERED",              // 9
        "ARMPITS_COVERED",           // 10
        "ARMPITS_EXPOSED",           // 11
        "FACE_MALE",                 // 12
        "BELLY_EXPOSED",             // 13
        "MALE_GENITALIA_EXPOSED",    // 14
        "ANUS_COVERED",              // 15
        "FEMALE_BREAST_COVERED",     // 16
        "BUTTOCKS_COVERED",          // 17
    )

    const val COUNT = 18

    /**
     * **Hech qachon blur qilinmaydigan klasslar.**
     *
     * `FACE_FEMALE` va `FACE_MALE` ataylab bu yerda. TZ 8.4 da "kiyingan ayol
     * siymosini blurlash" funksiyasi olib tashlangan edi: u jins bo'yicha
     * klassifikatsiya, tabiatan noaniq, va erkak hamda bolalar tasvirini ham
     * xato blur qiladi. Model yuzni aniqlay olishi bu funksiyani qaytarish
     * uchun sabab emas.
     *
     * `FEET_COVERED` va `ARMPITS_COVERED` — kiyim ostidagi tana qismlari,
     * ular hech qanday sezgirlikda blur qilinmaydi.
     */
    private val NEVER = setOf(1, 12, 9, 10)

    /** Aniq yalang'ochlik — barcha rejimlarda. */
    private val EXPLICIT = setOf(
        2,   // BUTTOCKS_EXPOSED
        3,   // FEMALE_BREAST_EXPOSED
        4,   // FEMALE_GENITALIA_EXPOSED
        6,   // ANUS_EXPOSED
        14,  // MALE_GENITALIA_EXPOSED
    )

    /** Qisman ochiqlik — MEDIUM va STRICT. */
    private val PARTIAL = setOf(
        5,   // MALE_BREAST_EXPOSED
        13,  // BELLY_EXPOSED
    )

    /** Kiyim ostidan bilinadigan / ishora qiluvchi — faqat STRICT. */
    private val SUGGESTIVE = setOf(
        0,   // FEMALE_GENITALIA_COVERED
        7,   // FEET_EXPOSED
        8,   // BELLY_COVERED
        11,  // ARMPITS_EXPOSED
        15,  // ANUS_COVERED
        16,  // FEMALE_BREAST_COVERED
        17,  // BUTTOCKS_COVERED
    )

    private val LOW_SET = EXPLICIT
    private val MEDIUM_SET = EXPLICIT + PARTIAL
    private val STRICT_SET = EXPLICIT + PARTIAL + SUGGESTIVE

    /** Berilgan sezgirlikda [classId] blur qilinadimi. */
    fun isBlurred(classId: Int, sensitivity: Sensitivity): Boolean {
        if (classId in NEVER) return false
        return when (sensitivity) {
            Sensitivity.LOW -> classId in LOW_SET
            Sensitivity.MEDIUM -> classId in MEDIUM_SET
            Sensitivity.STRICT -> classId in STRICT_SET
        }
    }

    fun nameOf(classId: Int): String =
        if (classId in NAMES.indices) NAMES[classId] else "UNKNOWN_$classId"
}
