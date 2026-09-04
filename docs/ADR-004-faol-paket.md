# ADR-004 — Faol ilovani aniqlash: UsageStatsManager

**Holat:** Qabul qilindi (2026-09-04)
**Kontekst:** TZ v2.1, 3.5 (C-05) va FR-106

## Qaror
Birlamchi mexanizm — `UsageStatsManager.queryEvents()` + `PACKAGE_USAGE_STATS`.
AccessibilityService **ishlatilmaydi** (v1 da umuman yo'q, keyin ixtiyoriy modul).

## Sabab
* Google Play accessibility API dan faqat haqiqiy yordamchi texnologiyalarga
  ruxsat beradi; `isAccessibilityTool` e'lon qilish bizga to'g'ri kelmaydi va
  Play Protect ogohlantirishiga olib keladi.
* Android 17 Advanced Protection Mode yoqilganda rasmiy accessibility tool
  bo'lmagan ilovalar bu ruxsatni umuman ololmaydi.
* UsageStats torroq qamrovli va Play deklaratsiyasi soddaroq.

## Oqibatlar
* Scroll hodisalari to'g'ridan-to'g'ri olinmaydi — scroll kadr-diff orqali
  bilvosita aniqlanadi (`FrameSignals.globalDelta`).
* Ilova almashishi ~400 ms so'rov oralig'i bilan aniqlanadi (FR-106 mezoni ≤ 500 ms).
* AccessibilityService keyin qo'shilsa ham, usiz ilova to'liq ishlashi shart.
