# HaramHide

Android ekranida real vaqtda nomaqbul tasvirlarni **qurilmaning o'zida** aniqlab,
ular ustiga xiralashtiruvchi qatlam chizadigan ilova.

> **Holat: F2 — foydalanish mumkin, lekin kalibrlanmagan.**
>
> Ilova haqiqiy model bilan ishlaydi (NudeNet v3, qurilmada), onboarding,
> cool-down, ilovalar ro'yxati va uch til bor. Ammo uning **aniqligi
> o'lchanmagan** — golden set hali yig'ilmagan, threshold qiymatlari esa
> taxminga asoslangan. Shu sababli ilova haqida "aniqlik" da'vosi
> qilinmasligi kerak.
>
> Natijalar: [F0](docs/F0-NATIJALAR.md) (platforma) ·
> [F1](docs/F1-NATIJALAR.md) (model) · [F2](docs/F2-NATIJALAR.md) (mahsulot)

---

## Nima uchun bu ilova boshqacha

| | |
|---|---|
| **Tarmoqqa chiqmaydi** | Chiqqan APK'da `INTERNET` ruxsati yo'q — buni `aapt2 dump permissions` bilan o'zingiz tekshirishingiz mumkin. `./gradlew check` har build'da buni majburlaydi. |
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

### Mahsulot funksiyalari

| | |
|---|---|
| Onboarding | 4 sahifa — nima ishlaydi, nima ishlamaydi, ma'lumot qayerga ketmaydi |
| **Cool-down** | Himoyani zaiflashtiradigan har qanday o'zgarish 30 daqiqa kechikadi. Taymer qayta bosilganda **nolga tushmaydi** |
| Ilovalar ro'yxati | Qidiruv va tavsiyalar bilan. Bo'sh = hamma ilova |
| Tap-to-unblur | Mask burchagidagi tugmani 2 s bosib tursangiz 5 s ochiladi. Kunlik limit bor |
| Kunlik statistika | Faqat qurilmada. Himoya vaqti, blur soni, sessiya uzilishlari |
| Tillar | O'zbek, rus, ingliz |

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
# ABI bo'yicha ajratilgan — emulyator/telefon arm64 bo'lsa:
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Reliz (ABI bo'yicha ajratilgan, R8 bilan):

```bash
./gradlew :app:assembleRelease
# app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk   (43 MB)
# app/build/outputs/apk/release/app-armeabi-v7a-release-unsigned.apk (34 MB)
```

Imzo kaliti muhit o'zgaruvchilaridan olinadi (`HARAMHIDE_STORE_FILE`,
`HARAMHIDE_STORE_PASSWORD`, `HARAMHIDE_KEY_ALIAS`, `HARAMHIDE_KEY_PASSWORD`).
Kalit repozitoriyaga hech qachon qo'shilmaydi.

### Maxfiylik tekshiruvi

`./gradlew check` har safar `verifyPrivacy` vazifasini bajaradi va build'ni
to'xtatadi, agar:

* **birlashtirilgan** manifestda tarmoq ruxsati paydo bo'lsa
* kodda piksel diskka yozadigan chaqiruv paydo bo'lsa

Manba manifest emas, aynan birlashtirilgani tekshiriladi. Sabab F3 da
amalda ko'rildi: `onnxruntime-android` o'z manifestida `INTERNET`,
`ACCESS_NETWORK_STATE` va Microsoft telemetriya provayderini e'lon qiladi,
va ular birlashtirishda APK'ga kirib qolgan edi. Manba manifestimizda ular
hech qachon bo'lmagan — ya'ni faqat manbani tekshirish yolg'on xotirjamlik
beradi. Batafsil: [F3 §1](docs/F3-NATIJALAR.md).

Tekshirishning eng ishonchli yo'li — chiqqan APK:

```bash
aapt2 dump permissions app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk
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
* **Probe paytida mask markazi ochiladi** (~20 % maydon). Bu F1 dagi to'liq
  ochilishdan yaxshi, lekin nol emas ([ADR-007](docs/F2-NATIJALAR.md)).
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
| [`docs/F2-NATIJALAR.md`](docs/F2-NATIJALAR.md) | F2 — mahsulot funksiyalari |
| [`docs/F3-NATIJALAR.md`](docs/F3-NATIJALAR.md) | F3 — reliz tayyorgarligi |
| [`CHANGELOG.md`](CHANGELOG.md) | O'zgarishlar tarixi |
| [`docs/ADR-*.md`](docs/) | Arxitektura qarorlari |
| [`NOTICE`](NOTICE) | Uchinchi tomon litsenziyalari |

---

## Litsenziya

[AGPL-3.0-or-later](LICENSE).

Bu tanlov majburiy edi: bbox qaytaradigan ochiq NSFW detektori faqat AGPL
ostida mavjud (NudeNet v3, u ham `ultralytics YOLOv8n` asosida).
Sabablari: [`docs/ADR-002-litsenziya-agpl.md`](docs/ADR-002-litsenziya-agpl.md)
