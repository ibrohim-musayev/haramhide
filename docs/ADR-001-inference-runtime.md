# ADR-001 — Inference runtime: ONNX Runtime Mobile

**Holat:** Qabul qilindi (2026-09-04) · F1 da amalga oshiriladi
**Kontekst:** TZ v2.1, 8.2-band

## Muammo
Tanlangan modellar turli formatda: NudeNet `320n.onnx` (ONNX), GantMan
`nsfw_model` (Keras/TF). Bitta runtime tanlash kerak.

## Variantlar

| | ONNX Runtime Mobile | LiteRT (TFLite) |
|---|---|---|
| NudeNet | To'g'ridan-to'g'ri ishlaydi | `onnx2tf` konversiyasi kerak |
| Konversiya riski | Yo'q | YOLOv8 uchun nozik (NMS, transpose) |
| Runtime hajmi | ~5–8 MB AAR | ~2–3 MB |
| Android NPU/GPU | QNN EP, XNNPACK | Kuchliroq ekotizim |

## Qaror
**v1 uchun ONNX Runtime Mobile.**

Sabab: yakka dasturchi uchun eng katta xavf — konversiyada kunlab tiqilib qolish.
NudeNet allaqachon ONNX'da, uni o'zgartirmaslik F1 ni bloklamaslikning eng
ishonchli yo'li. Hajm farqi (~5 MB) APK'da sezilarli emas.

## Oqibatlar
* `:core-detect` da `StageAClassifier` / `StageBDetector` interfeyslari runtime'dan
  mustaqil — LiteRT'ga o'tish uchun faqat implementatsiya almashadi.
* v1 da NPU ishlatilmaydi (TZ C-06 izohiga qarang), faqat XNNPACK va GPU.
* F-Droid uchun ORT AAR ning manba holati alohida tekshirilishi kerak.

## F1/F3 dagi amaliy natija

**Ishlashi bo'yicha qaror o'zini oqladi.** Model konversiyasiz ishladi,
preprocessing 1-2 ms, postprocessing < 1 ms, R8 minifikatsiyasidan keyin ham
muammosiz yuklandi.

**Hajm bo'yicha esa qimmatga tushdi.** ONNX Runtime har bir ABI uchun
~32-38 MB native kutubxona olib keladi:

| ABI | `libonnxruntime.so` |
|---|---|
| x86_64 | 38.5 MB |
| x86 | 38.4 MB |
| arm64-v8a | 32.1 MB |
| armeabi-v7a | 22.7 MB |

To'rttasi birga universal APK ni **145 MB** qildi — TZ 6.2 chegarasidan
(45 MB) uch barobar ko'p. Yechim: ABI ajratish (x86 olib tashlandi, u faqat
emulyatorda kerak) + R8. Natija: **arm64-v8a 43 MB, armeabi-v7a 34 MB.**

Ya'ni chegara ichida, lekin deyarli to'la. LiteRT runtime'i ~2-3 MB bo'lardi,
ya'ni bu farq ADR-001 ning to'g'ridan-to'g'ri narxi.

## Qayta ko'rib chiqish sharti

Quyidagilardan biri yuz bersa LiteRT sinovdan o'tkaziladi:

1. Tier C qurilmalarda Stage B 60 ms dan oshsa
2. APK hajmi muammo bo'lsa (masalan model kvantizatsiyadan keyin ham
   43 MB dan kam qilib bo'lmasa)
3. NPU/GPU delegate zarur bo'lib qolsa

Konversiya riski F1 da o'lchanmagan — u faqat *ehtimoliy* deb baholangan edi.
Qayta ko'rishdan oldin `onnx2tf` bilan bir marta sinab ko'rish arzon.
