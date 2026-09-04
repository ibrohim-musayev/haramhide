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

## Qayta ko'rib chiqish sharti
Agar F2 da Tier C qurilmalarda Stage B 60 ms dan oshsa — LiteRT + GPU delegate
sinovdan o'tkaziladi.
