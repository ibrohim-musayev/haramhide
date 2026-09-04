# TEXNIK TOPSHIRIQ (SRS) — v2.3

**Loyiha:** HaramHide — Real-Time On-Device Content Filtering App (Android)
**Package ID:** `com.haramhide.app` *(o'zgarmas — Play'da abadiy)*
**Litsenziya:** AGPL-3.0-or-later (ochiq kod)
**Platforma:** Android 8.0 (API 26) – Android 17 (API 37)
**Hujjat holati:** F0 va F1 natijalari bilan yangilangan
**Sana:** 2026-09-04
**O'lchov manbai:** [`docs/F0-NATIJALAR.md`](docs/F0-NATIJALAR.md) · [`docs/F1-NATIJALAR.md`](docs/F1-NATIJALAR.md)

---

## 0.AA. V2.2 GA NISBATAN O'ZGARISHLAR (F1 dan keyin)

Haqiqiy model integratsiya qilindi. Quyidagilar o'lchov natijasi.

| # | v2.2 dagi holat | v2.3 dagi qaror | Manba |
|---|---|---|---|
| B1 | Stage A = MobileNetV2 (GantMan, MIT) | **Bekor qilindi.** Tayyor, ruxsat beruvchi litsenziyali va mobil uchun yengil NSFW klassifikatori mavjud emas. O'rniga teri rangi prescreen | F1 §2.2 |
| B2 | Stage B kirishi 320x320 | **To'rtburchak kirish (Tier B: 256x512).** Kvadrat to'ldirish telefon ekranida kontentni 144 px ga tushiradi | F1 §2.1 |
| B3 | INT8 kvantizatsiya rejalashtirilgan | **Kechiktirildi.** Kalibrlash uchun golden set kerak. FP32 11.6 MB sig'adi | F1 §2.4 |
| B4 | — | **Yangi: kulrang kadr uchun fail-open.** Teri qoidasi `R>G>B` ga tayanadi va qora-oq kadrni butunlay tashlab yuborardi | F1 §2.3 |
| B5 | ADR-001 (ONNX Runtime) taklif edi | **Tasdiqlandi.** Model konversiyasiz ishladi, preprocessing 1-2 ms, postprocessing < 1 ms | F1 §3 |
| B6 | — | **Kritik xato topildi va tuzatildi:** `startForeground(mediaProjection)` rozilik bo'lmaganda `SecurityException` beradi. Bildirishnomadagi «To'xtatish» tugmasi ilovani qulatardi | 3.8-band |
| B7 | Aniqlik metrikalari (6.3) mo'ljal edi | **O'lchanmagan holicha qolmoqda.** Golden set — loyihaning yagona bloklovchi ML vazifasi | F1 §5 |

---

## 0.A. V2.1 GA NISBATAN O'ZGARISHLAR (F0 dan keyin)

F0 texnik prototipi qurildi, Android 17 emulyatorida ishga tushirildi va o'lchandi.
Quyidagilar **taxmin emas, o'lchov natijasi**.

| # | v2.1 dagi holat | v2.2 dagi qaror | Manba |
|---|---|---|---|
| A1 | C-04 (miltillash) — "prototipda o'lchanadi" | **Hal qilindi.** PROBE siyosati + kadr-hisobli kutish → 20 s da 0 miltillash | F0 §2 |
| A2 | ADR-003 ochiq edi | **Yopildi:** `ReleasePolicy.PROBE`, `probeFrames = 3` | ADR-003 |
| A3 | ADR-006 ochiq edi | **Yopildi:** kichraytirib-kattalashtirish, API 26+ da ishlaydi | ADR-006 |
| A4 | Rozilik dialogi bir bosish deb hisoblangan | **Yangi C-13:** Android 14+ default holda BITTA ILOVANI uzatadi. `MediaProjectionConfig` bilan majburlanadi | F0 §3.1 |
| A5 | C-02: "qulflansa uzatish to'xtaydi" | **Aniqlashtirildi:** faqat XAVFSIZ qulfda (PIN/naqsh). Oddiy ekran o'chishida sessiya tirik qoladi | F0 §3.2 |
| A6 | FR-103 loyihalangan edi | **Ishlaydi va tekshirildi.** Talab: tizim broadcast'lari `RECEIVER_EXPORTED` bilan | F0 §3.3 |
| A7 | Probe ~16 ms deb mo'ljallangan | **~600 ms chiqdi.** Hal qilinmagan masala, F1 ga o'tkazildi | F0 §5.1 |
| A8 | Scroll Shield oddiy chegara bilan | **Schmitt trigger majburiy** — bitta chegara o'z-o'zini ushlab qoladi | F0 §3.5 |
| A9 | F0 muddati 4–6 hafta | **Bajarildi.** F1 ga o'tish mumkin | §13 |

---

## 0. V2.0 GA NISBATAN O'ZGARISHLAR (CHANGE LOG)

v2.0 texnik jihatdan to'g'ri edi, lekin **o'rta hajmli jamoa va byudjet borligini nazarda tutgan** hujjat edi. Loyiha aslida: yakka dasturchi, nol byudjet, bepul ilova, ochiq kod. Quyidagilar shundan kelib chiqadi.

| # | v2.0 dagi holat | v2.1 dagi qaror | Sabab |
|---|---|---|---|
| 1 | Yopiq kod nazarda tutilgan; YOLOv8 AGPL bo'lgani uchun rad etilgan | **AGPL-3.0, to'liq ochiq kod** | Ilova bepul → himoyalanadigan tijorat siri yo'q. AGPL cheklov emas, imkoniyat |
| 2 | Stage B: YOLOX-Nano / NanoDet-Plus — **o'zimiz o'qitamiz** | **NudeNet v3 `320n.onnx` tayyor holda olinadi** | Ruxsat beruvchi litsenziyali ochiq NSFW *detektori* mavjud emas (2.4-band). AGPL'ga o'tish bu muammoni butunlay yechadi |
| 3 | 7.2: ≥ 80k kadrlik dataset yig'ish, sintetik screen-composite, trening | **To'liq olib tashlandi.** Trening yo'q | Yakka dasturchi + nol byudjet + ML injener yo'q. Bu loyihaning ~40% mehnati edi |
| 4 | Backend: FastAPI + PostgreSQL + S3 + Firebase Remote Config | **Server yo'q.** Modellar APK ichida; config — GitHub'dagi statik JSON | Nol byudjet. Va Firebase AGPL/F-Droid bilan mos emas (10.3-band) |
| 5 | Feedback serverga yuboriladi (`POST /v1/feedback`) | **Faqat lokal.** Server yo'q, telemetriya yo'q | Server yo'q → yuboradigan joy yo'q. Maxfiylik da'vosi kuchayadi |
| 6 | Play Integrity API, certificate pinning, rate-limit | **Olib tashlandi** | Himoyalanadigan API yo'q |
| 7 | Model OTA majburiy (FR-401..404) | **v1 da modellar APK'ga joylanadi.** OTA — v2 ga suriladi | ~16 MB model APK'ga sig'adi. Bir butun muammo yo'qoladi |
| 8 | Distribution: Play birlamchi + APK zaxira | **F-Droid + GitHub Releases birlamchi, Play — ixtiyoriy urinish** | Play rad etish ehtimoli 40–60%. Ochiq kod F-Droid'ni bepul ochadi |
| 9 | Device farm ≥ 12 qurilma, Firebase Test Lab | **1 ta shaxsiy qurilma + emulyator + jamoatchilik betasi** | Byudjet nol |
| 10 | To'liq bias auditi, reliz bloklovchi 3 p.p. mezoni | **Golden set qoladi; formal bias auditi — "eng yaxshi harakat"** | Auditga teri rangi/yosh bo'yicha etiketlangan dataset kerak — u yo'q. Yolg'on va'da bermaymiz |
| 11 | Monetizatsiya bo'limi ochiq savol edi | **Yopildi: ilova to'liq bepul, billing yo'q** | |
| 12 | Reja 22–25 hafta (jamoa nazarda tutilgan) | **6–9 oy (yakka, to'liq bo'lmagan bandlik)** | Realistik baho. 13-bo'limga qarang |
| 13 | Til: uz / ru / en, RTL — v2 | **uz / ru / en v1'da; ar / tr / id + RTL — v1.1** | Maqsadli bozor musulmon global |
| 14 | Emulyator masalasi ko'rilmagan | **Emulyator cheklovlari alohida hujjatlashtirildi (12.2)** | Hozircha real qurilma yo'q — bu R-01/R-03 ni tekshirishga to'sqinlik qiladi |

