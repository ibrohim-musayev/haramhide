# TEXNIK TOPSHIRIQ (SRS) — v2.0

**Loyiha:** Real-Time On-Device Content Filtering App (Android)
**Kod nomi:** `ScreenGuard` (ishchi nom)
**Platforma:** Android 8.0 (API 26) – Android 17 (API 37)
**Hujjat holati:** Qayta ishlangan, v1.0 dagi kritik xatolar tuzatilgan
**Sana:** 2026-09-04

---

## 0. V1.0 GA NISBATAN ASOSIY O'ZGARISHLAR (CHANGE LOG)

| # | v1.0 dagi holat | v2.0 dagi qaror | Sabab |
|---|---|---|---|
| 1 | Boot'da xizmat avtomatik tiklanadi | **Olib tashlandi.** Har safar foydalanuvchi tasdiqlashi shart | Android 14+ MediaProjection har sessiya uchun yangi rozilik talab qiladi |
| 2 | Ekran qulflansa ham ishlaydi (nazarda tutilgan) | **Qulf ochilgandan keyin qayta tiklash oqimi** loyihalanadi | Android 15 QPR1+ da qulflanganda projection avtomatik to'xtaydi |
| 3 | AccessibilityService — asosiy mexanizm | **Ixtiyoriy modul.** Asosiysi — `UsageStatsManager` | Play policy + Android 17 Advanced Protection bloklashi |
| 4 | YOLOv8-Nano | **YOLOX-Nano / NanoDet-Plus (Apache-2.0)** | YOLOv8 = AGPL-3.0, tijorat uchun yaroqsiz |
| 5 | NNAPI + Hexagon delegate | **LiteRT: XNNPACK → GPU → QNN/vendor NPU** | NNAPI Android 15 dan deprecated |
| 6 | Inference 12–18 ms = yakuniy kechikish | **Glass-to-glass budget 250 ms p95** | Capture + convert + overlay ham vaqt oladi |
| 7 | Batareya 3–5% / kun | **≤ 10% / himoyalangan ilovada 1 soat ekran vaqti** | Doimiy capture + inference realligi |
| 8 | RAM ≤ 140 MB | **PSS ≤ 260 MB (target 160 MB)** | Bitmap bufferlar hisobga olinmagan edi |
| 9 | Overlay↔capture qayta aloqasi hisobga olinmagan | **Mask State Machine + hysteresis** talabi kiritildi | Blur o'zi kadrga tushib, "miltillash" (flicker) beradi |
| 10 | Dataset va model o'qitish yo'q | **7-bo'lim: ML lifecycle to'liq** | Loyihaning eng qiyin 60% qismi tushib qolgan edi |
| 11 | "Paywall" sarlavhada bor, talab yo'q | **Monetizatsiya bo'limi** yoki scope'dan chiqarish | Nomuvofiqlik |
| 12 | Distribution yo'li aytilmagan | **Play + to'g'ridan-to'g'ri APK (dual-track)** | Play'dan rad etilish ehtimoli yuqori |

---

## 1. KIRISH

### 1.1. Maqsad
Android qurilmasi ekranida real vaqtda paydo bo'ladigan ochiq/nomaqbul (NSFW) tasvirlarni qurilmaning o'zida aniqlab, ular ustiga xiralashtiruvchi qatlam (overlay) chizadigan mobil ilova uchun texnik talablarni belgilash.

### 1.2. Foydalanuvchi va tahdid modeli (Threat Model)
Bu **xavfsizlik mahsuloti emas**, **o'z-o'zini nazorat (self-control) vositasi**. Buni aniq belgilash muhim, chunki u barcha dizayn qarorlariga ta'sir qiladi:

