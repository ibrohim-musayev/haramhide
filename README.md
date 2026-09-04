# HaramHide

Android ekranida real vaqtda nomaqbul tasvirlarni **qurilmaning o'zida** aniqlab,
ular ustiga xiralashtiruvchi qatlam chizadigan ilova.

> **Holat: F1 — model integratsiyasi.** Ilova endi haqiqiy model bilan ishlaydi
> (NudeNet v3, on-device), lekin **aniqligi kalibrlanmagan** — golden set hali
> yig'ilmagan. Ya'ni u ishlaydi, ammo uning recall/precision qiymatlari
> o'lchanmagan va ilova haqida "aniqlik" da'vosi qilinmasligi kerak.
>
> Natijalar: [`docs/F0-NATIJALAR.md`](docs/F0-NATIJALAR.md) (platforma cheklovlari) ·
> [`docs/F1-NATIJALAR.md`](docs/F1-NATIJALAR.md) (model)

---

## Nima uchun bu ilova boshqacha

| | |
|---|---|
| **Tarmoqqa chiqmaydi** | `INTERNET` ruxsati manifestda umuman e'lon qilinmagan. Piksel qurilmadan chiqishi texnik jihatdan imkonsiz. |
| **Server yo'q** | Hisob yo'q, telemetriya yo'q, bulut yo'q. Modellar APK ichida. |
| **Ochiq kod** | AGPL-3.0. "Ma'lumot yig'ilmaydi" degan da'voni har kim tekshirishi mumkin. |
| **Bepul** | Reklama, obuna, paywall yo'q. |
| **Google kutubxonalarisiz** | Firebase / Play Services ishlatilmaydi — F-Droid uchun. |

Bu **xavfsizlik mahsuloti emas**, o'z-o'zini nazorat vositasi. U hech qachon
100 % kafolat bermaydi va buni ochiq aytadi.

---

## Arxitektura

```
MediaProjection → VirtualDisplay → ImageReader
   │
   ├─ Gate 1: kadr-diff (64x64 luma SAD)      ~0.3 ms — o'zgarmagan kadr tashlanadi
   ├─ Gate 2: faol ilova filtri (UsageStats)
   │
   ├─ Stage A: teri rangi prescreen (darvoza)  ~0.3 ms, model emas
   ├─ Stage B: NudeNet v3 (YOLOv8n, ONNX)      faqat darvozadan o'tganda
   │
   ├─ Mask State Machine   ← C-04 (miltillash) shu yerda hal qilinadi
   └─ Overlay render (WindowManager)
```

### Modullar

| Modul | Vazifasi |
|---|---|
| `:core-capture` | MediaProjection, ImageReader, kadr signallari, qora kadr aniqlash |
| `:core-detect` | Stage A/B interfeyslari, NudeNet ONNX detektori, evristik prescreen |
| `:core-overlay` | **Mask State Machine**, blur render, WindowManager |
| `:core-context` | UsageStats orqali faol paketni aniqlash |
| `:core-data` | DataStore sozlamalari |
| `:app` | UI (Compose), foreground xizmat, bildirishnomalar |

### Model

| | |
|---|---|
| Stage B | NudeNet v3 `320n.onnx` — YOLOv8n asosida, 18 klass |
| Runtime | ONNX Runtime Mobile 1.29.0, CPU (XNNPACK) |
| Joylashuvi | APK ichida (11.6 MB) — tarmoq talab qilinmaydi |
| Litsenziya | AGPL-3.0 (`core-detect/src/main/assets/MODEL_NOTICE.txt`) |
| Kvantizatsiya | Yo'q (FP32) — kalibrlash uchun golden set kerak |

`FACE_FEMALE` va `FACE_MALE` klasslari **hech qachon blur qilinmaydi**.
Model yuzni aniqlay olishi TZ 8.4 da etik sabablarga ko'ra olib tashlangan
"kiyingan ayol siymosini blurlash" funksiyasini qaytarish uchun sabab emas.

---

## Asosiy texnik muammo: C-04

Overlay oynasi ham VirtualDisplay'ga tushadi:

