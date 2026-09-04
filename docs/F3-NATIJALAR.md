# F3 — Reliz tayyorgarligi

**Sana:** 2026-09-04
**Muhit:** Android 17 (API 37) emulyator

---

## 1. Eng muhim topilma: ONNX Runtime tarmoq ruxsatini olib kirgan edi

Ilovaning butun asosiy da'vosi shu edi:

> INTERNET ruxsati manifestda e'lon qilinmagan, ya'ni tizim ilovaga
> tarmoqqa chiqishga texnik imkon bermaydi.

**Bu da'vo yolg'on bo'lib chiqdi.** Reliz APK'ni tekshirganda ma'lum bo'ldi:

```
uses-permission: android.permission.INTERNET
uses-permission: android.permission.ACCESS_NETWORK_STATE
provider:        ai.onnxruntime.TelemetryInitializer
```

Manba manifestimizda ular yo'q edi. Ular `onnxruntime-android:1.29.0`
kutubxonasining o'z manifestidan **manifest birlashtirilishida** kelgan:

```
uses-permission#android.permission.INTERNET
  ADDED from [com.microsoft.onnxruntime:onnxruntime-android:1.29.0]
```

Ya'ni ilova bir necha faza davomida tarmoqqa chiqish huquqi va Microsoft'ning
telemetriya provayderi bilan yig'ilgan, hujjatlarda esa buning aksi yozilgan.

### Nima uchun tekshiruv buni ushlamadi

`verifyPrivacy` vazifasi F3 boshida yozilgan edi va u **manba manifestni**
tekshirardi. Manbada ruxsat yo'q edi — tekshiruv "o'tdi" deb aytdi.

Bu tekshiruvning o'zi xato edi: ahamiyatga ega bo'lgan narsa manba emas,
**chiqqan APK**. Tekshiruv yolg'on xotirjamlik bergan.

### Tuzatish

1. Manifestda ruxsatlar va provayder `tools:node="remove"` bilan
   birlashtirishdan chiqarildi.