* **Adversary = foydalanuvchining o'zi** (irodasi zaiflashgan paytdagi). Demak:
  * Ilovani o'chirish/sozlamani yumshatish **kechikish bilan** (cool-down 30–60 daqiqa) amalga oshadi.
  * Ixtiyoriy **Accountability Partner** rejimi (sozlama o'zgarishi haqida e-mail xabarnoma).
  * Ilova **hech qachon 100% kafolat bermaydi** — bu onboarding'da yozma ravishda aytiladi.
* **Adversary ≠ tashqi hujumchi.** Root'langan qurilma, ADB, safe-mode, boshqa profil orqali chetlab o'tish **scope'dan tashqarida**.

### 1.3. Scope
**Kiradi:** ekran kadrini olish, on-device inference, overlay blur, ilovalar bo'yicha filtr, sozlamalar, feedback, model OTA yangilanishi.

**Kirmaydi (Out of scope, v1 uchun):**
* DNS/VPN darajasidagi sayt bloklash
* Matnli kontent (so'z) filtri
* iOS versiyasi (iOS'da MediaProjection ekvivalenti yo'q — texnik jihatdan imkonsiz)
* FLAG_SECURE qo'yilgan ilovalarni himoyalash (texnik jihatdan imkonsiz — 2.2-bo'limga qarang)
* Video qo'ng'iroqlar (FaceTime-turi), DRM kontent (Netflix va h.k.)

---

## 2. PLATFORMA CHEKLOVLARI (CRITICAL CONSTRAINTS)

> Bu bo'lim v1.0 da umuman yo'q edi va loyihaning eng katta riski shu yerda.

### 2.1. C-01 — MediaProjection har sessiyada rozilik talab qiladi
> Ilova har bir media projection sessiyasidan oldin foydalanuvchi roziligini so'rashi shart. Sessiya — `createVirtualDisplay()` ning bitta chaqiruvi, va MediaProjection tokeni faqat bir marta ishlatilishi mumkin. Android 14 va undan yuqorisida `createScreenCaptureIntent()` qaytargan Intent'ni `getMediaProjection()` ga bir martadan ko'p uzatish yoki bitta MediaProjection instance'ida `createVirtualDisplay()` ni bir martadan ko'p chaqirish SecurityException beradi.

**Oqibat:** v1.0 dagi "Boot Receiver → xizmatni avtomatik tiklash" talabi **bajarilmaydi**. Uni bekor qilish kerak.

**Yechim (FR-102):** Boot'da faqat kichik notification chiqadi: "Himoyani yoqish uchun bosing" → foydalanuvchi bosadi → tizim dialogi → sessiya boshlanadi. Onboarding'da bu holat oldindan tushuntiriladi.

### 2.2. C-02 — Qulflanganda sessiya uziladi + status bar chip
> Android 15 QPR1 va undan yuqori qurilmalarda ekran uzatilishi haqida katta va ko'zga tashlanadigan status bar chip'i ko'rsatiladi; foydalanuvchi uni bosib uzatishni to'xtatishi mumkin. Bundan tashqari, qurilma ekrani qulflanganda ekran uzatish avtomatik to'xtaydi.

**Oqibat:** Har ekran qulfini ochganda **qaytadan rozilik so'rash** kerak bo'ladi. Bu kuniga 50–100 marta bo'lishi mumkin — mahsulotni o'ldiradigan UX muammosi.

**Majburiy mitigatsiya (FR-103):**
* Qulf ochilishini `ACTION_USER_PRESENT` orqali ushlab, darhol **bitta bosishlik** heads-up notification / full-screen intent ko'rsatish.
* Foydalanuvchi "Himoya o'chiq" holatida himoyalangan ilovani ochsa — **butun ekranni to'liq blur** qilib turish va "Yoqish" tugmasini ko'rsatish (fail-closed).
* Sessiya uzilgan vaqt statistikasini yig'ish (lokal).

### 2.3. C-03 — FLAG_SECURE oynalari
Bank ilovalari, parol menejerlari, Telegram maxfiy chatlari, DRM pleyerlar `FLAG_SECURE` qo'yadi → VirtualDisplay'ga **qora kadr** keladi.

**Talab (FR-104):** Qora/bo'sh kadr aniqlansa, tizim buni "aniqlab bo'lmadi" deb belgilaydi va tanlangan siyosatga ko'ra harakat qiladi:
* `fail-open` (default) — blur qo'yilmaydi, lekin lokal log yoziladi
* `fail-closed` (Strict rejim) — ekran to'liq blur bo'ladi

### 2.4. C-04 — Overlay ↔ Capture qayta aloqa halqasi (feedback loop)
Overlay oynasi ham VirtualDisplay'ga tushadi. Ya'ni:
1. Kadr N: NSFW aniqlandi → blur chizildi
2. Kadr N+1: model blur'langan hududni ko'radi → NSFW emas → blur olib tashlandi
3. Kadr N+2: yana NSFW → blur qaytdi → **miltillash (flicker)**

**Bu v1.0 da umuman ko'rilmagan va prototipni birinchi kunidayoq buzadigan muammo.**

**Talab (FR-105) — Mask State Machine:**
* Har bir mask uchun holat: `ACTIVE → HOLD → FADING → RELEASED`
* Mask joylashgan hudud "ko'r zona" deb belgilanadi; u yerdagi inference natijasi **e'tiborga olinmaydi**
* Mask faqat quyidagi hodisalarda bo'shatiladi:
  * scroll delta aniqlandi (mask hudud tashqariga chiqdi)
  * faol ilova (package) o'zgardi
  * mask atrofidagi 20% halqa pikselida sezilarli o'zgarish (frame diff)
  * `HOLD_TIMEOUT` (default 3 s) tugadi va "probe" ijobiy natija bermadi
* **Probe strategiyasi:** mask'ni to'liq olib tashlamasdan, mask ostidagi hududni **oxirgi saqlangan toza kadr** bilan emas, balki `SurfaceControl` orqali overlay'ni 1 kadrga `INVISIBLE` qilib (foydalanuvchi ko'zga ilmaydigan ~16 ms) tekshirish. Bu **ADR-003** da alohida hal qilinadi va prototipda o'lchanadi.

