package com.haramhide.core.detect

/**
 * Detektor kirish o'lchami — qurilma tier'iga bog'liq (TZ 6.1).
 *
 * ### Nega kvadrat emas
 * NudeNet ning o'z Python kodi rasmni kvadratga to'ldirib 320x320 ga kichraytiradi.
 * 1080x2400 telefon ekrani uchun bu kontentni atigi **144 px** kenglikda qoldiradi.
 * Model kirishi dinamik bo'lgani uchun nisbatga mos to'rtburchak ishlatiladi.
 *
 * ### O'lchov (Android 17 emulyator, arm64, 2 oqim, FP32, izolyatsiyalangan)
 * ```
 * 224x448  100k px   40 ms   kontent 202x448
 * 320x320  102k px   44 ms   kontent 144x320   <- NudeNet usuli
 * 256x512  131k px   53 ms   kontent 230x512
 * 320x640  205k px   78 ms   kontent 288x640
 * ```
 * Vaqt piksel soniga deyarli chiziqli. Ya'ni bir xil narxga to'rtburchak
 * kirish kvadratdan ~1.5 barobar ko'p vertikal aniqlik beradi.
 *
 * DIQQAT: bu raqamlar **emulyatorda** olingan va real qurilma uchun mo'ljal emas.
 * Haqiqiy tier tanlash ishga tushishdagi benchmark asosida bo'lishi kerak
 * (TZ NFR-201) — bu F2 vazifasi.
 */
data class DetectorConfig(
    val inputWidth: Int,
    val inputHeight: Int,
    val threads: Int,
) {
    companion object {
        val TIER_A = DetectorConfig(320, 640, threads = 4)
        val TIER_B = DetectorConfig(256, 512, threads = 2)
        val TIER_C = DetectorConfig(224, 448, threads = 2)

        fun byName(name: String?): DetectorConfig = when (name) {
            "A" -> TIER_A
            "C" -> TIER_C
            else -> TIER_B
        }

        fun nameOf(config: DetectorConfig): String = when (config) {
            TIER_A -> "A"
            TIER_C -> "C"
            else -> "B"
        }

        /**
         * **TZ NFR-201** — ishga tushishdagi mikro-benchmark asosida tier tanlash.
         *
         * Chegara qiymatlari [TIER_B] (256x512) da o'lchangan medianaga qarab
         * belgilangan. Ular emulyator o'lchoviga asoslangan boshlang'ich
         * qiymatlar va real qurilmalarda qayta ko'rilishi kerak.
         *
         * @param medianMs [TIER_B] konfiguratsiyasidagi mediana
         */
        fun pickTier(medianMs: Long): DetectorConfig = when {
            medianMs <= 0 -> TIER_B          // o'lchov bo'lmadi
            medianMs < 35 -> TIER_A          // tez qurilma — ko'proq aniqlik
            medianMs < 120 -> TIER_B
            else -> TIER_C                   // sekin qurilma — narxni tushiramiz
        }
    }
}
