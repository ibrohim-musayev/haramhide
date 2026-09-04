# ADR-003 — Mask'ni bo'shatish siyosati: PROBE

**Holat:** Qabul qilindi (2026-09-04) · F0 o'lchovi bilan tasdiqlangan
**Kontekst:** TZ v2.1, 3.4 (C-04) va FR-105 · O'lchov: `docs/F0-NATIJALAR.md`

## Muammo
Overlay oynasi ham VirtualDisplay'ga tushadi. Blur qo'yilgach detektor
blur'langan hududni ko'radi va "kontent yo'q" deydi. Mask ko'r zona bo'lgani
uchun uni **qachon** bo'shatishni bilish kerak.

## Variantlar va F0 o'lchovi

20 soniya, bir xil statik test namunasi, Android 17 emulyator:

| Siyosat | Miltillash | Kamchiligi |
|---|---|---|
| **PROBE** | **0** | Kontent ~600 ms ochiq qoladi |
| TIMEOUT_ONLY | 1 | Ko'r-ko'rona ochadi |
| MOTION_ONLY | 0 | Mask hech qachon bo'shalmaydi |

## Qaror
**`ReleasePolicy.PROBE`**, quyidagi tuzatish bilan:

Probe kutishi **vaqtda emas, kadrda** o'lchanadi (`probeFrames = 3` va
`probeWindowMs = 120` — ikkalasi ham bajarilishi shart).

Bu tuzatishsiz probe ishlamadi: capture 1–5 FPS da ishlaganda 120 ms bitta
kadrdan ham qisqa bo'lib chiqdi va probe hali blur'langan eski kadrni baholab,
mask'ni noto'g'ri bo'shatdi → miltillash.

## Oqibatlar
* Mask timeout'dan keyin kontent ~600 ms ochiq qoladi (3 kadr × ~200 ms).
  Bu TZ dagi ~16 ms mo'ljalidan ancha uzoq va **hal qilinmagan masala**.
* Foydalanuvchi uchun sozlama sifatida uchala siyosat ham qoldirildi —
  turli qurilmalarda turlicha bo'lishi mumkin.

## Keyingi qadam (F1)
Probe paytida mask'ni to'liq olib tashlamasdan, uning **markazidagi ~20 % ni**
ochish sinab ko'riladi. Bu kontentni to'liq ochmaydi, lekin detektorga
yetarli signal berishi mumkin.