### 2.5. C-05 — Google Play siyosati
> Google Play AccessibilityService API dan foydalanishga ruxsat beradi, lekin faqat nogironligi bor odamlarga qurilmadan foydalanishda yordam berish uchun mo'ljallangan xizmatlargina `isAccessibilityTool` atributini e'lon qilishga haqli. Ilovalar imkon bo'lganda Accessibility API o'rniga toraroq qamrovli API va ruxsatlardan foydalanishi shart.

Bundan tashqari: Android 17 Beta 2 da Advanced Protection Mode yoqilganda, rasmiy ravishda accessibility tool deb tasniflanmagan ilovalar AccessibilityService ruxsatini ololmaydi.

**Talab (FR-106):**
* `isAccessibilityTool` **e'lon qilinmaydi** (bizniki accessibility tool emas — bu Play Protect ogohlantirishiga olib keladi).
* Faol ilova nomini aniqlash uchun **birlamchi mexanizm — `UsageStatsManager` + `PACKAGE_USAGE_STATS`**, AccessibilityService emas.
* AccessibilityService faqat **ixtiyoriy "aniqroq rejim"** sifatida (scroll hodisalarini olish uchun), o'chirilgan holatda ham ilova to'liq ishlashi shart.
* Play Console'da Permission Declaration Form + prominent disclosure ekrani (menyu ichida emas, birinchi ishga tushirishda) majburiy.

### 2.6. C-06 — Akselerator (NPU) siyosati
> Android 17 ni target qiladigan va NPU'ga to'g'ridan-to'g'ri murojaat qiladigan ilovalar manifestda `FEATURE_NEURAL_PROCESSING_UNIT` ni e'lon qilishlari shart, aks holda NPU'ga kirish bloklanadi. Bu LiteRT NPU delegate, vendor SDK'lari va deprecated NNAPI dan foydalanadigan ilovalarga tegishli.

NNAPI Android 15 dan boshlab deprecated, shuning uchun unga tayanib bo'lmaydi.

**Talab (NFR-201):** Delegate tanlash zanjiri runtime'da, benchmark asosida:
`Vendor NPU (QNN / NeuroPilot) → GPU delegate → XNNPACK (multi-thread CPU)`.
Har bir qurilmada birinchi ishga tushishda 200 ms lik mikro-benchmark o'tkaziladi, natija DataStore'ga yoziladi.

### 2.7. C-07 — Boshqa cheklovlar
| Kod | Cheklov | Ta'siri |
|---|---|---|
| C-08 | Foreground service type `mediaProjection` majburiy; e'lon qilinmasa `MissingForegroundServiceTypeException` | Manifest talabi |
| C-09 | Screen-cast indikator (chip / ikonka) yashirilmaydi | UX'da oldindan tushuntirish |
| C-10 | `SYSTEM_ALERT_WINDOW` ba'zi OEM (Xiaomi/MIUI, Huawei) da qo'shimcha qo'lda yoqishni talab qiladi | OEM-specific onboarding |
| C-11 | Split-screen, PiP, foldable, rotatsiya — koordinatalar to'g'ri map qilinishi kerak | FR-107 |
| C-12 | OEM battery killer (MIUI, EMUI, ColorOS) xizmatni o'ldiradi | `dontkillmyapp` yo'riqnomasi onboarding'ga |

---

## 3. ARXITEKTURA

### 3.1. Modullar

