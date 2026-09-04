# Real qurilmada test — nazorat ro'yxati

> Barcha mavjud o'lchovlar Android 17 **emulyatorida** olingan. Quyidagilar
> emulyatorda tekshirib bo'lmaydi va ular hamon noma'lum.
>
> Bu hujjat telefon topilganda testni ochiq savoldan ro'yxatga aylantiradi.

---

## 0. Tayyorgarlik

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Qurilma haqida yozib qo'ying: ishlab chiqaruvchi, model, Android versiyasi,
SoC, RAM, ekran o'lchami. Ular natijalarni tushunish uchun kerak.

```bash
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.board.platform
```

---

## 1. Sessiya turini tekshirish — BIRINCHI QADAM

Boshqa hech narsani sinashdan oldin:

```bash
adb shell dumpsys media_projection | grep contentToRecord
```

`RECORD_CONTENT_DISPLAY` bo'lishi **shart**. Agar `RECORD_CONTENT_TASK`
chiqsa — ilova faqat bitta oynani ko'ryapti va boshqa hamma test ma'nosiz
(TZ C-13).

Android 13 va undan pastda `MediaProjectionConfig` yo'q, ya'ni foydalanuvchi
noto'g'ri tanlashi mumkin. Shu versiyalarda buni alohida tekshiring.

---

## 2. C-02 — qulf va tiklash oqimi

**Bu R-01, loyihaning eng katta riski.**

| # | Qadam | Kutilgan |
|---|---|---|
| 1 | Xavfsiz qulf (PIN/naqsh/barmoq izi) o'rnatilganini tekshiring | — |
| 2 | Himoyani yoqing | `RECORD_CONTENT_DISPLAY` |
| 3 | Ekranni qulflang | Sessiya uziladi |
| 4 | Qulfni oching | Heads-up bildirishnoma darhol chiqadi |
| 5 | Bildirishnomani bosing | Rozilik dialogi ochiladi |
| 6 | Tasdiqlang | Himoya tiklanadi |

```bash
adb logcat -s ProtectionService:I | grep -E "Sessiya uzildi|Qulf ochildi"
```

**Asosiy savol raqamda emas, tajribada:** buni kuniga 30–50 marta qilish
qanchalik chidab bo'lmas? Bir kun normal ishlatib ko'ring va sessiya
uzilishlari sonini yozing (ilova ichidagi kunlik statistikada bor).

Agar bu chidab bo'lmas bo'lsa — mahsulot g'oyasi qayta ko'rib chiqiladi.
Bu TZ R-01 da shunday yozilgan.

---

## 3. C-03 — FLAG_SECURE (emulyatorda umuman sinalmagan)

Quyidagilarni navbat bilan oching va nima bo'lishini yozing:

* Bank ilovasi
* Parol menejeri
* Telegram maxfiy chat
* Netflix / DRM pleyer

```bash
adb logcat -s HaramHideMetrics:I | grep -o "secureFrames=[0-9]*"
```

Kutilgan: qora kadr aniqlanadi, `fail-open` rejimida blur qo'yilmaydi.
Sozlamada `fail-closed` ni yoqib, butun ekran blur bo'lishini ham tekshiring.

**Diqqat:** bank ilovasi ichida qora kadr kelishi normal. Lekin agar
`fail-closed` da bank ilovasi butunlay yopilib qolsa — bu ishlatib
bo'lmaydigan holat va default `fail-open` to'g'ri tanlov ekanini tasdiqlaydi.

---

## 4. Ishlash — haqiqiy raqamlar

Emulyator raqamlari mo'ljal emas. Quyidagilar qayta o'lchanishi kerak.

### 4.1. Inference

```bash
adb logcat -s HaramHideMetrics:I | grep -o "runAvg=[0-9]*/[0-9]*"
```

Emulyatorda: izolyatsiyalangan 40–53 ms, pipeline ichida 210–500 ms.
**Bu 4–8 barobar farqning sababi aniqlanmagan** — real qurilmada ham
shundaymi yoki bu emulyator artefaktimi, shu yerda hal bo'ladi.

### 4.2. Batareya (TZ 6.2: ≤ 12 %/soat)

```bash
adb shell dumpsys batterystats --reset
# 1 soat normal foydalaning: Instagram/brauzer, himoya yoqiq
adb shell dumpsys batterystats > batt.txt
adb shell dumpsys batterystats | grep -A5 "com.haramhide"
```

Solishtirish uchun himoya o'chiq holatda ham 1 soat o'lchang.

### 4.3. Harorat (TZ 6.2: ≤ +8 °C, 30 daq)

```bash
adb shell dumpsys thermalservice | grep -i temperature
```

