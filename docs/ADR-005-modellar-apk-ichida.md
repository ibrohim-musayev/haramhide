# ADR-005 — Modellar APK ichida, OTA yo'q

**Holat:** Qabul qilindi (2026-09-04)
**Kontekst:** TZ v2.1, 5.5 va 9-bo'lim

## Qaror
Modellar (`320n.onnx` + MobileNetV2) APK'ga joylashtiriladi. Model OTA
mexanizmi v1 da **yo'q**, v2 ga suriladi.

## Sabab
* Nol byudjet: model hosting, imzo infratuzilmasi, versiya registri — hammasi
  server talab qiladi. Server yo'q.
* Modellar INT8 da ~16 MB — APK'ga bemalol sig'adi (TZ 6.2 chegarasi 45 MB).
* Bir butun muammolar sinfi yo'qoladi: imzo tekshiruvi, rollback, qisman
  yuklab olish, internetsiz holat.
* F-Droid ishga tushgandan keyin ikkilik fayl yuklab olishni yoqtirmaydi.

## Oqibatlar
* Model yangilanishi = ilova yangilanishi. Bu sekinroq, lekin ishonchli.
* `INTERNET` ruxsati manifestda umuman e'lon qilinmaydi (TZ 10.2).
* Threshold'larni masofadan sozlash ham yo'q — faqat ilova ichidagi sozlama.
