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
     * **Erkak ko'kragi — foydalanuvchi qaroriga qoldirilgan.**
     *
     * Golden set o'lchovida qolgan yolg'on ijobiylarning deyarli hammasi
     * shu klassdan chiqdi (yalang'och ko'krakli sportchi, suzuvchi,
     * bodibilder). Lekin bu **xato emas, belgilash qarori**: erkak ko'kragi
     * yopilishi kerakmi yoki yo'qmi — bu diniy va shaxsiy masala, texnik
     * savol emas.
     *
     * Shuning uchun u alohida sozlama bilan boshqariladi
     * (`AppSettings.blurMaleChest`), sezgirlik darajasidan mustaqil.
     *
     * DIQQAT: erkak avrati (`MALE_GENITALIA_EXPOSED`, indeks 14) bunga
     * KIRMAYDI. U [EXPLICIT] ro'yxatida va har qanday sozlamada blur qilinadi.
     *
     * O'lchangan ta'sir (237 neytral rasm, MEDIUM):
     *   sozlama yoniq  -> FPR 0.030
     *   sozlama o'chiq -> FPR 0.025
     *
     * Farq kutilganidan kichik, chunki `erkak_torso` kategoriyasidagi ko'p
     * rasm boshqa klasslarni ham qo'zg'atadi (model yalang'och erkak
     * ko'kragini ba'zan `FEMALE_BREAST_EXPOSED` deb ham belgilaydi).
     * Butun kategoriyani hisobdan chiqarish FPR ni 0.009 ga tushiradi —
     * lekin bu boshqa savolga javob: "ular blur qilinishi KERAKMI".
     */
    const val MALE_BREAST_EXPOSED = 5

    /**
     * **Hech qachon blur qilinmaydigan klasslar.**
     *
     * `FACE_FEMALE` va `FACE_MALE` ataylab bu yerda. TZ 8.4 da "kiyingan ayol
     * siymosini blurlash" funksiyasi olib tashlangan edi: u jins bo'yicha
     * klassifikatsiya, tabiatan noaniq, va erkak hamda bolalar tasvirini ham
     * xato blur qiladi. Model yuzni aniqlay olishi bu funksiyani qaytarish
     * uchun sabab emas.
     *
     * `FEMALE_BREAST_COVERED` ham shu yerda — va bu **golden set o'lchovidan
     * keyin qo'shildi**. U STRICT rejimda 237 ta neytral rasmdan 20 tasida
     * ishga tushdi, jumladan hijobli ayollar suratlarida. Ya'ni TZ 8.4 da
     * etik sabablarga ko'ra olib tashlangan "kiyingan ayolni blurlash"
     * funksiyasi STRICT orqali orqa eshikdan qaytib kirgan edi.
     *
     * `FEET_EXPOSED`, `ARMPITS_EXPOSED`, `BELLY_COVERED` — o'lchovda ular
     * shovqindan boshqa narsa bermadi (9, 7 va 1 yolg'on ijobiy) va
     * oyoq yoki qo'ltiqni blur qilish uchun mudofaa qilinadigan asos yo'q.
     *
     * `FEET_COVERED` va `ARMPITS_COVERED` — kiyim ostidagi tana qismlari.
     */
    private val NEVER = setOf(
        1,   // FACE_FEMALE
        12,  // FACE_MALE
        9,   // FEET_COVERED
        10,  // ARMPITS_COVERED
        7,   // FEET_EXPOSED
        8,   // BELLY_COVERED
        11,  // ARMPITS_EXPOSED
        16,  // FEMALE_BREAST_COVERED  <- TZ 8.4
    )

    /** Aniq yalang'ochlik — barcha rejimlarda. */
    private val EXPLICIT = setOf(
        2,   // BUTTOCKS_EXPOSED
        3,   // FEMALE_BREAST_EXPOSED
        4,   // FEMALE_GENITALIA_EXPOSED
        6,   // ANUS_EXPOSED
        14,  // MALE_GENITALIA_EXPOSED
    )

    /**
     * Qisman ochiqlik — MEDIUM va STRICT.
     *
     * `BELLY_EXPOSED` bu yerdan STRICT ga ko'chirildi: golden set o'lchovida
     * u MEDIUM dagi yolg'on ijobiylarning eng katta manbai bo'ldi (10 tadan
     * 9 tasi). Ochiq qorin yalang'ochlik emas — sportchi, suzuvchi va
     * bodibilderlar shu sababli blur bo'lardi.
     */
    private val PARTIAL = setOf(
        MALE_BREAST_EXPOSED,
    )

    /**
     * Kiyim ostidan bilinadigan / ishora qiluvchi — faqat STRICT.
     *
     * Ro'yxat golden set o'lchovidan keyin qisqartirildi. Qolganlari —
     * mudofaa qilinadigan darajada aniq holatlar.
     */
    private val SUGGESTIVE = setOf(
        0,   // FEMALE_GENITALIA_COVERED
        13,  // BELLY_EXPOSED
        15,  // ANUS_COVERED
        17,  // BUTTOCKS_COVERED
    )

    private val LOW_SET = EXPLICIT
    private val MEDIUM_SET = EXPLICIT + PARTIAL
    private val STRICT_SET = EXPLICIT + PARTIAL + SUGGESTIVE

    /**
     * Berilgan sezgirlikda [classId] blur qilinadimi.
     *
     * @param blurMaleChest yalang'och erkak ko'kragini blur qilish
     *   (`MALE_BREAST_EXPOSED`). Erkak avrati (`MALE_GENITALIA_EXPOSED`)
     *   bunga bog'liq emas — u har doim blur qilinadi.
     */
    fun isBlurred(
        classId: Int,
        sensitivity: Sensitivity,
        blurMaleChest: Boolean = true,
    ): Boolean {
        if (classId in NEVER) return false
        if (classId == MALE_BREAST_EXPOSED && !blurMaleChest) return false
        return when (sensitivity) {
            Sensitivity.LOW -> classId in LOW_SET
            Sensitivity.MEDIUM -> classId in MEDIUM_SET
            Sensitivity.STRICT -> classId in STRICT_SET
        }
    }

    fun nameOf(classId: Int): String =
        if (classId in NAMES.indices) NAMES[classId] else "UNKNOWN_$classId"
}