---

## 1. KIRISH

### 1.1. Maqsad
Android qurilmasi ekranida real vaqtda paydo bo'ladigan ochiq/nomaqbul (NSFW) tasvirlarni **qurilmaning o'zida** aniqlab, ular ustiga xiralashtiruvchi qatlam (overlay) chizadigan mobil ilova uchun texnik talablarni belgilash.

### 1.2. Foydalanuvchi va tahdid modeli (Threat Model)
Bu **xavfsizlik mahsuloti emas**, **o'z-o'zini nazorat (self-control) vositasi**. Buni aniq belgilash muhim, chunki u barcha dizayn qarorlariga ta'sir qiladi:

* **Adversary = foydalanuvchining o'zi** (irodasi zaiflashgan paytdagi). Demak:
  * Ilovani o'chirish / sozlamani yumshatish **kechikish bilan** (cool-down 30–60 daqiqa).
  * Ixtiyoriy **Accountability Partner** rejimi (sozlama o'zgarishi haqida xabarnoma).
  * Ilova **hech qachon 100% kafolat bermaydi** — bu onboarding'da yozma ravishda aytiladi.
* **Adversary ≠ tashqi hujumchi.** Root'langan qurilma, ADB, safe-mode, boshqa profil orqali chetlab o'tish **scope'dan tashqarida**.
* **Ochiq kod bu modelga zid emas.** Kodni o'qib chetlab o'tish yo'lini topish — bu allaqachon "ADB bilan o'chirish" darajasidagi harakat, ya'ni scope'dan tashqarida. Ochiq kodning ishonch foydasi bu zarardan katta.

### 1.3. Scope

**Kiradi:** ekran kadrini olish, on-device inference, overlay blur, ilovalar bo'yicha filtr, sozlamalar, lokal statistika, cool-down mexanizmi.

**Kirmaydi (v1):**
* DNS/VPN darajasidagi sayt bloklash
* Matnli kontent (so'z) filtri
* iOS versiyasi (iOS'da MediaProjection ekvivalenti yo'q — texnik jihatdan imkonsiz)
* FLAG_SECURE qo'yilgan ilovalarni himoyalash (texnik jihatdan imkonsiz — 3.3-band)
* Video qo'ng'iroqlar, DRM kontent (Netflix va h.k.)
* **Model OTA yangilanishi** (v2 ga suriladi — 8.5-band)
* **Server, hisob (account), sinxronizatsiya, bulut** — umuman yo'q
* **Monetizatsiya** — ilova bepul, reklama va obuna yo'q

---

## 2. LOYIHA KONTEKSTI VA UNING CHEKLOVLARI (yangi bo'lim)

> Bu bo'lim v2.0 da yo'q edi va aynan shu sababli hujjat bajarib bo'lmaydigan talablarni o'z ichiga olgan edi. Texnik qarorlarning yarmi shu jadvaldan kelib chiqadi.

| Omil | Holat | Texnik oqibati |
|---|---|---|
| Jamoa | 1 dasturchi (+ AI yordamchi). ML injener yo'q | Model o'qitish scope'dan chiqadi. Faqat tayyor modellar |
| Byudjet | ≈ 0 | Server yo'q, device farm yo'q, pullik dataset yo'q, huquqiy ko'rik yo'q |
| Monetizatsiya | Yo'q (bepul) | Billing yo'q; server xarajatini qoplaydigan daromad yo'q → server bo'lmasligi kerak |
| Litsenziya | AGPL-3.0-or-later | Firebase / Play Services ishlatilmaydi (10.3). AGPL modellar ochiladi |
| Bozor | Musulmon global (uz/ru/en → ar/tr/id) | RTL v1.1 da; GDPR "eng yaxshi harakat" darajasida (ma'lumot yig'ilmaydi → risk past) |
| Test qurilmasi | **Hozircha yo'q, faqat emulyator** | **Eng katta joriy risk.** 12.2 va R-01 ga qarang |

### 2.1. Ushbu kontekstdan kelib chiqadigan uchta qattiq qoida

1. **Serverga bog'liq hech qanday funksiya v1 ga kiritilmaydi.** Ilova internetsiz 100% ishlaydi va hech qachon internet talab qilmaydi.
2. **Model o'qitishni talab qiladigan hech qanday talab yozilmaydi.** Agar biror funksiya uchun tayyor ochiq model yo'q bo'lsa — u funksiya v1 da yo'q.
3. **Real qurilmada tasdiqlanmagan hech narsa "bajarildi" deb belgilanmaydi.** Emulyator — faqat funksional test uchun.

---

## 3. PLATFORMA CHEKLOVLARI (CRITICAL CONSTRAINTS)

> Bu bo'lim v2.0 dan deyarli o'zgarishsiz keladi — u yerda to'g'ri aniqlangan edi. Loyihaning eng katta texnik riski shu yerda qoladi.

### 3.1. C-01 — MediaProjection har sessiyada rozilik talab qiladi
Ilova har bir media projection sessiyasidan oldin foydalanuvchi roziligini so'rashi shart. Sessiya — `createVirtualDisplay()` ning bitta chaqiruvi; MediaProjection tokeni faqat bir marta ishlatiladi. Android 14+ da `createScreenCaptureIntent()` qaytargan Intent'ni `getMediaProjection()` ga bir martadan ko'p uzatish → `SecurityException`.

**Oqibat:** "Boot Receiver → xizmatni avtomatik tiklash" **bajarilmaydi**.

**Yechim (FR-102):** Boot'da faqat notification: "Himoyani yoqish uchun bosing" → foydalanuvchi bosadi → tizim dialogi → sessiya boshlanadi.

> **F0 da tasdiqlandi.** Rozilik oqimi ishlaydi va bir bosishga tushiriladi —
> lekin faqat C-13 (quyida) bajarilgan holda.

### 3.2. C-02 — Qulflanganda sessiya uziladi + status bar chip
Android 15 QPR1+ da ekran uzatilishi haqida ko'zga tashlanadigan status bar chip'i ko'rsatiladi va foydalanuvchi uni bosib to'xtatishi mumkin. Qurilma ekrani qulflanganda uzatish **avtomatik to'xtaydi**.

**F0 o'lchovi buni aniqlashtirdi:**

| Holat | Sessiya |
|---|---|
| Ekran o'chdi, xavfsiz qulf YO'Q (swipe) | **Tirik qoladi** |
| Ekran o'chdi, PIN / naqsh / biometrika o'rnatilgan | **Uziladi** |

Ya'ni muammo faqat qulf o'rnatgan foydalanuvchilarga tegishli — lekin bu
maqsadli auditoriyaning aksariyati, shuning uchun risk bahosi o'zgarmaydi.

**Oqibat:** Har qulf ochilganda qaytadan rozilik. Kuniga 50–100 marta bo'lishi mumkin — **mahsulotni o'ldiradigan UX muammosi**.

**Majburiy mitigatsiya (FR-103):**
* `ACTION_USER_PRESENT` → darhol **bitta bosishlik** heads-up notification / full-screen intent.
* "Himoya o'chiq" holatida himoyalangan ilova ochilsa — **butun ekranni blur** + "Yoqish" tugmasi (fail-closed).
* Sessiya uzilgan vaqt statistikasi (lokal).

> **F0 da tekshirildi va ishlaydi:** `SCREEN_OFF` → `MediaProjection.Callback.onStop()`
> → xizmat tirik qoladi → `ACTION_USER_PRESENT` → heads-up bildirishnoma.
>
> **Amalga oshirish talabi:** `ACTION_USER_PRESENT` va `ACTION_SCREEN_OFF`
> `RECEIVER_EXPORTED` bilan ro'yxatdan o'tkazilishi shart.
> `RECEIVER_NOT_EXPORTED` bilan ular umuman kelmadi (F0 §3.3). Bular
> himoyalangan tizim broadcast'lari, shuning uchun bu xavfsiz.

### 3.3. C-03 — FLAG_SECURE oynalari
Bank ilovalari, parol menejerlari, maxfiy chatlar, DRM pleyerlar `FLAG_SECURE` qo'yadi → VirtualDisplay'ga **qora kadr** keladi.

**Talab (FR-104):** Qora/bo'sh kadr aniqlansa → "aniqlab bo'lmadi" holati:
* `fail-open` (default) — blur yo'q, lokal log
* `fail-closed` (Strict) — ekran to'liq blur

### 3.4. C-04 — Overlay ↔ Capture qayta aloqa halqasi (feedback loop)
Overlay oynasi ham VirtualDisplay'ga tushadi:
1. Kadr N: NSFW aniqlandi → blur chizildi
2. Kadr N+1: model blur'langan hududni ko'radi → NSFW emas → blur olindi
3. Kadr N+2: yana NSFW → blur qaytdi → **miltillash (flicker)**

**Bu prototipni birinchi kunidayoq buzadigan muammo. Loyihaning 2-raqamli texnik riski.**

**Talab (FR-105) — Mask State Machine:**
* Holatlar: `ACTIVE → HOLD → FADING → RELEASED`
* Mask hududi "ko'r zona" — u yerdagi inference natijasi **e'tiborga olinmaydi**
* Mask faqat quyidagi hodisalarda bo'shatiladi:
  * scroll delta aniqlandi (mask hudud tashqariga chiqdi)
  * faol paket o'zgardi
  * mask atrofidagi 20% halqa pikselida sezilarli o'zgarish (frame diff)
  * `HOLD_TIMEOUT` (default 3 s) tugadi va "probe" ijobiy natija bermadi
* **Probe strategiyasi:** overlay'ni 1 kadrga `INVISIBLE` qilib (~16 ms, ko'zga ilinmaydi) mask ostini tekshirish → **ADR-003**, F0 da o'lchanadi.

### 3.4.1. F0 natijasi — muammo hal qilindi

Uch siyosat bir xil sharoitda (20 s, statik namuna) o'lchandi:

| Siyosat | Miltillash | Kamchiligi |
|---|---|---|
| **PROBE** (tanlandi) | **0** ✅ | Kontent ~600 ms ochiq qoladi |
| TIMEOUT_ONLY | 1 ❌ | Ko'r-ko'rona ochadi |
| MOTION_ONLY | 0 | Mask hech qachon bo'shalmaydi |

**Kritik tuzatish:** probe kutishi **vaqtda emas, kadrda** o'lchanadi
(`probeFrames = 3`). Faqat vaqtga tayanilganda (120 ms) probe past FPS'da
bitta kadrdan ham qisqa bo'lib chiqdi va hali blur'langan eski kadrni
baholab, mask'ni noto'g'ri bo'shatdi → miltillash.

**Hal qilinmagan qism:** probe oynasi ~600 ms (3 kadr × ~200 ms). v2.1 da bu
~16 ms deb mo'ljallangan edi (`SurfaceControl` orqali). WindowManager overlay
bilan bunga erishilmadi. **F1 vazifasi:** probe paytida mask'ni to'liq
olib tashlamasdan, markazidagi ~20 % ni ochish sinovdan o'tkazilsin.

### 3.5. C-05 — Google Play siyosati
Google Play AccessibilityService API dan foydalanishga ruxsat beradi, lekin `isAccessibilityTool` atributini faqat haqiqiy accessibility vositalari e'lon qilishi mumkin. Android 17 Beta 2 da Advanced Protection Mode yoqilganda, rasmiy accessibility tool deb tasniflanmagan ilovalar AccessibilityService ruxsatini ololmaydi.

**Talab (FR-106):**
* `isAccessibilityTool` **e'lon qilinmaydi**.
* Faol ilovani aniqlash — birlamchi mexanizm **`UsageStatsManager` + `PACKAGE_USAGE_STATS`**.
* AccessibilityService faqat **ixtiyoriy "aniqroq rejim"** (scroll hodisalari); o'chirilgan holatda ilova to'liq ishlashi shart.

### 3.6. C-06 — Akselerator (NPU) siyosati
Android 17 ni target qiladigan va NPU'ga to'g'ridan-to'g'ri murojaat qiladigan ilovalar manifestda `FEATURE_NEURAL_PROCESSING_UNIT` ni e'lon qilishlari shart. NNAPI Android 15 dan deprecated.

**Talab (NFR-201):** Delegate/EP tanlash runtime'da, benchmark asosida:
`Vendor NPU (QNN) → GPU → XNNPACK (multi-thread CPU)`.
Birinchi ishga tushishda ~200 ms lik mikro-benchmark, natija DataStore'ga yoziladi.

> **v2.1 izohi:** Nol byudjetda vendor NPU SDK'lari (QNN) bilan ishlash real emas — ular qurilmaga xos test talab qiladi. **v1 uchun faqat XNNPACK (CPU) va GPU** amalga oshiriladi; NPU shoxi arxitekturada joy sifatida qoldiriladi.

### 3.6.1. C-13 — Android 14+ default holda BUTUN EKRANNI EMAS, bitta ilovani uzatadi

> **F0 da topildi. Eng xavfli cheklov, chunki u JIMGINA ishdan chiqaradi.**

Rozilik dialogida default tanlov — «Share one app». Foydalanuvchi shuni tanlasa
(yoki e'tibor bermay «Next» bossa), tizim `RECORD_CONTENT_TASK` rejimiga o'tadi
va faqat bitta ilova oynasini uzatadi. Ilova **hech qanday xato olmaydi** —
shunchaki boshqa ilovalardan kadr kelmaydi. Kontent filtri butunlay foydasiz
bo'lib qoladi, foydalanuvchi esa himoya ishlayapti deb o'ylaydi.

F0 da amalda kuzatilgan:
```
mSession: ContentRecordingSession { contentToRecord = RECORD_CONTENT_TASK,
          tokenToRecord = Task{... com.google.android.gm} }
```

**Talab (FR-109):**
* API 34+: `MediaProjectionConfig.createConfigForDefaultDisplay()` ishlatilsin —
  tanlov imkoniyati butunlay olib tashlanadi, bitta tugma qoladi.
* API 33 va past: bunday config yo'q. Onboarding'da aniq tushuntirish va
  ishga tushgach aniqlash mexanizmi kerak (F2).
* Qabul mezoni: `dumpsys media_projection` da `contentToRecord = RECORD_CONTENT_DISPLAY`.

### 3.8. C-14 — `startForeground(mediaProjection)` rozilik talab qiladi

> **F1 da topildi. Ilovani qulatadigan xato edi.**

Android 14+ da `mediaProjection` turidagi foreground xizmatni ishga tushirish
uchun **amaldagi rozilik tokeni** bo'lishi shart. Bo'lmasa:

```
SecurityException: Starting FGS with type mediaProjection ...
  requires ... Media projection screen capture permission
```

Bu nafaqat sessiya boshlanishida, balki xizmatga yuborilgan **har qanday**
buyruqda tekshiriladi. Ilovaning dastlabki tuzilishida `onStartCommand`
har bir amal uchun `startForeground` chaqirar edi — natijada bildirishnomadagi
«To'xtatish» tugmasi (sessiya allaqachon tugagan holatda) ilovani qulatardi.

**Talab (FR-110):** `startForeground` faqat rozilik hozirgina olingan
[FR-005] oqimida chaqiriladi. Boshqa amallar mavjud bildirishnomani
`NotificationManager.notify()` bilan yangilaydi va FGS holatiga tegmaydi.

### 3.7. C-07 — Boshqa cheklovlar

| Kod | Cheklov | Ta'siri |
|---|---|---|
| C-08 | Foreground service type `mediaProjection` majburiy; e'lon qilinmasa `MissingForegroundServiceTypeException` | Manifest talabi |
| C-09 | Screen-cast indikator (chip / ikonka) yashirilmaydi | UX'da oldindan tushuntirish |
| C-10 | `SYSTEM_ALERT_WINDOW` ba'zi OEM (Xiaomi/MIUI, Huawei) da qo'shimcha qo'lda yoqishni talab qiladi | OEM-specific onboarding |
| C-11 | Split-screen, PiP, foldable, rotatsiya — koordinatalar map qilinishi kerak | FR-107 |
| C-12 | OEM battery killer (MIUI, EMUI, ColorOS) xizmatni o'ldiradi | `dontkillmyapp` yo'riqnomasi onboarding'ga |

---

## 4. ARXITEKTURA

### 4.1. Modullar

```
:app                  — UI (Compose), navigatsiya
:feature-onboarding   — ruxsatlar sehrgari, OEM qo'llanmalari
:feature-settings     — sozlamalar, cool-down mantiqi
:core-capture         — MediaProjection, ImageReader, YUV→RGB, frame throttling
:core-detect          — inference runtime, EP selector, 2-bosqichli pipeline
:core-overlay         — WindowManager, mask state machine, RenderEffect
:core-context         — UsageStats / a11y, faol paket, scroll signali
:core-data            — Room (lokal statistika), DataStore (sozlamalar)
```

> v2.0 dagi `:core-telemetry` **o'chirildi** — server yo'q, telemetriya yo'q.

### 4.2. Pipeline

```
MediaProjection → VirtualDisplay → ImageReader (RGBA_8888, 720p max)
   │  (acquireLatestImage; eski kadrlar tashlanadi — backpressure: CONFLATE)
   ▼
[Gate 1: Frame-diff]  downscaled luma 64x64, SAD < threshold → tashlanadi (~0.3 ms)
   │
   ▼
[Gate 2: App filter]  faol paket whitelist'da emasmi? → tashlanadi
   │
   ▼
[Preprocess]  crop → resize 224² (Stage A) / 320² (Stage B), INT8 quantize
   │
   ▼
[Stage A: NSFW classifier]  MobileNetV2, 224², INT8, ~4–8 ms
   │   score < T_low  → mask yo'q   (kadrlarning ~95% shu yerda tugaydi)
   │   score ≥ T_low  ↓
   ▼
[Stage B: Detector]  NudeNet 320n (YOLOv8n arch), 320², INT8, ~15–30 ms
   │   → bbox[] + class + conf
   ▼
[Tracker]  IoU-based, Kalman lite — bbox'larni kadrlar orasida bog'lash
   │
   ▼
[Mask State Machine]  → WindowManager overlay update (Choreographer bilan sinxron)
```

**Nega 2 bosqich:** yengil klassifikator "darvoza" sifatida ishlatilsa, og'ir detector kadrlarning atigi ~5% ida ishga tushadi → energiya 4–6 barobar tejaladi.

> **v2.0 dagi "Stage C: Face/attribute classifier" olib tashlandi.** U "kiyingan ayol siymosini aniqlash" uchun edi — bunga tayyor, litsenziyasi toza va etik jihatdan mudofaa qilinadigan model yo'q. 8.4-bandga qarang.

### 4.3. Scroll Shield (FR-108)
Aniqlash kechikishi (~150–250 ms) tufayli tez scroll paytida kontent blur qo'yilgunicha ko'rinib qoladi:
* Scroll tezligi > `V_threshold` → ekranning kontent qismiga **butunlay yengil blur** (default 40%)
* Scroll to'xtagach 300 ms ichida aniq bbox blur'ga o'tiladi
* Sozlamada o'chirilishi mumkin

---

## 5. FUNKSIONAL TALABLAR

Prioritet: **M** = Must, **S** = Should, **C** = Could.

### 5.1. Onboarding va ruxsatlar

| ID | Pr | Talab | Qabul mezoni |
|---|---|---|---|
| FR-001 | M | Prominent disclosure ekrani | Birinchi ishga tushirishda, menyusiz, "Roziman" bosilmaguncha davom etmaydi |
| FR-002 | M | Ruxsatlar sehrgari: overlay, usage-stats, notification (API 33+), battery exemption | Har biri uchun holat ikonkasi (✓/✗), "Ochish" tugmasi tizim ekraniga olib boradi |
| FR-003 | M | OEM-ga xos yo'riqnoma | `Build.MANUFACTURER` bo'yicha Xiaomi/Oppo/Vivo/Huawei/Samsung uchun alohida qadamlar |
| FR-004 | S | AccessibilityService — ixtiyoriy, "o'tkazib yuborish" mumkin | Skip qilinsa ilova funksional qoladi |
| FR-005 | M | MediaProjection roziligi **himoyani yoqish** paytida so'raladi | Tizim dialogi ko'rsatiladi |
| FR-006 | M | Kutilma boshqaruvi: ilova hech qachon 100% kafolat bermasligi yozma aytiladi | Onboarding'da alohida ekran |

### 5.2. Himoya xizmati

| ID | Pr | Talab | Qabul mezoni |
|---|---|---|---|
| FR-101 | M | Foreground service, `mediaProjection` type | Manifest'da e'lon; notification o'chirilmaydigan |
| FR-102 | M | Boot'dan keyin **rozilik so'rovchi notification** (avtomatik start emas) | Reboot → notification 30 s ichida chiqadi |
| FR-103 | M | Sessiya uzilganda qayta tiklash oqimi | `MediaProjection.Callback.onStop()` handled; `ACTION_USER_PRESENT` da qayta so'rov ≤ 1 bosish |
| FR-104 | M | Qora kadr (FLAG_SECURE) siyosati | Sozlamada `fail-open`/`fail-closed` |
| FR-105 | M | Mask State Machine (3.4-band) | Statik NSFW rasm ustida 10 s davomida **0 ta miltillash** |
| FR-106 | M | Faol ilova aniqlash — UsageStats birlamchi | Ilova almashganda ≤ 500 ms ichida aniqlanadi |
| FR-107 | M | Rotatsiya, split-screen, foldable da koordinata mapping | Konfiguratsiya o'zgarganda mask ≤ 300 ms da to'g'ri joyga qayta chiziladi |
| FR-108 | S | Scroll Shield (4.3-band) | Scroll paytida blur'siz kadr ko'rinmaydi. **Schmitt trigger majburiy** — bitta chegara o'z-o'zini ushlab qoladi (F0 §3.5) |
| FR-109 | M | Butun ekran capture majburlanadi (C-13) | `dumpsys media_projection` da `RECORD_CONTENT_DISPLAY` |
| FR-110 | M | `startForeground` faqat rozilik oqimida (C-14) | Bildirishnomadagi «To'xtatish» sessiyasiz bosilganda qulash bo'lmaydi |

### 5.3. Sozlamalar

| ID | Pr | Talab | Qabul mezoni |
|---|---|---|---|
| FR-201 | M | Sezgirlik: Low / Medium / Strict — aniq threshold juftligi | Qiymatlar 8.3-jadvalda |
| FR-202 | M | Blur stili: Gaussian, Pixelate, Solid | `RenderEffect` (API 31+) / fallback: pre-blurred bitmap tiling |
| FR-203 | M | Blur intensivligi 10–100% | Real-time preview |
| FR-204 | M | Ilovalar bo'yicha whitelist | Default: ijtimoiy tarmoq + brauzerlar ON, boshqalar OFF |
| FR-205 | M | **Cool-down:** sezgirlikni pasaytirish / ilovani o'chirish / xizmatni to'xtatish — 30 daq kechikish | Taymer UI'da ko'rinadi, qayta boshlansa nolga tushmaydi |
| FR-206 | S | PIN/biometrika bilan sozlamalarni qulflash | |
| FR-207 | C | Accountability Partner — **lokal**: sozlama o'zgarishlari jurnali + qo'lda ulashiladigan hisobot | Server yo'q → avtomatik e-mail yo'q (5.5-band) |
| FR-208 | M | Tap-to-unblur: 2 s bosib turish → 5 s ochilish, kuniga N marta limit (default 5) | Limit tugagach xabar |
| FR-209 | S | Uninstall protection: Device Admin **emas**, cool-down + jurnal | |

### 5.4. Lokal feedback va statistika

| ID | Pr | Talab | Qabul mezoni |
|---|---|---|---|
| FR-301 | M | Skrinshot **hech qachon** saqlanmaydi/yuborilmaydi | Kod review + CI statik tekshiruvi: `Bitmap` diskka yozilmaydi |
| FR-302 | M | Xato aniqlash (false positive) **lokal** ro'yxatga yoziladi | `{app_package, timestamp, stage_a_score, bbox}` — faqat qurilmada |
| FR-303 | M | Foydalanuvchi lokal jurnalni **o'zi** eksport qilib GitHub Issue'ga yuborishi mumkin | Eksport JSON, piksel yo'q, bir bosishda ulashish |
| FR-304 | S | Lokal "false positive" ro'yxati: shu hududni 1 soat blur qilmaslik | |
| FR-305 | M | Lokal statistika: himoya faol vaqti, bloklangan kadrlar soni, sessiya uzilishlari | Asosiy ekranda |

> **v2.0 dan farq:** `POST /v1/feedback` yo'q. Server yo'q → hech narsa yuborilmaydi. Feedback — foydalanuvchi ixtiyori bilan, qo'lda, GitHub orqali.

### 5.5. Config va yangilanish

| ID | Pr | Talab | Qabul mezoni |
|---|---|---|---|
| FR-401 | M | Modellar **APK ichida** joylashadi | Internetsiz birinchi ishga tushishda to'liq ishlaydi |
| FR-402 | S | Ixtiyoriy config: `https://<user>.github.io/haramhide/config.json` (threshold'lar) | Yetib bo'lmasa — APK ichidagi default; ilova bloklanmaydi |
| FR-403 | S | Yangi versiya haqida xabar: GitHub Releases API tekshiruvi (ixtiyoriy, sozlamada o'chiriladi) | Majburiy yangilanish (force update) **yo'q** |
| FR-404 | C | Model OTA — **v2 ga suriladi** | |

---

## 6. NOFUNKSIONAL TALABLAR

### 6.1. Qurilma tierlari

| Tier | Misol SoC | Konfiguratsiya |
|---|---|---|
| **A** | SD 8 Gen 2+, Dimensity 9000+, Tensor G3+ | 720p capture, 12 FPS, Stage A+B, GPU |
| **B** | SD 7xx/6xx, Dimensity 7xxx | 540p capture, 8 FPS, Stage A+B, XNNPACK |
| **C** | SD 4xx, Helio G | 360p capture, 4 FPS, faqat Stage A + butun ekran blur |

Tier avtomatik aniqlanadi (birinchi ishga tushishdagi benchmark + `MemoryClass`).

> Maqsadli bozor (musulmon global) da **Tier B/C ustunlik qiladi**. Shuning uchun **Tier B — birinchi darajali maqsad**, Tier A emas.

### 6.2. Ishlash metrikalari

| Metrika | Target (Tier B) | Maksimal (hard limit) | O'lchash usuli |
|---|---|---|---|
| Stage A inference | 8 ms | 20 ms | Trace, p95 |
| Stage B inference | 30 ms | 60 ms | Trace, p95 |
| **Glass-to-glass kechikish** | **200 ms** | **300 ms (p95)** | Yuqori tezlikli kamera (telefon slow-mo 240 fps yetadi) |
| CPU (himoya yoqiq, scroll paytida) | 10% | 18% | Perfetto |
| RAM (PSS) | 180 MB | 280 MB | `dumpsys meminfo` |
| Batareya | 7% / soat ekran vaqti | 12% / soat | Battery Historian, 1 soatlik standart senariy |
| Harorat o'sishi | +4 °C | +8 °C (30 daq uzluksiz) | Thermal API |
| Xost ilova scroll jank | < 2% | < 5% frame drop | `dumpsys gfxinfo` |
| APK hajmi (modellar bilan) | 30 MB | 45 MB | Modellar bundled |
| Crash-free sessions | ≥ 99.5% | ≥ 99.0% | Lokal crash log (server yo'q) |

> **v2.0 dan farq:** raqamlar Tier A emas, **Tier B** ga bog'landi va biroz yumshatildi. Sabab: (a) maqsadli bozor arzon qurilmalar, (b) NudeNet 320n biz o'qitadigan optimallashtirilgan modeldan sekinroq, (c) yakka dasturchi mikro-optimizatsiyaga ko'p vaqt sarflay olmaydi.

**Thermal throttling (NFR-202):** `PowerManager.getCurrentThermalStatus()` ≥ `MODERATE` → FPS yarmiga tushadi; `SEVERE` → faqat Stage A + foydalanuvchiga xabar.

### 6.3. Aniqlik metrikalari

| Metrika | Target | Izoh |
|---|---|---|
| Recall (aniq NSFW) | ≥ 0.90 | Eng muhim metrika |
| Precision | ≥ 0.85 | False positive foydalanuvchini bezor qiladi |
| False Positive Rate (neytral kontent) | ≤ 3% kadr | Yangiliklar, sport, oshxona bo'yicha |
| Erkak/bola siymosini xato blur qilish | ≤ 2% | Golden set ichidagi alohida to'plam |

> **v2.0 dan farq va halol ogohlantirish:** v2.0 da recall ≥ 0.95 va bias FPR farqi ≤ 3 p.p. yozilgan edi. Bu **o'z modelini o'qitgan holatdagi** maqsad. Biz tayyor model olamiz — uning aniqligini **o'zgartira olmaymiz**, faqat threshold'ni sozlaymiz. Shuning uchun bu raqamlar **maqsad emas, o'lchanadigan kuzatuv**: F1 fazasida golden set bo'yicha haqiqiy qiymat o'lchanadi va shu yerga yoziladi. Agar recall 0.90 dan past chiqsa — bu mahsulot qarori uchun signal, "tuzatiladigan bug" emas.

---

## 7. UI / UX TALABLARI

* **Til:** v1 — o'zbek (lotin), rus, ingliz. **v1.1 — arab, turk, indonez + RTL.**
* **Asosiy ekran:** katta toggle, sessiya holati (Faol / To'xtagan / Ruxsat kerak), bugungi lokal statistika.
* **Notification:** "Himoya faol" + tez tugmalar: `10 daq pauza` (cool-down qoidasiga bo'ysunadi), `Sozlamalar`.
* **Xato ekranlari:** overlay ruxsati yo'q / sessiya uzildi / qurilma qizib ketdi / model yuklanmadi.
* **Accessibility:** kontrast ≥ 4.5:1, TalkBack yorliqlari, minimal tegish maydoni 48 dp.
* **Ton:** ayblovchi emas, qo'llab-quvvatlovchi. "Sen gunoh qilding" emas, "Himoya ishladi".

---

## 8. AI / ML — TAYYOR MODELLAR (butunlay qayta yozilgan)

> v2.0 dagi 7-bo'lim "dataset yig'ing va model o'qiting" degan edi. Bu yakka dasturchi + nol byudjet uchun bajarilmaydi. v2.1 da **hech qanday trening yo'q**.

### 8.1. Litsenziya tekshiruvi (2026-09-04 holatiga)

| Model | Rol | Arxitektura | Litsenziya | Qaror |
|---|---|---|---|---|
| **NudeNet v3 `320n.onnx`** | Stage B — detector, bbox | ultralytics YOLOv8n, 320² | **AGPL-3.0** (repo AGPL + YOLOv8 AGPL) | ✅ **Tanlandi** — loyihamiz AGPL |
| **GantMan/nsfw_model** | Stage A — gate klassifikator | MobileNetV2, 224² | MIT | ✅ **Tanlandi** |
| opennsfw2 (Yahoo) | Stage A muqobili | ResNet-50 | MIT + BSD-2 | Zaxira — sekinroq |
| AdamCodd/vit-base-nsfw-detector | Stage A muqobili | ViT-base, tayyor `model_int8.onnx` | Apache-2.0 | Zaxira — mobil uchun og'ir |
| Falconsai/nsfw_image_detection | Stage A muqobili | ViT-base | Apache-2.0 | Zaxira — mobil uchun og'ir |

**Asosiy topilma:** ruxsat beruvchi litsenziyali (MIT/Apache) ochiq NSFW **detektori** — ya'ni bbox qaytaradigani — mavjud emas. Faqat klassifikatorlar bor. Shu sabab AGPL'ga o'tish **texnik zarurat**, shunchaki falsafiy tanlov emas.

**F1 dagi ikkinchi topilma:** klassifikatorlar ham yaramadi. Tayyor ONNX
ko'rinishida, ruxsat beruvchi litsenziyali **va** mobil darvoza uchun yetarlicha
yengil NSFW klassifikatori mavjud emas — Apache-2.0 variantlar ViT-base (86M
parametr), GantMan'niki Keras formatida, `giacomoarienti/nsfw-classifier` esa
CC-BY-NC-ND (notijorat + hosila taqiqlangan, bizga umuman yaramaydi).

Shuning uchun **Stage A model emas**, balki teri rangi mavjudligini tekshiruvchi
evristik prescreen (`SkinPrescreen`). Batafsil: `docs/F1-NATIJALAR.md` §2.2.

**Litsenziya majburiyati:** har relizda `NOTICE` faylida NudeNet (AGPL-3.0) va GantMan nsfw_model (MIT) atributsiyasi; ilova ichida "Litsenziyalar" ekrani; model fayllari o'z litsenziya matni bilan birga tarqatiladi.

### 8.2. Runtime tanlovi — ADR-001

NudeNet `.onnx` formatida keladi. Ikki yo'l:

| Variant | Foydasi | Zarari |
|---|---|---|
| **ONNX Runtime Mobile** (tavsiya, v1) | Model o'zgartirilmaydi — konversiya riski nol. XNNPACK EP CPU'da yaxshi. Bir runtime ikkala model uchun | AAR ~5–8 MB. NPU yo'li Qualcomm QNN EP bilan cheklangan |
| LiteRT (TFLite) | Android'da NPU/GPU ekotizimi kuchliroq, runtime kichikroq | `onnx2tf` konversiyasi YOLOv8 uchun nozik (NMS, transpose) — yakka dasturchi uchun kunlab yo'qotish riski |

**Qaror:** v1 da **ONNX Runtime Mobile**. Konversiya ishi F0/F1 ni bloklamasligi kerak. LiteRT'ga o'tish — v2 uchun ochiq.

> F0 da `:core-detect` moduli `StageAClassifier` / `StageBDetector` interfeyslari
> bilan qurildi va soxta evristik detektor shu interfeyslarni amalga oshiradi.
> F1 da faqat implementatsiya almashadi, chaqiruvchi kod o'zgarmaydi.

### 8.3. Threshold jadvali (boshlang'ich — F1 da kalibrlanadi)

| Rejim | `T_low` (Stage A gate) | `T_det` (bbox conf) |
|---|---|---|
| Low | 0.75 | 0.60 |
| Medium | 0.50 | 0.45 |
| Strict | 0.30 | 0.30 |

NudeNet klass'lari filtri sozlamada guruhlanadi (masalan: `EXPOSED_*` klasslari doim; `COVERED_*` klasslari faqat Strict'da).

### 8.4. Etik cheklov va olib tashlangan funksiya
v2.0 da "kiyingan ayol siymosini blurlash" (Strict rejim, Stage C) bor edi. **v2.1 da olib tashlandi**, sabablari:
1. Bu **jins bo'yicha klassifikatsiya** — tabiatan noaniq, xato ehtimoli yuqori.
2. Bunga tayyor, litsenziyasi toza model yo'q → o'qitish kerak → scope'dan tashqarida.
3. Erkak va bola tasvirlarini xato blur qilish ehtimoli yuqori — foydalanuvchi ishonchini yo'qotadi.

O'rniga: NudeNet ning `COVERED_*` klasslari Strict rejimda yoqiladi — bu yopiqroq, o'lchanadigan va modelning haqiqiy imkoniyatiga mos.

### 8.5. Baholash protokoli (byudjetga mos)
* **Golden set:** 300–500 ta qo'lda tanlangan qiyin holat — sport, tibbiyot, san'at, plyaj, bola rasmi, erkak torso, hijobli ayol, oshxona/go'sht, cho'milish kiyimi reklamasi. **Bu yagona majburiy ML artefakti va uni yig'ish yakka dasturchi uchun ham real** (~2–3 kun).
* Golden set **repozitoriyga yuklanmaydi** (huquqiy sabab) — faqat hash ro'yxati va natijalar jadvali.
* Har relizda golden set bo'yicha metrikalar qayta o'lchanadi; tushsa reliz to'xtaydi.
* Threshold kalibratsiyasi ham shu to'plamda.
* **Formal bias auditi:** teri rangi/yosh bo'yicha etiketlangan dataset yo'q → to'liq audit bajarilmaydi. Buni **hujjatda ochiq tan olamiz**, marketingda "adolatli" degan da'vo qilinmaydi.

### 8.6. Qat'iy taqiqlar
* Noma'lum manbadan scraping yo'q.
* Golden set uchun ham **faqat qonuniy, kattalar uchun, ochiq manbalar**.
* Voyaga yetmaganlar tasviri bo'lishi mumkin bo'lgan har qanday manba — mutlaq taqiq.
* Hech qanday NSFW tasvir repozitoriyga, CI'ga yoki bulutga yuklanmaydi.

---

## 9. BACKEND

**Server yo'q.**

| Ehtiyoj | v2.0 yechimi | v2.1 yechimi |
|---|---|---|
| Modellar | S3/R2 + imzo tekshiruvi | **APK ichida** |
| Threshold config | Firebase Remote Config | `config.json` — GitHub Pages (statik, bepul, ixtiyoriy) |
| Feedback | FastAPI + PostgreSQL | Lokal jurnal + qo'lda GitHub Issue |
| Telemetriya | Anonim metrikalar | **Yo'q** |
| Force update | `min_supported_version_code` | **Yo'q** — faqat ixtiyoriy xabar |
| Auth / Integrity | Play Integrity API | **Yo'q** |

**Yagona tarmoq murojaatlari (allowlist):**
1. `https://<user>.github.io/haramhide/config.json` — ixtiyoriy, threshold'lar
2. `https://api.github.com/repos/<user>/haramhide/releases/latest` — ixtiyoriy, versiya xabari

Ikkalasi ham **o'chirilishi mumkin** va ikkalasi ham ishlamasa ilova to'liq ishlaydi.

---

## 10. LITSENZIYA, MAXFIYLIK VA HUQUQIY MASALALAR

### 10.1. AGPL-3.0-or-later majburiyatlari
* Butun ilova manba kodi AGPL-3.0 ostida ochiq bo'ladi.
* Har bir binary relizda (APK) manba kodiga aniq havola beriladi.
* Ilovaga qo'shiladigan har bir kutubxona AGPL-3.0 bilan **mos** bo'lishi shart:
  * ✅ MIT, BSD, Apache-2.0, LGPL, GPL-3.0, AGPL-3.0
  * ❌ GPL-2.0-**only** (mos emas), proprietary/yopiq SDK'lar
* AGPL §13 (tarmoq orqali foydalanish) — bizda server yo'q, shuning uchun amalda tegishli emas.

### 10.2. Maxfiylik
* **Zero-transmission:** hech qanday piksel qurilmadan chiqmaydi.
* Tasdiqlash: (a) CI'da network allowlist testi, (b) `Bitmap` → disk yozuvi yo'qligini tekshiradigan statik qoida, (c) **kod ochiq — har kim tekshiradi**.
* Play Data Safety: "Screen content — collected: No, processed on-device only".
* Foydalanuvchi identifikatori **umuman yo'q** (install-id ham yo'q — yuboradigan joy yo'q).

> v2.0 "uchinchi tomon audit" ni tavsiya qilgan edi — bu byudjetdan tashqarida. **Ochiq kod uning o'rnini bosadi** va bepul.

### 10.3. Firebase / Google Play Services ishlatilmaydi
Ikki mustaqil sabab:
1. **Litsenziya:** Play Services yopiq kodli; AGPL ilovaga bog'lash muammoli.
2. **F-Droid:** Google kutubxonalari bo'lgan ilovani qabul qilmaydi, F-Droid esa bizning **birlamchi** tarqatish kanalimiz.

Oqibati: Crashlytics, Analytics, Remote Config, Test Lab, Play Billing — **hech qaysisi yo'q**. Crash'lar lokal fayl sifatida saqlanadi va foydalanuvchi ixtiyori bilan ulashiladi.

### 10.4. Boshqa
* **Yosh reytingi:** 13+. Ilovaning o'zi NSFW kontent ko'rsatmaydi.
* **GDPR:** shaxsiy ma'lumot umuman yig'ilmaydi → risk minimal. Privacy Policy baribir yoziladi (bir sahifa, GitHub Pages'da).

---

## 11. TARQATISH (DISTRIBUTION)

| Kanal | Prioritet | Holat |
|---|---|---|
| **GitHub Releases (APK)** | 1 | Darhol ishlaydi, hech kimdan ruxsat kerak emas. Birlamchi kanal |
| **F-Droid** | 2 | Ochiq kod + Google kutubxonalari yo'q → shartlar bajariladi. Ko'rib chiqish uzoq (haftalar), lekin bepul va barqaror |
| **Obtainium** | 2 | GitHub Releases'dan avtomatik yangilanish — foydalanuvchi uchun qulay, bizdan ish talab qilmaydi |
| **Google Play** | 3 | **Urinib ko'riladi, lekin unga tayanilmaydi.** Rad etish ehtimoli 40–60% (MediaProjection + kontent mavzusi) |

**Muhim:** APK **bir xil imzo kaliti** bilan imzolanadi va kalit zaxiralanadi. Kalit yo'qolsa foydalanuvchilar yangilanish ololmaydi.

---

## 12. TESTLASH

### 12.1. Test turlari

| Tur | Qamrov |
|---|---|
| Unit | threshold mantiqi, mask state machine, koordinata mapping, cool-down — ≥ 70% |
| Integration | capture → detect → overlay zanjiri, soxta (sintetik) kadrlar bilan |
| **Golden set regression** | har relizda: 300–500 kadr, metrikalar tushsa reliz bloklanadi |
| Manual UX | qulf ochish → qayta tiklash oqimi, OEM battery killer, onboarding |
| Soak test | 8 soat uzluksiz — memory leak, thermal, sessiya uzilishlari |
| Security | statik analiz, network allowlist tekshiruvi |

> v2.0 dagi "device farm ≥ 12 qurilma" va "Macrobenchmark batareya" **olib tashlandi** — byudjet nol. O'rniga: **jamoatchilik betasi** (GitHub Issues orqali turli qurilmalardan hisobot).

### 12.2. Emulyator cheklovlari — MUHIM

Hozircha real qurilma yo'q. Emulyatorda **tekshirib bo'ladigan** narsalar:

✅ MediaProjection roziligi va sessiya oqimi
✅ Overlay chizish, mask state machine, flicker mantiqi (C-04!)
✅ UsageStats bilan faol paket aniqlash
✅ Rotatsiya, split-screen, koordinata mapping
✅ Model inference to'g'ri ishlashi (arm64 emulyator image Apple Silicon'da native tezlikda)

❌ **Tekshirib bo'lmaydigan va F0 ni to'liq yopmaydigan:**
* Qulf ochilgandagi sessiya uzilishi (C-02) — **loyihaning 1-raqamli riski**
* Status bar cast chip'ining haqiqiy ko'rinishi va bosilishi
* OEM battery killer (MIUI/EMUI/ColorOS)
* Haqiqiy batareya sarfi, harorat, thermal throttling
* Haqiqiy inference tezligi va glass-to-glass kechikish
* FLAG_SECURE ilovalari (bank, Telegram)

**Talab:** F0 fazasi **real qurilmasiz yopilmaydi**. Emulyatorda ishlaganidan keyin ham C-02 va C-04 real qurilmada tasdiqlanishi shart. Bu — arzon Android telefon (~$60–100 ishlatilgan) sotib olish yoki qarzga olishni talab qiladi va u **nol byudjetdagi yagona majburiy xarajat**.

---

## 13. RELIZ REJASI (yakka dasturchi, to'liq bo'lmagan bandlik)

| Faza | Muddat | Mazmun | Chiqish mezoni |
|---|---|---|---|
| ~~**F0 — Texnik prototip**~~ | ~~4–6 hafta~~ **BAJARILDI** | Capture + overlay + mask state machine + soxta detektor | ✅ 20 s da 0 miltillash; ✅ qulf oqimi 1 bosish; ✅ rotatsiya |
| **F1 — Model integratsiyasi** | 3–4 hafta · **kod qismi BAJARILDI** | ONNX Runtime + NudeNet 320n integratsiya qilindi, klass filtri va sezgirlik darajalari ishlaydi | ⚠️ **Yakunlanmagan:** golden set yig'ilmagan, threshold kalibrlanmagan |
| **F2 — MVP (Alpha)** | 8–10 hafta | Sozlamalar, onboarding, cool-down, whitelist, 1 til (uz) | Kundalik foydalanishga yaroqli; o'zing 1 hafta ishlatasan |
| **F3 — Beta** | 5–6 hafta | 3 til, Scroll Shield, lokal statistika, OEM qo'llanmalari, soak test | GitHub'da ochiq beta; ≥ 10 tashqi foydalanuvchi |
| **F4 — v1.0 reliz** | 3–4 hafta | Litsenziya/NOTICE, Privacy Policy, F-Droid arizasi, Play urinishi | F-Droid'ga topshirildi |

**Jami: ~23–30 hafta ish (≈ 6–7 oy).** To'liq bo'lmagan bandlikda (kuniga 2–3 soat) — **9–12 oy**.

> v2.0 da "22–25 hafta" yozilgan edi, lekin u jamoa uchun edi. Yakka dasturchi uchun ML treningi olib tashlanganiga qaramay muddat qisqarmadi — chunki parallel ishlash imkoni yo'q.

**F0 xulosasi: ijobiy.** C-01, C-02, C-04 hal qilindi, C-13 topildi va hal qilindi.
Loyihani davom ettirish mumkin.

**F1 ning qolgan qismi — bu kod ishi emas:**
Golden set (300–500 qiyin holat) yig'ilmaguncha recall/precision noma'lum va
ilova haqida aniqlik da'vosi qilinmasligi kerak. Buni yig'ish loyiha egasining
huquqiy va etik qaroridir (TZ 8.5, 8.6).

**Real qurilmada bajarilishi shart:**
1. Real qurilma topilsin (R-02) — F0 ning emulyatorda tekshirilmagan qismi
   (OEM battery killer, batareya, harorat, FLAG_SECURE, glass-to-glass) shu yerda yopiladi.
2. Probe oynasini ~600 ms dan qisqartirish sinovi (F0 §5.1).

---

## 14. RISK REYESTRI

| ID | Risk | Ehtimol | Ta'sir | Mitigatsiya |
|---|---|---|---|---|
| R-01 | Qulf ochilganda qayta rozilik UX'ni o'ldiradi | **Yuqori** | **Kritik** | Oqim qurildi va emulyatorda ishlaydi (1 bosish). Haqiqiy kundalik yuk real qurilmada o'lchanishi kerak — **hamon ochiq** |
| R-02 | **Real test qurilmasi yo'q** → R-01/R-03 tekshirilmaydi | **Yuqori** | **Kritik** | Arzon telefon sotib olish/qarzga olish — F0 ni yopish uchun majburiy (12.2) |
| ~~R-03~~ | ~~Mask feedback loop (flicker) hal bo'lmaydi~~ | — | — | **YOPILDI.** ADR-003, F0 §2: PROBE bilan 0 miltillash |
| R-11 | Probe oynasi (~600 ms) kontentni ochib qo'yadi | **Yuqori** | O'rta | F1 da qisman ochish sinovi (F0 §5.1) |
| R-12 | Foydalanuvchi «Share one app» ni tanlab, himoya jimgina ishlamaydi | O'rta | **Kritik** | API 34+ da config bilan majburlandi (C-13). API 33 va past — **hamon ochiq** |
| R-04 | Tayyor model aniqligi yetarli emas | **O'rta-yuqori** | Yuqori | **Hamon ochiq va endi bloklovchi.** Model ishlaydi, lekin aniqligi o'lchanmagan. Golden setsiz bu risk baholanmaydi |
| R-13 | Inference real qurilmada juda sekin | O'rta | Yuqori | Emulyatorda izolyatsiyalangan 40–53 ms, pipeline ichida 210–500 ms. Sabab aniqlanmagan. Real qurilmada o'lchash shart |
| R-05 | Batareya sarfi qabul qilinmas darajada | O'rta | Yuqori | 2-bosqichli pipeline, frame-diff gate, thermal throttle, past FPS |
| R-06 | Play rad etadi | Yuqori | **Past** | F-Droid + GitHub birlamchi kanal → Play yo'qotish emas |
| R-07 | Android 18+ da MediaProjection yanada cheklanadi | O'rta | Yuqori | Har beta relizni kuzatish, capture modulini ajratib saqlash |
| R-08 | OEM battery killer xizmatni o'ldiradi | Yuqori | O'rta | Onboarding qo'llanmasi, watchdog notification |
| R-09 | **Yakka dasturchi charchashi / loyiha to'xtashi** | **Yuqori** | Yuqori | Kichik fazalar, har fazada ishlaydigan natija; ochiq kod → boshqalar davom ettira oladi |
| R-10 | Golden set yig'ishda huquqiy muammo (NSFW material saqlash) | O'rta | Yuqori | Faqat qonuniy manba; repozitoriyga yuklanmaydi; faqat hash ro'yxati |

> **v2.0 dan farq:** R-06 (Play) ta'siri **Yuqori → Past** ga tushdi, chunki tarqatish strategiyasi o'zgardi. R-02 va R-09 — yangi, loyihaning haqiqiy holatidan kelib chiqadi.

---

## 15. ADR RO'YXATI (yoziladigan qarorlar)

| ID | Mavzu | Holat |
|---|---|---|
| ADR-001 | Inference runtime: ONNX Runtime Mobile vs LiteRT | ✅ **F1 da amalda tasdiqlandi** — konversiyasiz ishladi |
| ADR-002 | Litsenziya: AGPL-3.0 va uning oqibatlari | ✅ Yozildi — `docs/ADR-002-litsenziya-agpl.md` |
| ADR-003 | Overlay↔capture flicker: probe strategiyasi | ✅ **Yopildi** — `docs/ADR-003-mask-boshatish.md` |
| ADR-004 | Faol paket aniqlash: UsageStats vs AccessibilityService | ✅ Yozildi — `docs/ADR-004-faol-paket.md` |
| ADR-005 | Modellar: APK ichida vs OTA | ✅ Yozildi — `docs/ADR-005-modellar-apk-ichida.md` |
| ADR-006 | Blur render | ✅ **Yopildi** — `docs/ADR-006-blur-render.md` |
| ADR-007 | Probe oynasini qisqartirish (qisman ochish) | **Ochiq — F2** |
| ADR-008 | Detektor kirish o'lchami: to'rtburchak vs kvadrat | ✅ Qaror qabul qilindi (F1 §2.1) — `DetectorConfig` |

---

## 16. QOLGAN OCHIQ SAVOLLAR

v2.0 dagi 5 ta savolning 4 tasi yopildi (monetizatsiya — bepul; bozor — musulmon global; jamoa — yakka; byudjet — nol). Qolganlari:

1. **Muvaffaqiyat mezoni.** F0 qismi bajarildi (miltillash 0, qulf oqimi 1 bosish).
   v1.0 qismi hamon tasdiqlanmagan: *golden set recall ≥ 0.90, batareya ≤ 12 %/soat,
   100 ta faol foydalanuvchi.*
2. **Real test qurilmasi qachon va qanday topiladi?** (R-02 — F0 ni yopadigan yagona to'siq)
3. **GitHub tashkiloti/hisobi:** repozitoriya qaysi nom ostida ochiladi? (`config.json` va Releases URL'lari shunga bog'liq)
4. **Golden set:** NSFW test materialini qonuniy va xavfsiz yig'ish/saqlash tartibi kim tomonidan belgilanadi? (R-10)

---

*Hujjat oxiri. v2.4 uchun: golden set bo'yicha o'lchovdan keyin 6.3 (aniqlik
metrikalari) va 8.3 (threshold jadvali) haqiqiy qiymatlar bilan almashtiriladi;
real qurilmada o'lchovdan keyin 3 (C-02, C-03) va 6.2 yangilanadi.*