30 daqiqa uzluksiz ishlating, boshida va oxirida o'lchang.
`THERMAL_STATUS_MODERATE` ga yetsa FPS yarmiga tushishi kerak (NFR-202).

### 4.4. Jank (TZ 6.2: < 5 % frame drop)

```bash
adb shell dumpsys gfxinfo com.instagram.android reset
# 2 daqiqa Instagram'da scroll qiling
adb shell dumpsys gfxinfo com.instagram.android | grep -E "Janky|Total frames"
```

Himoya yoqiq va o'chiq holatda solishtiring.

### 4.5. Glass-to-glass kechikish (TZ 6.2: ≤ 300 ms p95)

Boshqa telefonning sekin tortish (slow-mo, 240 fps) rejimi bilan:

1. Ekranda kontent paydo bo'lishini va blur qo'yilishini yozib oling
2. Kadrlarni sanang: kontent ko'ringan kadr → blur to'liq qo'yilgan kadr
3. 240 fps da har kadr 4.17 ms

Kamida 10 marta takrorlab, p95 ni hisoblang.

---

## 5. C-12 — OEM battery killer

Ishlab chiqaruvchiga qarab (ilova ichida yo'riqnoma bor):

| # | Qadam |
|---|---|
| 1 | Himoyani yoqing |
| 2 | Ilovani recents'dan **swipe qilib yoping** |
| 3 | 30 daqiqa kuting, telefonni normal ishlating |
| 4 | Xizmat tirikmi tekshiring |

```bash
adb shell dumpsys activity services com.haramhide.app.debug | grep ProtectionService
```

Keyin ilova ichidagi OEM yo'riqnomasini bajarib, qaytadan sinang.
Farq bo'lsa — yo'riqnoma ishlayapti; bo'lmasa — matnni tuzatish kerak.

Xiaomi, Oppo, Vivo, Huawei uchun bu deyarli albatta muammo beradi (TZ R-08).

---

## 6. C-09 — cast chip

Android 15 QPR1+ da ekran yuqorisida katta yozib olish belgisi turadi.

* Foydalanuvchi uni bosib to'xtata oladimi?
* To'xtatgach ilova buni aniqlaydimi (`Sessiya uzildi` logi)?
* Belgi kundalik foydalanishda qanchalik bezor qiladi?

Oxirgisi — subyektiv, lekin mahsulot uchun muhim.

---

## 7. Aniqlik — haqiqiy kontentda

Golden set (`docs/GOLDEN-SET.md`) desktopda kalibrlanadi. Qurilmada esa
boshqa savol tekshiriladi: **haqiqiy foydalanishda qanday?**

Bir kun normal ishlating va yozing:

* Necha marta xato blur qo'yildi (ilova ichidagi jurnalda belgilang)
* Necha marta o'tkazib yuborildi
* Scroll paytida kontent ko'rinib qoldimi (Scroll Shield ishlayaptimi)
* Probe paytidagi ochilish sezildimi (ADR-007, ~600 ms)

```bash
adb logcat -s HaramHideMetrics:I | grep -o "flicker=[0-9]*"
```

Miltillash 0 bo'lib qolishi kerak.

---

## 8. Soak (TZ 12: 8 soat)

Emulyatordagi 9 daqiqalik soak modelni umuman ishlatmadi (ekranda mos
kontent yo'q edi). Real qurilmada to'liq soak:

```bash
# Har 5 daqiqada
adb shell dumpsys meminfo com.haramhide.app.debug | grep "TOTAL PSS"
adb shell dumpsys thermalservice | grep -i temperature
adb logcat -d -s HaramHideMetrics:I | tail -1
```

Kuzatish kerak: PSS o'sib ketmasin (TZ 6.2: ≤ 280 MB), harorat barqaror,
sessiya uzilishlari soni, miltillash 0.

---

## 9. Natijalarni yozish

Tugagach `docs/QURILMA-NATIJALARI.md` yarating va TZ ning quyidagi
joylarini haqiqiy raqamlar bilan yangilang:

| TZ bo'limi | Nima yangilanadi |
|---|---|
| 3.2 (C-02) | Qulf uzilishining kundalik chastotasi |
| 3.3 (C-03) | FLAG_SECURE haqiqiy xatti-harakati |
| 6.2 | Barcha ishlash raqamlari |
| 14 (R-01) | Risk baholanishi — hal bo'ldimi yoki mahsulot qayta ko'riladimi |
| ADR-001 | Inference tezligi LiteRT'ga o'tishni talab qiladimi |

Va `docs/F0-NATIJALAR.md` §5.2 dagi "tekshirilmagan" ro'yxatini yoping.
