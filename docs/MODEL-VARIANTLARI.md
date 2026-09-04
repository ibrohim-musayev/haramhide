# Model variantlari — o'lchangan taqqoslash

**Sana:** 2026-09-04
**Dataset:** 237 neytral rasm (`goldenset/safe/`), ekran yo'li taqlid qilingan

> Savol shu edi: modelni **treningsiz** aniqroq qilish mumkinmi?
> Javob: ha, lekin uni hozir ishlatib bo'lmaydi. Sabab quyida.

---

## 1. Nega trening emas

| To'siq | |
|---|---|
| Ma'lumot | Bbox bilan belgilangan NSFW dataset kerak. Uni yig'ish loyiha egasining qarori, va u hali yo'q |
| Hisoblash | Fine-tuning GPU talab qiladi. Mavjud mashinada YOLOv8 treningi amaliy emas |
| Xavf | Bir necha yuz rasmda fine-tuning modelni **yomonlashtirishi** ehtimoli yuqori (catastrophic forgetting) |
| O'lchash | Trening natijasini baholash uchun baribir `nsfw/` to'plami kerak |

Ya'ni trening ham o'sha bloklovchiga borib taqaladi. Lekin **treningsiz**
qilinadigan narsalar bor va ular o'lchandi.

---

## 2. Natijalar

| Model | Hajmi | LOW | MEDIUM | STRICT | Ball nisbati |
|---|---|---|---|---|---|
| **320n fp32** *(hozirgi)* | 12.2 MB | 0.008 | 0.030 | 0.055 | — |
| 320n INT8 | 5.5 MB | 0.008 | 0.021 | 0.059 | 0.60 |
| 640m fp32 | 103.5 MB | 0.004 | 0.017 | 0.046 | — |
| **640m INT8** | 37.6 MB | 0.004 | **0.013** | 0.046 | 0.87 |

*Ball nisbati* — kvantlangan modelning eng yuqori klass ballari fp32 ga
nisbatan. 1.00 dan qanchalik uzoq bo'lsa, threshold'lar shunchalik
noto'g'ri bo'lib qoladi.

**640m sezilarli aniqroq:** MEDIUM FPR 0.030 → 0.017 (fp32 vs fp32), ya'ni
yolg'on ijobiylar deyarli ikki barobar kam. Bu haqiqiy model sifati farqi.

**640m kvantizatsiyani ham yaxshi ko'taradi:** ball nisbati 0.87, 320n da
esa 0.60. Kattaroq modelda zaxira ko'proq.

---

## 3. Nega model hozir almashtirilmadi

**FPR ning pasayishi o'z-o'zidan yaxshi xabar emas.** Kamroq aniqlaydigan
model ham past FPR beradi. Ball nisbati 0.87 va 0.60 aynan shuni ko'rsatadi:
kvantlangan modellar kamroq narsani ushlaydi.

Recall o'lchanmagan (`nsfw/` yo'q), demak:

* 320n → 640m o'tish **ehtimol** yaxshilaydi (fp32 vs fp32 taqqoslovi toza)
* lekin INT8 ga o'tish recall'ni qanchalik tushirishini **bilmaymiz**
* va APK 43 MB dan ~68 MB ga chiqadi (TZ 6.2 chegarasi 45 MB)

Tekshirilmagan o'zgarish — mahsulotning asosiy xatti-harakatiga tegadigan
o'zgarish. Shuning uchun hozirgi model qoldirildi.

### Qaror mezoni

`nsfw/` to'plami yig'ilgach:

```bash
python3 tools/evaluate.py goldenset/ --all                          # 320n
python3 tools/evaluate.py goldenset/ --all --model 640m_int8.onnx   # 640m
```

640m INT8 ni tanlash sharti: **recall 320n dan past emas** va FPR pastroq.
Agar shunday bo'lsa, APK hajmining o'sishi oqlanadi.

---

## 4. Kvantizatsiya — nima ishlamadi

Bu qism kelajakda takrorlanmasligi uchun yozilgan.

**`quantize_dynamic` ishlamaydi.** U konvolyutsiyalarni `ConvInteger` ga
o'tkazadi, ONNX Runtime CPU esa uni qo'llab-quvvatlamaydi:

```
NOT_IMPLEMENTED: Could not find an implementation for ConvInteger(10)
```

**`quantize_static` ham dastlab ishlamadi** — model hamma rasmda aynan
`0.000` qaytardi. Uchta sozlama varianti (per_channel, activation type)
sinaldi, hech biri yordam bermadi.

**Sabab:** YOLOv8 ning chiqish boshi (`/model.22/*`, 155 node) bbox
dekodlash arifmetikasini bajaradi — Slice, Sub, Div, Mul, Concat. Uni
kvantlash natijani butunlay buzadi. `nodes_to_exclude` bilan chiqarib
tashlangach ishladi.

**Eng xavfli tomoni: bu xato jimgina.** O'lik model metrikalarda
`FPR 0.000` beradi va bu muvaffaqiyatga o'xshab ko'rinadi. Shuning uchun
`tools/quantize.py` endi kvantizatsiyadan keyin natijani **avtomatik
tekshiradi** va model o'lik bo'lsa xato bilan to'xtaydi.

---

## 5. Vositalar

```bash
# INT8 ga o'tkazish (kalibratsiya goldenset/safe dan olinadi)
python3 tools/quantize.py model.onnx model_int8.onnx goldenset/

# Taqqoslash
python3 tools/evaluate.py goldenset/ --all --model model_int8.onnx
```

`tools/quantize.py` chiqish boshini avtomatik chiqarib tashlaydi
(`--exclude-prefix`, default `/model.22/`) va natijani tekshiradi.

---

## 6. Xulosa

| Savol | Javob |
|---|---|
| Treningsiz aniqroq qilish mumkinmi? | **Ha** — 640m modeli MEDIUM FPR ni 0.030 dan 0.017 ga tushiradi |
| Uni hozir ishlatish mumkinmi? | **Yo'q** — recall o'lchanmagan, APK 68 MB ga chiqadi |
| Trening qilish mumkinmi? | **Yo'q** — ma'lumot, hisoblash va baholash imkoniyati yo'q |
| Nima kerak? | `nsfw/` to'plami. Hamma yo'l shunga borib taqaladi |
