# O'zgarishlar tarixi

Format: [Keep a Changelog](https://keepachangelog.com/), versiyalash: [SemVer](https://semver.org/).

## [Chiqarilmagan]

### Ma'lum cheklovlar
- **Aniqlik kalibrlanmagan.** Model ishlaydi, lekin recall va precision
  o'lchanmagan. Threshold qiymatlari — TZ dagi boshlang'ich taxminlar.
  Golden set (300–500 qiyin holat) yig'ilmaguncha ilova haqida aniqlik
  da'vosi qilinmasligi kerak.
- **Real qurilmada sinalmagan.** Barcha o'lchovlar Android 17 emulyatorida.
  OEM battery killer, batareya sarfi, harorat, glass-to-glass kechikish va
  FLAG_SECURE (bank ilovalari) tekshirilmagan.

---

## 0.1.0 — F3 (2026-09-04)

### Qo'shildi
- `verifyPrivacy` Gradle vazifasi: har `check` da manifestda tarmoq ruxsati
  va kodda piksel yozish chaqiruvlari tekshiriladi (TZ 10.2 / FR-301).
- Aniqlash jurnali (FR-302/303/304): lokal, pikselsiz. Xato blur qo'yilganini
  belgilash mumkin — o'sha hudud bir soat blur qilinmaydi. Eksport qo'lda.
- F-Droid metadatasi (fastlane), uch tilda.
- Reliz build: R8 minifikatsiya, ABI ajratish, imzo konfiguratsiyasi.

### O'zgardi
- Diagnostika qatlami endi **default o'chiq**. Ilgari u yoniq edi va oddiy
  foydalanuvchi ekranida yashil matn ko'rinardi.
- Universal APK 145 MB edi (ONNX Runtime har ABI uchun 22–38 MB native
  kutubxona olib keladi). ABI ajratish va R8 dan keyin: arm64-v8a 43 MB,
  armeabi-v7a 34 MB.

### Tuzatildi
- **`onnxruntime-android` APK'ga `INTERNET` va `ACCESS_NETWORK_STATE`
  ruxsatlarini hamda Microsoft telemetriya provayderini olib kirgan edi.**
  Manba manifestimizda ular yo'q edi — birlashtirishda kelgan. Ilovaning
  asosiy da'vosi bir necha faza davomida yolg'on bo'lgan. `tools:node="remove"`
  bilan olib tashlandi va `verifyPrivacy` endi birlashtirilgan manifestni
  tekshiradi.
- Overlay oynasi operatsiyalari capture oqimidan chaqirilardi. `addView`
  View'ni o'sha oqimning Looper'iga bog'lab qo'yardi va xizmat to'xtaganda
  ishonchsiz holat yuzaga kelardi. Endi hammasi asosiy oqimda.
- `proguard-rules.pro` buzuq yozilgan edi (heredoc xatosi), R8 ishlamasdi.
- Composable ichida `context.getString()` ishlatilgan edi.

---

## 0.1.0 — F2 (2026-09-04)

### Qo'shildi
- Onboarding (FR-001, FR-006) — 4 sahifa, jumladan ilovaning cheklovlari.
- **Cool-down (FR-205)** — himoyani zaiflashtiradigan o'zgarishlar 30 daqiqa
  kechikadi. Qayta so'ralganda taymer nolga tushmaydi.
- Ilovalar bo'yicha tanlash (FR-204), qidiruv va tavsiyalar bilan.
- Tap-to-unblur (FR-208) — mask burchagida 48 dp tugma, kunlik limit.
- Kunlik lokal statistika (FR-305).
- Tier avtomatik tanlash (NFR-201).
- O'zbek, rus, ingliz tillari.
- ADR-007: sinov paytida mask butunlay ochilmaydi, faqat markazi (~20 %).

### Tuzatildi
- Bo'sh ilovalar ro'yxati "hamma ilova" degani — demak bo'shdan aniq
  ro'yxatga o'tish zaiflashtirish. Kod buni kuchaytirish deb hisoblardi.
- Cool-down dastlabki sozlash paytida ham ishlardi.
- Ilovalar ro'yxati bo'sh chiqardi (tizim ilovalari yashirilgan edi).
- Ekran sarlavhalari status bar ostida qolardi.

---

## 0.1.0 — F1 (2026-09-04)

### Qo'shildi
- **NudeNet v3 `320n.onnx`** (YOLOv8n, 18 klass) ONNX Runtime Mobile orqali.
  Model APK ichida, tarmoq talab qilinmaydi.
- Klasslarni sezgirlik darajalariga moslash. `FACE_FEMALE` va `FACE_MALE`
  hech qachon blur qilinmaydi (TZ 8.4).
- Stage A — teri rangi prescreen (model emas, 2.2-band).
- Kirish o'lchami nisbatga mos to'rtburchak (kvadrat emas).

### Tuzatildi
- `startForeground(mediaProjection)` rozilik bo'lmaganda `SecurityException`
  berardi — bildirishnomadagi «To'xtatish» ilovani qulatardi.
- Teri rangi qoidasi kulrang kadrda ishlamasdi va butun ekranni tashlab
  yuborardi. Endi past to'yinganlikda fail-open.

---

## 0.1.0 — F0 (2026-09-04)

### Qo'shildi
- Capture (MediaProjection → VirtualDisplay → ImageReader), kadr signallari,
  qora kadr aniqlash.
- **Mask State Machine** — C-04 (overlay↔capture qayta aloqa halqasi) yechimi.
- Overlay render, WindowManager.
- Faol ilovani aniqlash (UsageStats).
- Foreground xizmat, boot va qulf tiklash oqimlari.

### Topildi
- Android 14+ default holda butun ekranni emas, bitta ilovani uzatadi —
  va bu jimgina ishdan chiqaradi. `MediaProjectionConfig` bilan majburlandi.
- MediaProjection faqat **xavfsiz** qulfda uziladi (PIN o'rnatilgan bo'lsa).
- Tizim broadcast'lari `RECEIVER_EXPORTED` talab qiladi.
- Probe kutishi vaqtda emas, kadrda o'lchanishi shart.
