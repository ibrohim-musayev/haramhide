# Golden set — yig'ish va kalibratsiya protokoli

> **Bu loyihaning yagona qolgan bloklovchisi.** Model ishlaydi, lekin uning
> recall va precision qiymatlari o'lchanmagan. Threshold qiymatlari
> (`Sensitivity.kt`) — TZ dagi taxminlar, o'lchovga asoslanmagan.
>
> Bu hujjat o'sha ishni **bajarilishi mumkin bo'lgan vazifaga** aylantiradi.

---

## 1. Nima uchun kerak

Hozir ilova haqida quyidagilarni ayta olamiz:

* Model yuklanadi va tez ishlaydi
* Kiyingan odamlarga default rejimda blur qo'ymaydi

Quyidagilarni **ayta olmaymiz**:

* Haqiqiy kontentning qanchasini ushlaydi (recall)
* Ushlaganlarining qanchasi to'g'ri (precision)
* Threshold qiymatlari to'g'rimi
* Klasslarni sezgirlik darajalariga taqsimlash to'g'rimi

Golden setsiz ilova haqida **"aniq ishlaydi" deb aytish mumkin emas** va
marketingda aniqlik da'vosi qilinmasligi kerak.

---

## 2. Huquqiy va etik cheklovlar

Bular muhokama qilinmaydi (TZ 8.6):

| Qoida | |
|---|---|
| Manba | Faqat qonuniy, kattalar uchun, ochiq litsenziyali |
| Voyaga yetmaganlar | Ehtimoli bor har qanday manba — **mutlaq taqiq** |
| Saqlash | Faqat shaxsiy qurilmada, shifrlangan diskda |
| Repozitoriya | **Hech qachon.** `.gitignore` da `/goldenset/` bor |
| Bulut | Hech qachon. Sinxronizatsiya papkasiga qo'ymang |
| Ulashish | Hech kimga. Faqat natijalar jadvali ulashiladi |

Agar biror rasm haqida shubha bo'lsa — uni ishlatmang. Golden set kichikroq
bo'lgani, huquqiy muammodan yaxshi.

---

## 3. Tuzilishi

```
goldenset/
  nsfw/                     <- blur QO'YILISHI kerak
    ochiq/                    aniq yalang'ochlik
    qisman/                   qisman ochiqlik
  safe/                     <- blur QO'YILMASLIGI kerak
    sport/                    sportchi, mashq, gimnastika
    tibbiyot/                 anatomiya, tibbiy rasm
    sanat/                    haykal, rasm, muzey
    plyaj/                    cho'milish kiyimi, dengiz
    bola/                     bolalar (kiyingan)
    erkak_torso/              yalang'och erkak ko'kragi
    hijob/                    yopiq kiyim, hijob, abaya
    oshxona/                  go'sht, non, pishirilgan taom
    reklama/                  ichki kiyim va cho'milish kiyimi reklamasi
    portret/                  oddiy portret, yuz
    kundalik/                 UI skrinshotlar, matn, xarita
```

**Hajm mo'ljali:** 300–500 rasm, kamida 100 tasi `nsfw/`.

`safe/` dagi kategoriyalar ataylab **qiyin** tanlangan. Ular modelning eng
ko'p xato qiladigan joylari va aynan shular foydalanuvchini bezor qiladi.
`safe/oshxona` va `safe/bola` — eng muhimlari.

---

## 4. Yig'ish tartibi

1. `safe/` dan boshlang. U ochiq manbalardan yig'iladi (Wikimedia Commons,
   Openverse, Unsplash) va huquqiy muammo bermaydi.
2. Har bir kategoriyaga 20–40 rasm.
3. `nsfw/` uchun faqat qonuniy, kattalar uchun, litsenziyasi aniq manba.
4. Rasmlarni **o'zgartirmang** — kesish, filtrlash, sifatni oshirish yo'q.
   Model haqiqiy kontentni ko'rishi kerak.
5. Fayl nomlarida shaxsiy ma'lumot bo'lmasin.

### Nazorat ro'yxati

Yig'ib bo'lgach:

