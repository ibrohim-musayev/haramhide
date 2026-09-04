# F2 — Mahsulot funksiyalari

**Sana:** 2026-09-04
**Muhit:** Android 17 (API 37) emulyator

> F0 platforma cheklovlarini hal qildi, F1 haqiqiy modelni ulandi.
> F2 ilovani **foydalanish mumkin** holatga keltiradi: onboarding, cool-down,
> ilovalar ro'yxati, tap-to-unblur, uch til.

---

## 1. Bajarilgan talablar

| ID | Talab | Holat |
|---|---|---|
| FR-001, FR-006 | Prominent disclosure, kutilmani to'g'ri o'rnatish | ✅ 4 sahifali onboarding |
| FR-002 | Ruxsatlar sehrgari, har biri uchun sabab | ✅ |
| FR-003 | OEM-ga xos yo'riqnoma | ✅ 5 ta ishlab chiqaruvchi |
| FR-201 | Sezgirlik: LOW / MEDIUM / STRICT | ✅ |
| FR-202, FR-203 | Blur uslubi va kuchi | ✅ |
| FR-204 | Ilovalar bo'yicha whitelist | ✅ qidiruv, tavsiyalar bilan |
| **FR-205** | **Cool-down** | ✅ o'lchov bilan tasdiqlangan (3-bo'lim) |
| FR-208 | Tap-to-unblur, kunlik limit | ✅ mask burchagidagi tugma |
| FR-305 | Lokal kunlik statistika | ✅ |
| NFR-201 | Ishga tushishdagi benchmark → tier | ✅ |
| ADR-007 | Probe oynasini qisqartirish | ✅ qisman ochish (2-bo'lim) |
| TZ 7 | uz / ru / en | ✅ |

**Qolgan (v1 uchun majburiy emas):** FR-206 (PIN qulfi, S), FR-207
(Accountability partner, C), FR-209 (uninstall protection, S), FR-304
(lokal false-positive ro'yxati, S).

---

## 2. ADR-007 — probe oynasi qisqartirildi

F1 dan keyin ochiq masala shu edi: sinov paytida mask butunlay olib
tashlanardi va kontent ~600 ms davomida **to'liq** ochiq qolardi.

Yechim: sinov paytida mask yo'qolmaydi, faqat **markazi ochiladi**
(chiziqli 45 % = maydonning ~20 %). Chizishda `Canvas.clipOutRect`
ishlatiladi — u API 26 dan mavjud, ya'ni minSdk bilan mos.

Detektor markazdagi signalni ko'radi va sinov ishlaydi, foydalanuvchi esa
kontentning atigi beshdan bir qismini ko'radi.

`MaskConfig.probeHoleFraction = 0` qilinsa eski xatti-harakat qaytadi.

---

## 3. Cool-down — o'lchangan xatti-harakat (FR-205)

Emulyatorda tekshirildi:

| Qadam | Kutilgan | Natija |
|---|---|---|
| «To'xtatish» bosildi | Himoya to'xtamaydi, taymer boshlanadi | ✅ Holat «Faol», «Qoldi: 29 daq» |
| 65 s kutildi | Taymer sanaydi | ✅ «Qoldi: 28 daq» |
| «To'xtatish» qayta bosildi | **Taymer nolga tushmaydi** | ✅ hamon 28 daq |
| «Bekor qilish» | So'rov yo'qoladi | ✅ |

Oxirgidan bittasi TZ FR-205 ning aniq talabi edi: *«qayta boshlansa nolga tushmaydi»*.

### Ikki mantiqiy tuzatish

**1. Bo'sh ro'yxat «hamma ilova» degani.** Ya'ni bo'sh ro'yxatdan aniq
ro'yxatga o'tish qamrovni **toraytiradi** — bu zaiflashtirish. Dastlabki
kodda bu kuchaytirish deb hisoblangan edi va cool-downdan o'tib ketardi.

**2. Cool-down faqat himoya yoqilgan bo'lsa ishlaydi.** Dastlabki sozlash
paytida odam hali hech narsaga majburiyat olmagan — u yerda 30 daqiqalik
kechikish shunchaki to'siq bo'lardi. Kechikish qaror qabul qilingandan
**keyin** ma'noga ega.

Bu mantiq `CoolDownPolicy` ga sof funksiyalar sifatida ajratilgan va
10 ta unit test bilan qoplangan.

---

## 4. Tap-to-unblur (FR-208)

Overlay oynasini teginiladigan qilish tegishni ostidagi ilovaga o'tkazmaydi —
Android'da bunday imkoniyat yo'q. Ya'ni butun mask teginiladigan bo'lsa,
uning ostidagi kontentni scroll qilib ham bo'lmaydi.

Shuning uchun teginiladigan hudud **alohida 48 dp lik oyna** — mask
burchagidagi kichik tugma. 48 dp Material minimal tegish o'lchami
(TZ 7-bo'lim accessibility talabi) va scroll'ga xalaqit bermaydi.

2 soniya bosib turilsa halqa to'ladi va blur 5 soniyaga ochiladi.
Kunlik limit (default 5) tugasa tugma ishlamaydi va xabar chiqadi.

---

## 5. Tier avtomatik tanlash (NFR-201)

Model birinchi yuklanganda mikro-benchmark ishlaydi va natijaga qarab
kirish o'lchami tanlanadi:

```
< 35 ms  -> Tier A (320x640)
< 120 ms -> Tier B (256x512)
>= 120ms -> Tier C (224x448)
```

Emulyatorda o'lchandi: 39 ms → **Tier B**. Natija saqlanadi va keyingi
ishga tushishlarda qayta o'lchanmaydi.

Chegaralar emulyator o'lchoviga asoslangan **boshlang'ich qiymatlar** va
real qurilmalarda qayta ko'rilishi kerak.

---

## 6. F2 da topilgan xatolar

**Ilovalar ro'yxati bo'sh chiqardi.** Tizim ilovalari default holda
yashirilgan edi. Telefonlarda esa brauzer, YouTube va ko'p ijtimoiy
ilovalar oldindan o'rnatilgan bo'ladi (`FLAG_SYSTEM`), ya'ni filtr aynan
himoyalash kerak bo'lgan ilovalarni ro'yxatdan chiqarib yuborardi.
Default `true` ga o'zgartirildi.

**Ekran sarlavhalari status bar ostida qolardi** — `systemBarsPadding()`
qo'shildi.

**Apostrof escape qilinmagan** — o'zbekcha matnlarda `'` belgisi Android
resurslarida `\'` bo'lishi shart, aks holda build to'xtaydi.

---

## 7. Hamon ochiq

* **Aniqlik kalibrlanmagan** — golden set kerak (F1 §5). Bu F2 da ham
  o'zgarmadi va loyihaning asosiy bloklovchisi bo'lib qolmoqda.
* **Real qurilmada sinalmagan** — barcha o'lchovlar emulyatorda.
* Probe oynasining qolgan ta'siri o'lchanmagan: markaz ochilishi
  detektorga yetarli signal beradimi degan savol real kontentda
  tekshirilishi kerak.