```
:app                  — UI (Compose), navigatsiya
:feature-onboarding   — ruxsatlar sehrgari, OEM qo'llanmalari
:feature-settings     — sozlamalar, cool-down mantiqi
:core-capture         — MediaProjection, ImageReader, YUV→RGB, frame throttling
:core-detect          — LiteRT runtime, delegate selector, 2-bosqichli pipeline
:core-overlay         — WindowManager, mask state machine, render (RenderEffect/RenderScript-replacement)
:core-context         — UsageStats / a11y, faol paket, scroll signali
:core-data            — Room, DataStore, model repository
:core-telemetry       — anonim metrikalar, crash (opt-in)
```

### 3.2. Pipeline (qayta ishlangan)

```
MediaProjection → VirtualDisplay → ImageReader (RGBA_8888, 720p max)
   │  (acquireLatestImage; eski kadrlar tashlab yuboriladi — backpressure: CONFLATE)
   ▼
[Gate 1: Frame-diff]  downscaled luma 64x64, SAD < threshold → kadr tashlanadi (~0.3 ms)
   │
   ▼
[Gate 2: App filter]  faol paket whitelist'da emasmi? → tashlanadi
   │
   ▼
[Preprocess]  crop → resize 224x224 (Stage A) / 320x320 (Stage B), INT8 quantize
   │
   ▼
[Stage A: NSFW classifier]  MobileNetV4-S, 224², INT8, ~4 ms
   │   score < T_low  → mask yo'q, tugadi   (kadrlarning ~95% shu yerda tugaydi)
   │   score ≥ T_low  ↓
   ▼
[Stage B: Detector]  YOLOX-Nano/NanoDet-Plus, 320², INT8, ~15 ms
   │   → bbox[] + class + conf
   ▼
[Stage C (ixtiyoriy): Face/attribute classifier]  faqat "Strict" rejimda, crop bo'yicha
   │
   ▼
[Tracker]  IoU-based, Kalman lite — bbox'larni kadrlar orasida bog'lash + prediktiv siljitish
   │
   ▼
[Mask State Machine]  → WindowManager overlay update (Choreographer bilan sinxron)
```

**Nega 2 bosqich:** v1.0 da har kadrda og'ir detector ishlatilishi ko'zda tutilgan edi. Bu batareyani yeydi. Yengil klassifikator "darvoza" sifatida ishlatilsa, detector kadrlarning atigi ~5% ida ishga tushadi → energiya 4–6 barobar tejaladi.

### 3.3. Scroll Shield (yangi talab, FR-108)
Aniqlash kechikishi (~150–250 ms) tufayli tez scroll paytida kontent blur qo'yilgunicha ko'rinib qoladi. Shuning uchun:
* Scroll tezligi > `V_threshold` bo'lsa → ekranning kontent qismiga **butunlay yengil blur** (default 40%) qo'yiladi
* Scroll to'xtagach 300 ms ichida aniq bbox blur'ga o'tiladi
* Sozlamalarda o'chirilishi mumkin (`Scroll Shield: ON/OFF`)

---

## 4. FUNKSIONAL TALABLAR

Prioritet: **M** = Must, **S** = Should, **C** = Could.

### 4.1. Onboarding va ruxsatlar

| ID | Pr | Talab | Qabul mezoni (Acceptance Criteria) |
|---|---|---|---|
| FR-001 | M | Prominent disclosure ekrani | Birinchi ishga tushirishda, menyusiz, "Roziman" bosilmaguncha davom etmaydi |
| FR-002 | M | Ruxsatlar sehrgari: overlay, usage-stats, notification (API 33+), battery exemption | Har biri uchun holat ikonkasi (✓/✗), "Ochish" tugmasi tizim ekraniga olib boradi |
| FR-003 | M | OEM-ga xos yo'riqnoma | `Build.MANUFACTURER` bo'yicha Xiaomi/Oppo/Vivo/Huawei/Samsung uchun alohida qadamlar |
| FR-004 | S | AccessibilityService — ixtiyoriy qadam, "o'tkazib yuborish" mumkin | Skip qilinsa ilova funksional qoladi |
| FR-005 | M | MediaProjection roziligi **himoyani yoqish** paytida so'raladi, onboarding'da emas | Tizim dialogi ko'rsatiladi |

### 4.2. Himoya xizmati

