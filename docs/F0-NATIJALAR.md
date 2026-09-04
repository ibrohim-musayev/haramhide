# F0 — Texnik prototip natijalari

**Sana:** 2026-09-04
**Muhit:** Android 17 (API 37) emulyator, arm64-v8a, Pixel 7 profili, Apple Silicon host
**Build:** `0.1.0-F0`, AGP 9.4.0 / Kotlin 2.4.10 / Gradle 9.7.1
**Capture:** Tier B (960 px uzun tomon, 8 FPS mo'ljal), `RECORD_CONTENT_DISPLAY`

> F0 ning yagona maqsadi — TZ C-01…C-04 cheklovlari **haqiqatan ham hal
> qilinadimi** yoki yo'qmi shuni bilish. Bu bosqichda mahsulot qurilmaydi:
> ML modeli yo'q, UI to'liq emas. Agar bu yerda muammo hal bo'lmasa, keyingi
> 6 oylik ish behuda ketardi.

---

## 1. Qisqacha xulosa

| Cheklov | Holat | Izoh |
|---|---|---|
| **C-01** — har sessiyada rozilik | ✅ Tasdiqlandi va hal qilindi | Android 14+ dialogi bir bosishga tushirildi |
| **C-02** — qulfda sessiya uziladi | ✅ Tasdiqlandi va hal qilindi | Yangi nyuans topildi (3.2-band) |
| **C-03** — FLAG_SECURE qora kadr | ⚠️ Kod bor, emulyatorda tekshirilmadi | Real bank ilovasi kerak |
| **C-04** — overlay↔capture halqasi | ✅ Qayta hosil qilindi va **yechildi** | Asosiy natija, 2-bo'lim |
| **FR-103** — qulfdan keyin tiklash | ✅ Ishlaydi | 1 bosish |
| **FR-105** — 0 miltillash | ✅ **Bajarildi** (PROBE siyosati bilan) | 20 s da 0 |
| **FR-107** — rotatsiya | ✅ Ishlaydi | 432x960 ↔ 960x432 |

**Xulosa: F0 ijobiy. Loyihani davom ettirish mumkin.** Ammo 5-bo'limdagi
ochiq risklar real qurilmada qayta tekshirilishi shart.

---

## 2. C-04 — miltillash muammosi va uning yechimi

### 2.1. Muammo qanday qayta hosil qilindi

Soxta detektor ataylab **kontentga bog'liq** qilib yozildi ([`HeuristicDetector`]):
katak "issiq" deb belgilanadi, agar unda (a) teri rangi ulushi va (b) **chekka
energiyasi** yuqori bo'lsa. Blur chekka energiyasini yo'q qiladi — xuddi haqiqiy
NSFW modeli shakl va teksturaga tayangani kabi.

O'lchov buni to'g'ridan-to'g'ri ko'rsatdi:

```
mask yo'q:      edge = 12.9
mask qo'yilgach: edge = 5.4
```

Ya'ni blur qo'yilishi bilan detektorning kirish signali ikki barobar kamaydi.
Himoyasiz tizimda bu darhol miltillashga olib keladi.

### 2.2. Birinchi urinish muvaffaqiyatsiz bo'ldi

`ReleasePolicy.PROBE` da mask timeout'dan keyin overlay 120 ms ga yashiriladi va
ostiga qaraladi. Natija:

```
11:23:18  mask=0/1  probe=0/1   <- probe TASDIQLAMADI
11:23:19  mask=1/2  flicker=1   <- MILTILLASH
```

**Sabab:** capture 1–5 FPS da ishladi, ya'ni bitta kadr 200–1000 ms. 120 ms lik
oyna bitta kadrdan ham qisqa edi — probe hali **blur'langan eski kadrni** baholab,
"kontent yo'q" degan xulosaga keldi.

### 2.3. Yechim: kutishni vaqtda emas, kadrda o'lchash

`MaskConfig.probeFrames = 3` qo'shildi. Probe tugashi uchun **ikkala** shart kerak:
vaqt oynasi tugashi **va** overlay yashiringan holatda 3 ta kadr kelishi.

### 2.4. ADR-003 tajribasi — uch siyosat, bir xil sharoit

Har biri 20 soniya, xuddi shu statik test namunasi ustida:

| Siyosat | Miltillash | Probe (tasdiq/jami) | Mask (faol/yaratilgan) |
|---|---|---|---|
| **PROBE** | **0** ✅ | 1/1 | 1/2 |
| TIMEOUT_ONLY | **1** ❌ | — | 1/2 |
| MOTION_ONLY | **0** ✅ | — | 1/1 |

**Tanlov: `PROBE`.**
`TIMEOUT_ONLY` ko'r-ko'rona ochadi va miltillaydi. `MOTION_ONLY` da miltillash
yo'q, lekin mask hech qachon bo'shatilmaydi — statik ekranda u abadiy qoladi va
foydalanuvchi normal kontentni ko'ra olmaydi.

**PROBE ning narxi:** 3 kadr × ~200 ms = **~600 ms davomida kontent ochiq qoladi**.
TZ da bu ~16 ms deb taxmin qilingan edi (`SurfaceControl` orqali). WindowManager
overlay bilan bunga erishib bo'lmadi. Bu ochiq masala — 5.1-bandga qarang.

---

## 3. Boshqa topilmalar

### 3.1. Android 14+ default holda BUTUN EKRANNI EMAS, bitta ilovani uzatadi

Bu eng xavfli topilma, chunki u **jimgina** ishdan chiqaradi.

Rozilik dialogida default tanlov — «Share one app». Foydalanuvchi shuni tanlasa,
tizim `RECORD_CONTENT_TASK` rejimiga o'tadi va faqat bitta ilova oynasini uzatadi.
Bizning ilovamiz esa hech qanday xato olmaydi — shunchaki boshqa ilovalardan
kadr kelmaydi. Kontent filtri butunlay foydasiz bo'lib qoladi.

F0 da bu amalda kuzatildi:

```
mSession: ContentRecordingSession { contentToRecord = RECORD_CONTENT_TASK,
          tokenToRecord = Task{... com.google.android.gm} }
```

**Yechim:** `MediaProjectionConfig.createConfigForDefaultDisplay()` (API 34+) —
tanlov imkoniyati butunlay olib tashlanadi, dialog darhol «Share entire screen»
ko'rsatadi va bitta tugma qoladi.

**API 33 va past uchun bunday config yo'q** — u yerda onboarding'da tushuntirish
va ishga tushgach aniqlash kerak. Bu F2 uchun ochiq vazifa.

### 3.2. C-02 aniqlashtirildi: sessiya faqat XAVFSIZ qulfda uziladi

TZ da "ekran qulflansa uzatish avtomatik to'xtaydi" deyilgan. O'lchov aniqroq
manzarani ko'rsatdi:

| Holat | Sessiya |
|---|---|
| Ekran o'chdi, qulf yo'q (swipe) | **Tirik qoladi** |
| Ekran o'chdi, PIN o'rnatilgan | **Uziladi** |

Ya'ni R-01 riski faqat qulf o'rnatgan foydalanuvchilarga tegishli — lekin bu
maqsadli auditoriyaning aksariyati. Risk baholanishi o'zgarmaydi.

### 3.3. Tizim broadcast'lari `RECEIVER_EXPORTED` talab qiladi

`ACTION_USER_PRESENT` `RECEIVER_NOT_EXPORTED` bilan ro'yxatdan o'tkazilganda
umuman kelmadi — qulf ochilgach hech narsa bo'lmadi. `RECEIVER_EXPORTED` ga
o'tkazilgach ishladi. Himoyalangan broadcast bo'lgani uchun bu xavfsiz:
boshqa ilova bu action'ni yubora olmaydi.

### 3.4. Statik ekranda kadr umuman kelmaydi

VirtualDisplay faqat kontent o'zgarganda yangi kadr beradi. To'liq statik ekranda
`onImageAvailable` chaqirilmaydi. Bu **foyda**: statik ekran = nol energiya sarfi.
Lekin mask state machine vaqt bo'yicha ishlaydi (timeout, fade) va kadrsiz u
ham to'xtaydi. Hozircha zararsiz (mask ham o'zgarmaydi), ammo F2 da
`Choreographer` bilan mustaqil taymer kerak bo'lishi mumkin.

