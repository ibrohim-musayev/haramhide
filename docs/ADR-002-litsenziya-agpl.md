# ADR-002 — Litsenziya: AGPL-3.0-or-later

**Holat:** Qabul qilindi (2026-09-04)
**Kontekst:** TZ v2.1, 8.1 va 10.1

## Muammo
Loyihaga bbox qaytaradigan NSFW detektori kerak. Uni o'zi o'qitish yakka
dasturchi va nol byudjet uchun imkonsiz (TZ 2-bo'lim).

## Topilma
2026-09-04 holatiga **ruxsat beruvchi litsenziyali ochiq NSFW detektori mavjud emas.**

| Model | Tur | Litsenziya |
|---|---|---|
| NudeNet v3 `320n.onnx` | **detektor** | AGPL-3.0 (repo + YOLOv8n og'irliklari) |
| GantMan/nsfw_model | klassifikator | MIT |
| opennsfw2 | klassifikator | MIT + BSD-2 |
| Falconsai/nsfw_image_detection | klassifikator | Apache-2.0 |
| AdamCodd/vit-base-nsfw-detector | klassifikator | Apache-2.0 |

Ya'ni tanlov ikkitadan iborat edi:
1. Yopiq kod → faqat klassifikator → bbox yo'q → butun ekran yoki katak darajasida blur
2. AGPL → NudeNet ishlatiladi → aniq bbox, trening kerak emas

## Qaror
**AGPL-3.0-or-later, to'liq ochiq kod.**

Ilova bepul va monetizatsiya rejalashtirilmagan, ya'ni himoyalanadigan tijorat
siri yo'q. AGPL bu holatda hech narsani yo'qotmaydi va uchta narsani beradi:
NudeNet, F-Droid kanali, hamda "piksel qurilmadan chiqmaydi" da'vosini
tekshirib bo'ladigan qilish (TZ 10.2 dagi uchinchi tomon auditining bepul o'rnini bosadi).

## Oqibatlar
* Har bir bog'liqlik AGPL-3.0 bilan mos bo'lishi shart. GPL-2.0-**only** — mos emas.
* **Firebase / Google Play Services ishlatilmaydi** (yopiq kod + F-Droid rad etadi).
  Demak: Crashlytics, Analytics, Remote Config, Test Lab, Billing — hech qaysisi yo'q.
* Relizda manba kodiga havola majburiy.
* AGPL §13 (tarmoq) amalda tegishli emas — server yo'q.