2. `verifyPrivacy` endi **birlashtirilgan** manifestlarni tekshiradi
   (barcha variant va ABI bo'yicha).

### Tasdiq

Reliz APK, tuzatishdan keyin:

```
uses-permission: FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PROJECTION,
                 SYSTEM_ALERT_WINDOW, POST_NOTIFICATIONS,
                 RECEIVE_BOOT_COMPLETED, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                 PACKAGE_USAGE_STATS, QUERY_ALL_PACKAGES
Telemetry provider: yo'q
```

Va ONNX Runtime'ning o'zi buni tasdiqlaydi:

```
W onnxruntime: [telemetry.cc:453 Initialize] Android telemetry is unavailable
               because the 1DS Java HttpClient was not initialized
```

Model o'zi buzilmadi: yuklanadi, benchmark ishlaydi, tier aniqlanadi.

### Saboq

Har qanday `implementation` bog'liqligi manifestingizga ruxsat qo'sha oladi.
Maxfiylik da'vosi manba kodga emas, **chiqqan artefaktga** tayanishi kerak.

---

## 2. APK hajmi

Universal reliz APK **145 MB** chiqdi — TZ 6.2 chegarasidan (45 MB) uch barobar
ko'p. Sabab: ONNX Runtime har bir ABI uchun alohida native kutubxona:

| ABI | `libonnxruntime.so` |
|---|---|
| x86_64 | 38.5 MB |
| x86 | 38.4 MB |
| arm64-v8a | 32.1 MB |
| armeabi-v7a | 22.7 MB |

Yechim: ABI ajratish (x86 va x86_64 olib tashlandi — ular faqat emulyatorda
kerak) + R8 minifikatsiya.

| APK | Hajmi |
|---|---|
| `app-arm64-v8a-release` | **43 MB** |
| `app-armeabi-v7a-release` | **34 MB** |

Chegara ichida, lekin deyarli to'la. LiteRT runtime'i ~2–3 MB bo'lardi —
bu farq ADR-001 (ONNX Runtime tanlovi) ning to'g'ridan-to'g'ri narxi va
u ADR ga yozildi.

**Diqqat:** R8 birinchi urinishda ishlamadi, chunki `proguard-rules.pro`
buzuq yozilgan edi. Reliz build'ni har o'zgarishdan keyin tekshirish shart —
R8 xatolari debug build'da umuman ko'rinmaydi.

---

## 3. Reliz build tekshiruvi

R8 ONNX Runtime'ni buzishi mumkin edi (u JNI va sinf nomlariga tayanadi).
Tekshirildi:

| Qadam | Natija |
|---|---|
| Reliz APK o'rnatildi va ishga tushdi | ✅ |
| Onboarding ko'rindi | ✅ |
| MediaProjection sessiyasi boshlandi | ✅ |
| ONNX modeli yuklandi (11865 KB) | ✅ |
| Benchmark ishladi, tier aniqlandi | ✅ |
| Qulash | ✅ yo'q |

---

## 4. Soak test

10 daqiqa uzluksiz, reliz build:

| Vaqt | PSS |
|---|---|
| 0s | 100.7 MB |
| 60s | 82.7 MB |
| 120s | 87.4 MB |
| 180s | 88.1 MB |
| 240s | 88.0 MB |
| 360s | 88.5 MB |
| 420s | 88.0 MB |
| 480s | 93.6 MB |
| 540s | 81.9 MB |

Xotira barqaror — oqish belgisi yo'q. Miltillash 0.

**Cheklov:** bu soak testda `runAvg=0/0`, ya'ni **model umuman ishlamadi**.
Ekranda teri rangi bo'lmagani uchun Stage A darvozasi hamma kadrni to'sib
turgan. Ya'ni bu test capture va overlay konveyerini tekshiradi, sustained
inference'ni emas. To'liq soak test uchun ekranda uzluksiz mos kontent
kerak — bu real qurilmada, haqiqiy foydalanishda bajarilishi kerak.

TZ 12 dagi 8 soatlik soak bajarilmagan.

---

## 5. Qo'shilgan boshqa narsalar

- Aniqlash jurnali (FR-302/303/304) — lokal, pikselsiz. Xato blur qo'yilganini
  belgilash, hudud bir soat blur qilinmaydi. Eksport qo'lda ulashish orqali.
- F-Droid metadatasi (fastlane) uch tilda.
- Reliz imzo konfiguratsiyasi — kalit muhit o'zgaruvchilaridan, repozitoriyaga
  hech qachon qo'shilmaydi.
- Diagnostika qatlami endi **default o'chiq**. Ilgari u yoniq edi va oddiy
  foydalanuvchi ekranida yashil matn ko'rinardi — mahsulot xatosi.
- Overlay oyna operatsiyalari asosiy oqimga ko'chirildi. Ilgari ular capture
  oqimidan chaqirilardi va View o'sha oqimning Looper'iga bog'lanib qolardi.

### Tap-to-unblur tekshiruvi

Haqiqiy mask ustida qo'lda sinaldi:

| Qadam | Natija |
|---|---|
| Mask yaratildi (evristik detektor + test namunasi) | ✅ `mask=1/1` |
| Ochish tugmasi mask burchagida ko'rindi | ✅ |
| 2.5 s bosib turildi | ✅ `Ochish berildi, 5000ms` |
| Blur ochildi, tekstura ko'rindi | ✅ |
| 5 s dan keyin mask qaytdi | ✅ `mask=1/1` |

Bu tekshiruv muhim edi, chunki tugma **alohida oyna** va u capture oqimidan
yaratilganda View noto'g'ri Looper'ga bog'lanib qolishi mumkin edi.

---

## 6. Hamon ochiq

1. **Aniqlik kalibrlanmagan** — golden set kerak. O'zgarmadi.
2. **Real qurilmada sinalmagan** — o'zgarmadi.
3. **8 soatlik soak** bajarilmagan; qisqa soak modelni ishlatmadi (4-bo'lim).
4. ~~Tap-to-unblur sinalmagan~~ — **tekshirildi** (5-bo'lim oxiri).
