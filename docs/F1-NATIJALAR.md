# F1 — Model integratsiyasi natijalari

**Sana:** 2026-09-04
**Muhit:** Android 17 (API 37) emulyator, arm64-v8a, 4 yadro, Apple Silicon host
**Model:** NudeNet v3 `320n.onnx` (YOLOv8n), FP32, ONNX Runtime Mobile 1.29.0

> F0 platforma cheklovlarini hal qildi. F1 ning vazifasi — soxta detektor
> o'rniga **haqiqiy model** qo'yish. Bu bosqichdan keyin ilova birinchi marta
> aslida NSFW kontentni aniqlashga qodir bo'ladi.

---

## 1. Qisqacha xulosa

| Talab | Holat |
|---|---|
| Stage B — haqiqiy model | ✅ NudeNet v3 320n, ONNX Runtime Mobile |
| Stage A — darvoza | ⚠️ Model emas, evristik prescreen (2.2-band) |
| Model APK ichida (ADR-005) | ✅ 11.6 MB, tarmoq talab qilinmaydi |
| Klass → sezgirlik moslashuvi | ✅ 18 klass, uch daraja |
| Yolg'on ijobiy tekshiruvi | ✅ Kiyingan odamga blur qo'yilmaydi |
| Kvantizatsiya (INT8) | ❌ Kechiktirildi (2.4-band) |
| **Aniqlik kalibratsiyasi** | ❌ **Bajarilmagan — golden set kerak** (5-bo'lim) |

**Ilova endi ishlaydi, lekin kalibrlanmagan.** Bu farq muhim: model yuklanadi,
tez ishlaydi va aniq yolg'on ijobiylardan xoli, ammo uning haqiqiy recall va
precision qiymatlari **o'lchanmagan**.

---

## 2. Qabul qilingan qarorlar

### 2.1. Kirish o'lchami kvadrat emas

NudeNet ning o'z Python kodi rasmni kvadratga to'ldirib, 320x320 ga kichraytiradi.
Telefon ekrani uchun bu falokat:

```
1080x2400 ekran → 2400x2400 kvadrat → 320x320
kontent atigi 144x320 px bo'lib qoladi
```

Model kirishi dinamik (`images: [batch, 3, height, width]`), shuning uchun
nisbatga mos to'rtburchak ishlatiladi. O'lchov (izolyatsiyalangan, 2 oqim):

| Kirish | Piksel | Mediana | Kontent aniqligi |
|---|---|---|---|
| 224x448 | 100k | 40 ms | 202x448 |
| 320x320 | 102k | 44 ms | 144x320 ← NudeNet usuli |
| 256x512 | 131k | 53 ms | 230x512 |
| 320x640 | 205k | 78 ms | 288x640 |

Vaqt piksel soniga deyarli chiziqli. Ya'ni **bir xil narxga to'rtburchak kirish
kvadratdan ~1.5 barobar ko'p vertikal aniqlik beradi.**

Tanlov (`DetectorConfig`): Tier A — 320x640, **Tier B — 256x512** (default),
Tier C — 224x448.

### 2.2. Stage A model emas

TZ 8.1 da Stage A uchun MobileNetV2 (GantMan/nsfw_model, MIT) rejalashtirilgan edi.
F1 dagi qidiruv shuni ko'rsatdi:

| Nomzod | Litsenziya | Muammo |
|---|---|---|
| GantMan/nsfw_model | MIT | Keras formatida, ONNX yo'q — konversiya kerak |
| Falconsai/nsfw_image_detection | Apache-2.0 | ViT-base (86M) — darvoza uchun 10-30x og'ir |
| AdamCodd/vit-base-nsfw-detector | Apache-2.0 | ViT-base, tayyor INT8 bor, lekin baribir og'ir |
| Freepik/nsfw_image_detector | MIT | ONNX yo'q |
| giacomoarienti/nsfw-classifier | **CC-BY-NC-ND** | Notijorat + hosila taqiqlangan — **yaramaydi** |

Ya'ni tayyor, ruxsat beruvchi litsenziyali va mobil uchun yetarlicha yengil
NSFW klassifikatori mavjud emas.

**Qaror:** Stage A — `SkinPrescreen`, teri rangi mavjudligini tekshiruvchi
evristika. U allaqachon hisoblangan tahlil buferi ustida ishlaydi, 0 MB oladi
va ~0.3 ms turadi.

Bu mudofaa qilinadigan tanlov: yalang'ochlik teri ko'rinishini nazarda tutadi,
ya'ni darvozaning **yolg'on salbiy** berish ehtimoli past — darvozadan aynan
shu talab qilinadi. Yolg'on ijobiylar zararsiz: ular shunchaki Stage B ni
ishga tushiradi.

### 2.3. Kulrang kadr uchun fail-open

Teri qoidasi `R > G > B` ga tayanadi. Qora-oq yoki kuchli rang filtri qo'yilgan
kadrda bu shart hech qachon bajarilmaydi — darvoza **butun kadrni tashlab
yuborardi**, ya'ni qora-oq kontent umuman tekshirilmay qolardi.

Yechim: kadrning o'rtacha rang to'yinganligi o'lchanadi. Juda past bo'lsa qoida
ishonchsiz deb belgilanadi va darvoza ochiq qoladi (ball = 1.0).

O'lchov bilan tasdiqlandi (3-test): qora-oq foto — `teri=0.00`, lekin `A=1.00`
va `stageB=59%`, ya'ni Stage B ishga tushdi.

### 2.4. Kvantizatsiya kechiktirildi

INT8 ga o'tkazish kalibrlash uchun haqiqiy rasmlar to'plamini talab qiladi, u esa
hozircha yo'q. FP32 11.6 MB APK chegarasiga (45 MB) bemalol sig'adi.
Golden set yig'ilgach qayta ko'riladi (TZ 8.5).

---

## 3. Ishlash o'lchovlari

| Bosqich | Vaqt |
|---|---|
| Preprocessing (letterbox + NCHW) | 1–2 ms |
| ONNX run, **izolyatsiyalangan** | 40–53 ms (256x512) |
| ONNX run, **ishlayotgan pipeline ichida** | 210–500 ms |
| Postprocessing (NMS) | < 1 ms |

**Izolyatsiyalangan va real o'lchov orasida 4-8 barobar farq bor.** Sabab
aniqlanmagan; ehtimoliy omillar: kesh raqobati (capture bitmap nusxalari),
emulyatorning 4 yadrosi, host mashinasidagi yuk.

Bu **emulyator raqamlari va real qurilma uchun mo'ljal emas.** Ular faqat bitta
xulosa uchun yetarli: preprocessing va postprocessing muammo emas, butun narx
ONNX chaqiruvida. Optimizatsiya kerak bo'lsa u yerdan boshlash kerak
(kvantizatsiya, kirish o'lchamini kichraytirish, NNAPI/GPU delegate).

Diqqat: **statik ekranda kadr umuman kelmaydi** (F0 §3.4), shuning uchun
tinch ekranda inference narxi nolga teng.

---

## 4. Tekshirilgan stsenariylar

Barchasi ochiq litsenziyali, betaraf test materiali bilan. **Hech qanday NSFW
material ishlatilmagan va repozitoriyaga qo'shilmagan** (TZ 8.6).

| Test | Sezgirlik | Natija | Xulosa |
|---|---|---|---|
| Rangli foto, to'liq kiyingan ayol (Prokudin-Gorskiy, PD) | MEDIUM | `mask=0/0`, `labels=[]` | ✅ Yolg'on ijobiy yo'q |
| Xuddi shu | STRICT | `mask=0/0` | ✅ STRICT da ham tegmadi |
| Qora-oq foto, kiyingan erkak (PD) | MEDIUM | `teri=0.00`, `A=1.00`, `stageB=59%` | ✅ Fail-open ishladi |

Birinchi test eng muhimi: TZ 8.4 dagi etik talab — kiyingan odamni blur
qilmaslik — amalda tasdiqlandi.

`FACE_FEMALE` va `FACE_MALE` klasslari kodda **hech qachon blur qilinmaydigan**
ro'yxatga kiritilgan. Model yuzni aniqlay olishi TZ 8.4 da olib tashlangan
"kiyingan ayol siymosini blurlash" funksiyasini qaytarish uchun sabab emas.

---

## 5. Bajarilmagan: aniqlik kalibratsiyasi

**Bu F1 ning yakunlanmagan qismi va uni kod bilan hal qilib bo'lmaydi.**

Hozir ma'lum:
* Model yuklanadi va ishlaydi
* Kiyingan odamlarga yolg'on ijobiy bermaydi
* Klass filtri va sezgirlik darajalari ishlaydi

Hozir **noma'lum**:
* Recall — haqiqiy NSFW kontentning qanchasini ushlaydi
* Precision — ushlaganlarining qanchasi to'g'ri
* Threshold qiymatlari (`Sensitivity.tLow`, `tDet`) to'g'rimi — ular hozir
  TZ 8.3 dagi **boshlang'ich taxminlar**, o'lchovga asoslanmagan
* Klasslarni sezgirlik darajalariga taqsimlash to'g'rimi

Buni aniqlash uchun **golden set** kerak (TZ 8.5): 300–500 ta qo'lda tanlangan
qiyin holat. Uni yig'ish kod ishi emas va uni men bajara olmayman —
huquqiy va etik jihatdan bu loyiha egasining qaroridir.

Golden set yig'ilmaguncha ilova haqida **"aniqlik" da'vosi qilinmasligi kerak**.

---

## 6. Keyingi qadamlar

| # | Ish | Faza |
|---|---|---|
| 1 | Golden set yig'ish va threshold kalibratsiyasi | F1 (yakunlash) |
| 2 | Real qurilmada ishlash o'lchovi | F1 |
| 3 | INT8 kvantizatsiya (golden set bo'lgach) | F2 |
| 4 | Probe oynasini qisqartirish (ADR-007) | F2 |
| 5 | Ishga tushishdagi benchmark bilan tier avtomatik tanlash (NFR-201) | F2 |