1. Kadr N — NSFW aniqlandi, blur chizildi
2. Kadr N+1 — detektor blur'langan hududni ko'radi, "NSFW emas" deydi, blur olindi
3. Kadr N+2 — kontent yana ochiq, blur qaytdi → **miltillash**

Yechim — [`MaskStateMachine`](core-overlay/src/main/kotlin/com/haramhide/core/overlay/MaskStateMachine.kt):
mask qo'yilgan hudud **ko'r zona** deb belgilanadi va u yerdagi detektsiya
natijasi mask'ni bo'shatish uchun ishlatilmaydi. Bo'shatish faqat tashqi
signallarga tayanadi: ilova almashishi, halqa o'zgarishi, scroll, yoki
nazorat ostidagi "probe".

Asosiy assimetriya: **blur qo'yish tez, olib tashlash sekin.**

O'lchangan natija (20 s, statik namuna): `PROBE` siyosati bilan **0 miltillash**.
Batafsil: [`docs/ADR-003-mask-boshatish.md`](docs/ADR-003-mask-boshatish.md)

---

## Qurish

Talablar: JDK 21, Android SDK (API 37, build-tools 37).

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=$HOME/Library/Android/sdk

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ruxsatlarni ilovaning o'zidan bering, yoki test uchun:

```bash
PKG=com.haramhide.app.debug
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell appops set $PKG GET_USAGE_STATS allow
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
```

### F0 o'lchovini takrorlash

```bash
adb logcat -s HaramHideMetrics:I
```

Batafsil yo'riqnoma: [`docs/F0-NATIJALAR.md`](docs/F0-NATIJALAR.md) 6-bo'lim.

---

## Ma'lum cheklovlar

Bular **hal qilinmagan** va hujjatlashtirilgan:

* **Har sessiyada rozilik.** Android 14+ har `createVirtualDisplay()` uchun
  yangi rozilik talab qiladi. Chetlab o'tib bo'lmaydi.
* **Xavfsiz qulfda sessiya uziladi.** PIN o'rnatilgan qurilmada ekran
  qulflansa uzatish to'xtaydi. Qulf ochilgach bir bosishlik tiklash so'raladi.
* **FLAG_SECURE oynalari.** Bank ilovalari, parol menejerlari qora kadr beradi —
  ularni himoyalab bo'lmaydi.
* **Probe oynasi ~600 ms.** Mask timeout'dan keyin kontent qisqa vaqt ochiq
  qoladi. Yechim izlanmoqda ([ADR-003](docs/ADR-003-mask-boshatish.md)).
* **Real qurilmada sinalmagan.** Barcha o'lchovlar emulyatorda olingan.
* **Aniqlik kalibrlanmagan.** Threshold qiymatlari — o'lchovga emas, taxminga
  asoslangan boshlang'ich qiymatlar. Golden set yig'ilmaguncha ilova haqida
  aniqlik da'vosi qilinmasligi kerak ([F1 §5](docs/F1-NATIJALAR.md)).
* **Stage A model emas** — teri rangi evristikasi. Tayyor, ruxsat beruvchi
  litsenziyali va mobil uchun yengil NSFW klassifikatori mavjud emas.

---

## Hujjatlar

| Fayl | Mazmuni |
|---|---|
| [`TZ_v2.1_HaramHide.md`](TZ_v2.1_HaramHide.md) | Texnik topshiriq (SRS) |
| [`docs/F0-NATIJALAR.md`](docs/F0-NATIJALAR.md) | F0 — platforma cheklovlari |
| [`docs/F1-NATIJALAR.md`](docs/F1-NATIJALAR.md) | F1 — model integratsiyasi |
| [`docs/ADR-*.md`](docs/) | Arxitektura qarorlari |
| [`NOTICE`](NOTICE) | Uchinchi tomon litsenziyalari |

---

## Litsenziya

[AGPL-3.0-or-later](LICENSE).

Bu tanlov majburiy edi: bbox qaytaradigan ochiq NSFW detektori faqat AGPL
ostida mavjud (NudeNet v3, u ham `ultralytics YOLOv8n` asosida).
Sabablari: [`docs/ADR-002-litsenziya-agpl.md`](docs/ADR-002-litsenziya-agpl.md)