### 3.5. Scroll Shield o'z-o'zini ushlab qolgan edi

Bitta chegara bilan: overlay kadrga tushadi → delta beradi → Scroll Shield
yonadi → yana delta → o'chmaydi. Bu C-04 halqasining Scroll Shield'dagi
ko'rinishi. Schmitt trigger (kirish 22, chiqish 9, 2 ketma-ket kadr) muammoni
yechdi.

---

## 4. Ishlash o'lchovlari (emulyator — mo'ljal emas)

| Metrika | O'lchandi | TZ Tier B mo'ljali |
|---|---|---|
| Kadr ishlovi (o'rtacha) | 2–17 ms | — |
| Kadr ishlovi (maksimal) | 313 ms (rotatsiya paytida) | — |
| FPS | 1–7 (kontent o'zgarishiga bog'liq) | 8 |
| Stage B ishga tushish ulushi | 45–78 % | ~5 % maqsad |

**Bu raqamlar mahsulot bahosi emas.** Emulyator, soxta detektor va statik
namuna — uchalasi ham haqiqiy sharoitdan uzoq. Ayniqsa Stage B ulushi 45–78 %
juda yuqori: test namunasi butun ekranni egallaydi va doim ijobiy. Haqiqiy
kontentda bu ~5 % bo'lishi kutiladi.

Diqqat qiladigan yagona raqam — **rotatsiyadagi 313 ms**: bitmap buferlari
qayta ajratilishi. F2 da bufer hovuzi kerak bo'lishi mumkin.

---

## 5. Ochiq masalalar

### 5.1. Probe oynasi 600 ms — bu ko'p

TZ ~16 ms deb mo'ljallagan edi (`SurfaceControl` orqali overlay'ni bitta kadrga
ko'rinmas qilish). WindowManager overlay bilan bunga erishilmadi. Variantlar:

* Capture FPS ni oshirish (probe kadrda o'lchanadi → 12 FPS da 3 kadr = 250 ms)
* Probe paytida mask'ni butunlay olib tashlamasdan, uning **kichik bir qismini**
  ochish (masalan markazdagi 20 %) — kontent to'liq ochilmaydi
* `SurfaceControl.Transaction` bilan overlay qatlamini capture'dan chiqarish
  (tekshirilmagan, cheklangan bo'lishi mumkin)

**Tavsiya:** F1 da qisman ochish (2-variant) sinab ko'rilsin — u eng arzon.

### 5.2. Real qurilmada tekshirilmagan

Emulyatorda **tekshirib bo'lmagan** narsalar TZ 12.2 da sanab o'tilgan va
ular o'zgarishsiz qolmoqda: OEM battery killer, haqiqiy batareya sarfi,
harorat, glass-to-glass kechikish, FLAG_SECURE (C-03), status bar cast chip'ini
foydalanuvchi bosib to'xtatishi.

### 5.3. Mask chegarasi kontentni to'liq qoplamaydi

Skrinshotda mask chetida namunaning bir qismi ochiq qolgani ko'rindi.
`expandFraction = 0.10` yetarli emas. F2 da kengaytirishni katak o'lchamiga
bog'lash kerak.

---

## 6. Takrorlash yo'riqnomasi

```bash
# Muhit
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export ANDROID_HOME=~/Library/Android/sdk

# Build va o'rnatish
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Ruxsatlar
adb shell appops set com.haramhide.app.debug SYSTEM_ALERT_WINDOW allow
adb shell appops set com.haramhide.app.debug GET_USAGE_STATS allow
adb shell pm grant com.haramhide.app.debug android.permission.POST_NOTIFICATIONS

# Sessiyani boshlash (rozilik dialogini qo'lda tasdiqlash kerak — TZ C-01)
adb shell am start -n com.haramhide.app.debug/com.haramhide.app.MainActivity

# Tajriba (debug build'dagi boshqaruv qabul qiluvchisi orqali)
adb shell am broadcast -a com.haramhide.app.debug.CONTROL \
  -n com.haramhide.app.debug/com.haramhide.app.DebugControlReceiver \
  --es policy PROBE --ez reset true --ez pattern true

# O'lchovlarni kuzatish
adb logcat -s HaramHideMetrics:I
```

Sessiya turini tekshirish (RECORD_CONTENT_DISPLAY bo'lishi shart):

```bash
adb shell dumpsys media_projection | grep contentToRecord
```
