# HaramHide

Android ekranida real vaqtda nomaqbul tasvirlarni **qurilmaning o'zida** aniqlab,
ular ustiga xiralashtiruvchi qatlam chizadigan ilova.

> **Holat: F0 — texnik prototip.** Bu hali mahsulot emas. ML modeli yo'q,
> uning o'rnida evristik soxta detektor ishlaydi. F0 ning yagona maqsadi —
> platforma cheklovlari hal qilinadimi yoki yo'qmi shuni aniqlash.
> Natijalar: [`docs/F0-NATIJALAR.md`](docs/F0-NATIJALAR.md)

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
   ├─ Stage A: yengil klassifikator (darvoza)  kadrlarning ~95 % shu yerda tugaydi
   ├─ Stage B: detektor (bbox)                 faqat darvozadan o'tganda
   │
   ├─ Mask State Machine   ← C-04 (miltillash) shu yerda hal qilinadi
   └─ Overlay render (WindowManager)
```

### Modullar

| Modul | Vazifasi |
|---|---|
| `:core-capture` | MediaProjection, ImageReader, kadr signallari, qora kadr aniqlash |
| `:core-detect` | Stage A/B interfeyslari, evristik soxta detektor (F0) |
| `:core-overlay` | **Mask State Machine**, blur render, WindowManager |
| `:core-context` | UsageStats orqali faol paketni aniqlash |
| `:core-data` | DataStore sozlamalari |
| `:app` | UI (Compose), foreground xizmat, bildirishnomalar |

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

---

## Hujjatlar

| Fayl | Mazmuni |
|---|---|
| [`TZ_v2.1_HaramHide.md`](TZ_v2.1_HaramHide.md) | Texnik topshiriq (SRS) |
| [`docs/F0-NATIJALAR.md`](docs/F0-NATIJALAR.md) | F0 o'lchovlari va topilmalar |
| [`docs/ADR-*.md`](docs/) | Arxitektura qarorlari |
| [`NOTICE`](NOTICE) | Uchinchi tomon litsenziyalari |

---

## Litsenziya

[AGPL-3.0-or-later](LICENSE).

Bu tanlov majburiy edi: bbox qaytaradigan ochiq NSFW detektori faqat AGPL
ostida mavjud (NudeNet v3, u ham `ultralytics YOLOv8n` asosida).
Sabablari: [`docs/ADR-002-litsenziya-agpl.md`](docs/ADR-002-litsenziya-agpl.md)