| ID | Pr | Talab | Qabul mezoni |
|---|---|---|---|
| FR-101 | M | Foreground service, `mediaProjection` + `specialUse` type | Manifest'da e'lon; notification o'chirilmaydigan |
| FR-102 | M | Boot'dan keyin **rozilik so'rovchi notification** (avtomatik start emas) | Reboot → notification 30 s ichida chiqadi |
| FR-103 | M | Sessiya uzilganda (qulf/chip/boshqa cast) qayta tiklash oqimi | `MediaProjection.Callback.onStop()` handled; `ACTION_USER_PRESENT` da qayta so'rov |
| FR-104 | M | Qora kadr (FLAG_SECURE) siyosati | Sozlamada `fail-open`/`fail-closed` |
| FR-105 | M | Mask State Machine (2.4-bo'lim) | Statik NSFW rasm ustida 10 s davomida **0 ta miltillash** |
| FR-106 | M | Faol ilova aniqlash — UsageStats birlamchi | Ilova almashganda ≤ 500 ms ichida aniqlanadi |
| FR-107 | M | Rotatsiya, split-screen, foldable fold/unfold da koordinata mapping | Konfiguratsiya o'zgarganda mask ≤ 300 ms da to'g'ri joyga qayta chiziladi |
| FR-108 | S | Scroll Shield (3.3-bo'lim) | Scroll paytida blur'siz kadr ko'rinmaydi |

### 4.3. Sozlamalar

| ID | Pr | Talab | Qabul mezoni |
|---|---|---|---|
| FR-201 | M | Sezgirlik: Low / Medium / Strict — har biri aniq threshold juftligi (`T_low`, `T_det`) | Qiymatlar 7.4-jadvalda; sozlamada foizda ko'rsatilmaydi |
| FR-202 | M | Blur stili: Gaussian, Pixelate, Solid | `RenderEffect` (API 31+) / fallback: pre-blurred bitmap tiling |
| FR-203 | M | Blur intensivligi 10–100% | Real-time preview |
| FR-204 | M | Ilovalar bo'yicha whitelist | Default: ijtimoiy tarmoq + brauzerlar ON, boshqalar OFF |
| FR-205 | M | **Cool-down:** sezgirlikni pasaytirish / ilovani o'chirish / xizmatni to'xtatish — 30 daq kechikish bilan | Taymer UI'da ko'rinadi, qayta boshlansa nolga tushmaydi |
| FR-206 | S | PIN/biometrika bilan sozlamalarni qulflash | |
| FR-207 | C | Accountability Partner: e-mail'ga sozlama o'zgarishi haqida xabar | Faqat foydalanuvchi o'zi yoqsa |
| FR-208 | M | Tap-to-unblur: 2 s bosib turish → 5 s ochilish, kuniga N marta limit (default 5) | Limit tugagach xabar ko'rsatiladi |
| FR-209 | S | Uninstall protection: Device Admin **emas** (Play taqiqlaydi), o'rniga cool-down + partner xabari | |

### 4.4. Feedback

| ID | Pr | Talab | Qabul mezoni |
|---|---|---|---|
| FR-301 | M | Skrinshot **hech qachon** saqlanmaydi/yuborilmaydi | Kod review + statik tekshiruv: `Bitmap` diskka yozilmaydi |
| FR-302 | M | Yuboriladigan payload: `{app_package, screen_w, screen_h, model_ver, stage_a_score, stage_b_conf[], device_tier, category}` | JSON schema tasdiqlangan |
| FR-303 | M | Feedback yuborish **opt-in**, har safar tasdiq bilan | |
| FR-304 | S | Lokal "false positive" ro'yxati: shu hududni 1 soat blur qilmaslik | |

### 4.5. Model yangilanishi va Force Update

| ID | Pr | Talab | Qabul mezoni |
|---|---|---|---|
| FR-401 | M | Model OTA: yangi `.tflite` fayl imzo (Ed25519) tekshiruvi bilan yuklanadi | Imzo noto'g'ri → rad etiladi, eski model qoladi |
| FR-402 | M | Modelning rollback imkoniyati (oxirgi 2 versiya saqlanadi) | |
| FR-403 | S | Force update: `min_supported_version_code` | **Offline grace 7 kun** — internetsiz foydalanuvchi bloklanmaydi |
| FR-404 | M | Config kesh; server yetib bo'lmasa oxirgi ma'lum config ishlatiladi | Internetsiz ilova to'liq ishlaydi |

---

## 5. NOFUNKSIONAL TALABLAR (realistik)

### 5.1. Qurilma tierlari

| Tier | Misol SoC | Konfiguratsiya |
|---|---|---|
| **A** | SD 8 Gen 2+, Dimensity 9000+, Tensor G3+ | 720p capture, 12 FPS, Stage A+B+C, NPU |
| **B** | SD 7xx/6xx, Dimensity 7xxx | 540p capture, 8 FPS, Stage A+B, GPU |
| **C** | SD 4xx, Helio G | 360p capture, 4 FPS, faqat Stage A + butun ekran blur, XNNPACK |

Tier avtomatik aniqlanadi (birinchi ishga tushishdagi benchmark + `MemoryClass`).

### 5.2. Ishlash metrikalari

| Metrika | Target (Tier A) | Maksimal (hard limit) | O'lchash usuli |
|---|---|---|---|
| Stage A inference | 4 ms | 10 ms | Trace, p95 |
| Stage B inference | 15 ms | 30 ms | Trace, p95 |
| **Glass-to-glass kechikish** | **150 ms** | **250 ms (p95)** | Yuqori tezlikli kamera testi (240 fps) |
| CPU (himoya yoqiq, scroll paytida) | 8% | 15% | `top` / Perfetto |
| RAM (PSS) | 160 MB | 260 MB | `dumpsys meminfo` |
| Batareya | 6% / soat ekran vaqti | 10% / soat | Battery Historian, 1 soatlik standart senariy |
| Qurilma harorati o'sishi | +4 °C | +8 °C (30 daq uzluksiz) | Thermal API |
| Xost ilova scroll jank | < 1% | < 3% frame drop | `dumpsys gfxinfo` (Instagram scroll) |
| APK hajmi (base) | 20 MB | 35 MB | Modellar `Play Asset Delivery` orqali |
| ANR / crash-free sessions | ≥ 99.5% | ≥ 99.0% | |

**Thermal throttling talabi (NFR-202):** `PowerManager.getCurrentThermalStatus()` ≥ `THERMAL_STATUS_MODERATE` bo'lsa FPS avtomatik yarmiga tushadi; `SEVERE` da faqat Stage A qoladi va foydalanuvchiga xabar beriladi.

### 5.3. Aniqlik metrikalari (yangi — v1.0 da yo'q edi)

| Metrika | Target | Izoh |
|---|---|---|
| Recall (aniq NSFW) | ≥ 0.95 | Eng muhim metrika |
| Precision | ≥ 0.90 | False positive foydalanuvchini bezor qiladi |
| False Positive Rate (neytral kontent) | ≤ 2% kadr | Yangiliklar, sport, oshxona video'lari bo'yicha |
| Erkak/bola siymosini xato blur qilish | ≤ 1% | Alohida test to'plami |
| Turli teri ranglari bo'yicha FPR farqi | ≤ 3 p.p. | **Bias testi majburiy** |

---

## 6. UI / UX TALABLARI

* **Til:** o'zbek (lotin), rus, ingliz. RTL qo'llab-quvvatlash — v2.
* **Asosiy ekran:** katta toggle, sessiya holati (Faol / To'xtagan / Ruxsat kerak), bugungi statistika (lokal).
* **Notification:** "Himoya faol" + tez tugmalar: `10 daq pauza` (cool-down qoidasiga bo'ysunadi), `Sozlamalar`.
* **Xato holatlari uchun aniq ekranlar:** overlay ruxsati yo'q / sessiya uzildi / model yuklanmadi / qurilma qizib ketdi.
* **Accessibility:** kontrast ≥ 4.5:1, TalkBack yorliqlari, minimal tegish maydoni 48 dp.

---

## 7. AI / ML LIFECYCLE (v1.0 da butunlay yo'q edi)

### 7.1. Litsenziya cheklovi — KRITIK
**YOLOv8 (Ultralytics) AGPL-3.0 litsenziyasida.** Tijorat/yopiq kodli ilovada ishlatish uchun pullik Enterprise litsenziya kerak, aks holda butun ilova manba kodini ochish majburiyati tug'iladi.

**Tanlangan alternativalar (Apache-2.0 / MIT):**
* Detector: **YOLOX-Nano** (Apache-2.0) yoki **NanoDet-Plus** (Apache-2.0)
* Klassifikator backbone: **MobileNetV3/V4** (Apache-2.0, `timm`)
* Yuz detektori (kerak bo'lsa): **BlazeFace** / **YuNet** (OpenCV, Apache-2.0)

### 7.2. Dataset strategiyasi
> Bu loyihaning eng qiyin va eng ko'p vaqt oladigan qismi. Model tayyor emas — uni o'qitish kerak.

* **Ruxsat etilgan manbalar:** faqat kattalar uchun, ochiq litsenziyali, provenance hujjatlashtirilgan datasetlar (masalan, LSPD, NudeNet ochiq dataset'i — litsenziyasi alohida tekshiriladi). Person/face uchun Open Images (CC BY 4.0), WIDER FACE.
* **Qat'iy taqiq:** noma'lum manbadan scraping; voyaga yetmaganlar tasviri bo'lishi mumkin bo'lgan har qanday manba. Datasetni qabul qilishdan oldin **CSAM-scan (PhotoDNA/hash-based) va yosh-filtri** majburiy. Bu shartnomaga huquqiy talab sifatida kiritiladi.
* **Domain adaptation:** model real ekran kadrlarida ishlaydi — ya'ni UI elementlari, kompressiya artefaktlari, kichik thumbnail'lar bilan. Shuning uchun **sintetik screen-composite** training set kerak: NSFW rasm → Instagram/TikTok UI shabloniga joylashtirilgan → augmentatsiya (JPEG noise, scroll blur, dark mode).
* **Hajm mo'ljali:** ≥ 80k etiketlangan kadr, undan ≥ 15k — screen-composite.

### 7.3. Baholash protokoli
* **Hold-out test set** trening'dan to'liq ajratilgan, ≥ 10k kadr.
* **Golden set:** 500 ta qo'lda tanlangan qiyin holat (sport, tibbiyot, san'at, plyaj, bola rasmi, erkak torso) — har bir relizda regressiya testi.
* **Bias auditi:** teri rangi, yosh, jins guruhlari bo'yicha FPR/FNR jadvali. Farq 3 p.p. dan oshsa reliz bloklanadi.
* Kvantizatsiyadan **keyingi** metrikalar rasmiy hisoblanadi (INT8 dan keyin recall odatda 1–3% tushadi).

### 7.4. Threshold jadvali

| Rejim | `T_low` (Stage A gate) | `T_det` (bbox conf) | Stage C |
|---|---|---|---|
| Low | 0.75 | 0.60 | o'chiq |
| Medium | 0.50 | 0.45 | o'chiq |
| Strict | 0.30 | 0.30 | yoniq |

Qiymatlar Remote Config orqali yangilanishi mumkin (A/B test uchun).

### 7.5. Etik cheklov
"Kiyingan ayol siymosini blurlash" (Strict rejim) — bu **jins bo'yicha klassifikatsiya**, u tabiatan noaniq va xato ehtimoli yuqori. Shuning uchun:
* Bu funksiya **default o'chiq**, alohida yoqiladi
* UI'da aniq yozuv: "Bu rejim ko'p xato beradi va erkaklar/bolalar tasvirini ham xiralashtirishi mumkin"
* Marketing'da "aniqlik" da'volari qilinmaydi

---

## 8. BACKEND

v1.0 da FastAPI + PostgreSQL + Firebase Remote Config — takrorlanish bor. Soddalashtirildi:

| Komponent | Vazifa |
|---|---|
| **Firebase Remote Config** | threshold'lar, feature flag'lar, `min_supported_version_code` |
| **FastAPI (yagona servis)** | `POST /v1/feedback`, `GET /v1/models/manifest` (model versiya + imzo + URL) |
| **PostgreSQL** | feedback yozuvlari (agregat tahlil uchun), model registri |
| **Object storage (S3/R2)** | model fayllari |

**Xavfsizlik:** TLS 1.2+, certificate pinning, rate-limit (IP + install-id bo'yicha), Play Integrity API tokeni bilan so'rovlarni tasdiqlash. Foydalanuvchi identifikatori — tasodifiy `install_id` (UUIDv4), qayta o'rnatishda yangilanadi; hech qanday PII yo'q.

---

## 9. MAXFIYLIK VA HUQUQIY MASALALAR

* **Zero-transmission:** hech qanday piksel qurilmadan chiqmaydi. Buni tasdiqlash uchun **network allowlist** (faqat 2 ta domen) va CI'da tekshiruv.
* **Privacy Policy** va **Data Safety** formasi Play Console'da to'g'ri to'ldiriladi: "Screen content — collected: No, processed on-device only".
* **Uchinchi tomon audit** (yoki hech bo'lmasa kod ochiqligi capture modulida) — ishonch uchun kuchli argument.
* **Yoshi cheklovi:** ilovaning o'zi 13+ / Play kontent reytingi bo'yicha aniqlanadi.
* **Distribution dual-track:** Play'dan rad etilish ehtimoli **yuqori** (≈40–60%). Shu sababli boshidanoq to'g'ridan-to'g'ri APK + o'z-o'zini yangilash kanali (yoki F-Droid / Obtainium) rejalashtiriladi.

---

## 10. TESTLASH

| Tur | Qamrov |
|---|---|
| Unit | threshold mantiqi, mask state machine, koordinata mapping — ≥ 80% |
| Integration | capture → detect → overlay to'liq zanjiri, soxta kadrlar bilan |
| **Golden set regression** | har PR'da: 500 kadr, metrikalar tushsa merge bloklanadi |
| Device farm | ≥ 12 qurilma: Android 8/10/13/15/17, Xiaomi, Samsung, Oppo, Tecno, past tier |
| Performance | Macrobenchmark: startup, jank, batareya (1 soatlik senariy) |
| Soak test | 8 soat uzluksiz — memory leak, thermal, sessiya uzilishlari |
| Manual UX | qulf ochish → qayta tiklash oqimi, OEM battery killer |
| Security | statik analiz (MobSF), network allowlist tekshiruvi |

---

## 11. RELIZ REJASI

| Faza | Muddat (taxminiy) | Mazmun |
|---|---|---|
| **F0 — Texnik prototip** | 3–4 hafta | Faqat capture + overlay + hardcoded model. **Maqsad: C-01…C-04 ni haqiqiy qurilmada tekshirish.** Agar mask flicker yoki qulf-uzilish hal bo'lmasa — loyiha qayta ko'rib chiqiladi |
| **F1 — ML baseline** | 6–8 hafta | Dataset yig'ish, Stage A o'qitish, kvantizatsiya, golden set |
| **F2 — MVP (Alpha)** | 6 hafta | Stage B, sozlamalar, onboarding, 1 til |
| **F3 — Beta** | 4 hafta | Cool-down, feedback, model OTA, 3 til, device farm |
| **F4 — Reliz** | 3 hafta | Play declaration, privacy policy, dual-track distribution |

**Jami: ~22–25 hafta** (v1.0 hujjatida muddat umuman ko'rsatilmagan edi).

---

## 12. RISK REYESTRI

| ID | Risk | Ehtimol | Ta'sir | Mitigatsiya |
|---|---|---|---|---|
| R-01 | Qulf ochilganda qayta rozilik UX'ni o'ldiradi | **Yuqori** | **Kritik** | F0 da o'lchash; bir bosishlik oqim; kerak bo'lsa mahsulot g'oyasini qayta ko'rib chiqish |
| R-02 | Play'dan rad etish / suspension | Yuqori | Yuqori | Dual-track distribution, minimal ruxsatlar, aniq disclosure |
| R-03 | Mask feedback loop hal bo'lmaydi | O'rta | Yuqori | ADR-003, F0 prototipi |
| R-04 | Model aniqligi yetarli emas | O'rta | Yuqori | Golden set, iterativ OTA yangilanish |
| R-05 | Batareya sarfi qabul qilinmas darajada | O'rta | Yuqori | 2-bosqichli pipeline, AFR, thermal throttle |
| R-06 | Dataset huquqiy muammosi | O'rta | **Kritik** | Faqat litsenziyalangan manba, hash-scan, huquqiy ko'rik |
| R-07 | Android 18+ da MediaProjection yanada cheklanadi | O'rta | Yuqori | Har beta relizni kuzatish, arxitekturani modul qilish |
| R-08 | OEM battery killer xizmatni o'ldiradi | Yuqori | O'rta | Onboarding qo'llanmasi, watchdog notification |

---

## 13. OCHIQ SAVOLLAR (loyihani boshlashdan oldin javob kerak)

1. **Monetizatsiya:** v1.0 da "Remote Paywall" aytilgan, lekin talab yo'q. Bepulmi, obunami, bir martalik to'lovmi? Bu backend va Play billing talablarini o'zgartiradi.
2. **Maqsadli bozor:** faqat O'zbekiston/MDHmi yoki global? Global bo'lsa GDPR + til talablari ortadi.
3. **Jamoa:** ML injener bormi? Model o'qitish — bu Android dasturchisining ishi emas va loyihaning ~40% mehnat hajmi.
4. **Byudjet:** dataset, device farm, GPU trening vaqti, huquqiy maslahat — taxminan qancha?
5. **Muvaffaqiyat mezoni:** qaysi raqamda loyiha "bajarildi" hisoblanadi? (masalan: recall ≥ 0.95 va batareya ≤ 10%/soat)

---

*Hujjat oxiri. v2.1 uchun: F0 prototip natijalaridan keyin 2 va 5-bo'limlar yangilanadi.*