```bash
find goldenset -type f | wc -l              # jami
find goldenset/nsfw -type f | wc -l         # nsfw ulushi
git check-ignore goldenset && echo "gitignore OK"
```

---

## 5. Baholash

```bash
python3 -m pip install --user onnxruntime pillow numpy

# Uchala sezgirlik bo'yicha
python3 tools/evaluate.py goldenset/ --all

# Har bir rasm uchun batafsil
python3 tools/evaluate.py goldenset/ --detail

# Threshold sweep — eng yaxshi juftlikni topish
python3 tools/evaluate.py goldenset/ --sweep

# Natijani saqlash
python3 tools/evaluate.py goldenset/ --all --json natijalar.json
```

### Ekran yo'li taqlid qilinadi

Skript default holda **ekran yo'lini** taqlid qiladi:

```
foto -> ekranda ko'rsatiladi (1080x2400) -> capture (432x960) -> model (256x512)
```

Bu muhim, chunki ilova hech qachon manba faylni ko'rmaydi — u faqat ekran
nusxasini ko'radi, va bu yo'lda aniqlik bir necha barobar yo'qoladi.

Farq amalda o'lchandi: kiyingan ayol surati STRICT rejimda
* manba fayl ustida — `FEMALE_BREAST_COVERED=0.30` (yolg'on ijobiy)
* ekran yo'lida — aniqlash yo'q

Va aynan ikkinchisi qurilmadagi natijaga mos keldi. Ya'ni **manba fayl
ustida kalibrlash noto'g'ri threshold beradi.**

Model darajasidagi taqqoslash uchun `--raw` bor, lekin mahsulot qarorlari
default rejimga asoslanishi kerak.

---

## 6. Natijalarni qo'llash

1. `--sweep` eng yaxshi `t_low` / `t_det` juftligini beradi.
2. Ularni `core-detect/.../Sensitivity.kt` ga ko'chiring.
3. `docs/F1-NATIJALAR.md` §5 va TZ 6.3 dagi o'lchanmagan qatorlarni
   haqiqiy raqamlar bilan almashtiring.
4. Natijalar jadvalini repozitoriyaga qo'shing (rasmlarni EMAS).

### Qabul mezonlari (TZ 6.3)

| Metrika | Mo'ljal |
|---|---|
| Recall | ≥ 0.90 |
| Precision | ≥ 0.85 |
| FPR (neytral kontent) | ≤ 0.03 |
| `safe/bola` da xato | ≤ 0.02 |

Agar bularga erishilmasa, bu **model xatosi emas** — biz modelni
o'zgartira olmaymiz. Variantlar:

* threshold'ni qayta sozlash (recall va precision o'rtasidagi almashuv)
* klasslarni sezgirlik darajalari bo'yicha qayta taqsimlash
* Stage A darvozasini yumshatish (agar NSFW rasmlar darvozada to'xtayotgan bo'lsa)
* mo'ljallarni pasaytirish va buni hujjatda ochiq aytish

Oxirgisi ham qabul qilinadigan javob. Yolg'on raqam yozishdan yaxshi.

---

## 7. Regressiya

Golden set yig'ilgach, har relizdan oldin:

```bash
python3 tools/evaluate.py goldenset/ --all --json natijalar.json
```

Metrikalar tushsa reliz to'xtaydi (TZ 10). Bu qo'lda bajariladi — golden set
CI'ga hech qachon yuklanmaydi.

---

## 8. Skript va Android kodi mos bo'lishi shart

`tools/evaluate.py` Android kodidagi mantiqni takrorlaydi:

| Skript | Android manbasi |
|---|---|
| `LABELS`, `BLUR_SETS` | `NudeNetLabels.kt` |
| `THRESHOLDS` | `Sensitivity.kt` |
| `preprocess`, `postprocess` | `NudeNetDetector.kt` |
| `skin_peak_ratio`, `stage_a_score` | `SkinPrescreen.kt` |
| `simulate_screen` | `CaptureConfig.TIER_B` + `ScreenCapturer.fitInto` |

**Ulardan birortasi o'zgarsa, skript ham o'zgarishi shart.** Aks holda
kalibratsiya jimgina noto'g'ri bo'lib qoladi.
