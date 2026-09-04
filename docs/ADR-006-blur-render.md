# ADR-006 — Blur render: kichraytirib-kattalashtirish

**Holat:** Qabul qilindi (2026-09-04) · F0 da ishlaydi
**Kontekst:** TZ v2.1, FR-202

## Muammo
Overlay ostidagi kontentni blur qilish kerak. `RenderEffect` (API 31+) View'ning
**o'z** mazmunini blur qiladi, ostidagini emas. `LayoutParams.setBlurBehindRadius`
butun oynaga tegishli, hudud bo'yicha emas.

## Qaror
Capture'dan kelgan kadrni [BlurSpec.downscale] marta kichraytirib, mask
to'rtburchagiga bilinear filtr bilan qayta chizish.

```
small = frame.bitmap  →  w/N x h/N   (N = 4..40, intensivlikka qarab)
canvas.drawBitmap(small, srcRectInSmall, maskRectOnScreen, filterPaint)
```

## Sabab
* API 26 dan boshlab ishlaydi — alohida fallback kerak emas.
* Bir kadrga bitta kichraytirish, keyin har mask uchun bepul.
* Uchala uslub bir mexanizmdan chiqadi: filtr bilan = GAUSSIAN,
  filtrsiz = PIXELATE, bitmap'siz = SOLID.

## Oqibatlar
* Blur manbai — **joriy kadr**, ya'ni u allaqachon oldingi blur'ni o'z ichiga
  olishi mumkin (blur-ustiga-blur). Bu zararsiz: natija baribir blur bo'lib qoladi.
* Ko'rinadigan mask bo'lmasa kichraytirish umuman bajarilmaydi.
* Kadr va ekran o'rtasidagi kechikish tufayli blur mazmuni bir oz eskiroq —
  blur'langan tasvirda bu sezilmaydi.
