# Aniqlik o'lchovi — birinchi haqiqiy natijalar

**Sana:** 2026-09-04
**Dataset:** 237 rasm, 11 kategoriya, faqat `safe/`
**Model:** NudeNet v3 `320n.onnx`, Tier B (256x512)
**Rejim:** ekran yo'li taqlid qilingan (`tools/evaluate.py` default)

---

## 1. Nima o'lchandi va nima o'lchanmadi

**O'lchandi: yolg'on ijobiylar (FPR va precision tomoni).**
237 ta neytral, ochiq litsenziyali rasm Wikimedia Commons dan yig'ilib,
11 ta qiyin kategoriyaga ajratildi.

**O'lchanmadi: recall.** Datasetda `nsfw/` qismi yo'q. Ya'ni bu o'lchov
ilova **ortiqcha blur qilmasligini** ko'rsatadi, lekin **haqiqiy kontentni
ushlashini KO'RSATMAYDI.**

> Bu farq muhim: hech narsani aniqlamaydigan model ham 0 % FPR beradi.
> Recall o'lchash uchun `nsfw/` qismi kerak va uni loyiha egasi o'zi
> yig'adi (`docs/GOLDEN-SET.md` §2).

---

## 2. Natijalar

| Sezgirlik | FPR | Mo'ljal (TZ 6.3) | Yolg'on ijobiy |
|---|---|---|---|
| LOW | **0.008** | ≤ 0.03 | 2 / 237 |
| MEDIUM | **0.030** | ≤ 0.03 | 7 / 237 |
| STRICT | **0.055** | ≤ 0.03 | 13 / 237 |

### Kategoriya bo'yicha (MEDIUM)

| Kategoriya | n | Xato | |
|---|---|---|---|
| bola | 24 | **0** | ✅ eng muhim holat |
| oshxona | 24 | **0** | ✅ "go'sht teriga o'xshaydi" qo'rquvi tasdiqlanmadi |
| hijob | 20 | **0** | ✅ |
| kundalik (UI, matn, xarita) | 24 | **0** | ✅ darvoza ishlaydi |
| plyaj | 24 | **0** | ✅ |
| portret | 24 | **0** | ✅ |
| sport | 24 | **0** | ✅ |
| sanat | 24 | 1 | |
| tibbiyot | 24 | 1 | |
| **erkak_torso** | 24 | **5** | ⚠️ 3-bo'limga qarang |

`bola` kategoriyasida uchala sezgirlikda ham **nol** xato — bu TZ 6.3 dagi
eng muhim xavfsizlik talabi.

---

## 3. `erkak_torso` — bu xatomi yoki kutilgan xatti-harakat?

MEDIUM dagi 7 ta yolg'on ijobiyning 5 tasi va STRICT dagi 13 tasidan 9 tasi
shu kategoriyadan: yalang'och ko'krakli erkaklar (bodibilder, suzuvchi,
bokschi). Sabab — `MALE_BREAST_EXPOSED` klassi.

**Bu belgilash qarori, aniq haqiqat emas.** Men bu kategoriyani `safe/` ga
qo'ydim, ya'ni "blur qilinmasligi kerak". Loyiha auditoriyasi uchun aksi
ham to'g'ri bo'lishi mumkin — bu diniy va shaxsiy masala, texnik savol emas.

Shuning uchun u **alohida sozlamaga** chiqarildi:
`Sozlamalar → Erkak ko'kragi yopilsinmi` (`AppSettings.blurMaleChest`).

> **Erkak avrati bunga bog'liq emas.** `MALE_GENITALIA_EXPOSED` klassi
> `EXPLICIT` ro'yxatida va har qanday sozlamada, har qanday sezgirlikda
> blur qilinadi. Sozlama faqat yalang'och ko'krakka tegishli.

### Ikki xil hisob — ular boshqa savolga javob beradi

**1. Sozlamani o'chirish** (o'lchangan):

| Sezgirlik | Yoniq | O'chiq |
|---|---|---|
| LOW | 0.008 | 0.008 |
| MEDIUM | 0.030 | **0.025** |
| STRICT | 0.055 | 0.051 |

Farq kichik, chunki `erkak_torso` dagi ko'p rasm boshqa klasslarni ham
qo'zg'atadi — model yalang'och erkak ko'kragini ba'zan
`FEMALE_BREAST_EXPOSED` deb ham belgilaydi.

**2. Kategoriyani `nsfw/` deb qayta belgilash** (ya'ni "ular blur
qilinishi KERAK" deb hisoblash):

| Sezgirlik | FPR |
|---|---|
| LOW | 0.005 |
| MEDIUM | **0.009** |
| STRICT | 0.019 |

> **Tuzatish.** Bu hujjatning oldingi versiyasida 0.009 raqami sozlamani
> o'chirish natijasi sifatida ko'rsatilgan edi. Bu noto'g'ri: u
> kategoriyani hisobdan chiqarish natijasi. Sozlamani o'chirish MEDIUM
> FPR ni 0.030 dan 0.025 ga tushiradi, 0.009 ga emas.

---

## 4. Klasslarni qayta taqsimlash

Birinchi o'lchov mo'ljaldan chiqdi (MEDIUM 0.042, STRICT 0.122). Sabablar
aniqlandi va `NudeNetLabels.kt` tuzatildi:

| Klass | Oldin | Keyin | Sabab |
|---|---|---|---|
| `FEMALE_BREAST_COVERED` | STRICT | **hech qachon** | STRICT da 20 ta yolg'on ijobiy, jumladan hijobli ayollar. **TZ 8.4 aynan shu funksiyani etik sabablarga ko'ra olib tashlagan edi** — u STRICT orqali orqa eshikdan qaytib kirgan |
| `FEET_EXPOSED` | STRICT | **hech qachon** | 9 ta yolg'on ijobiy; oyoqni blur qilish uchun asos yo'q |
| `ARMPITS_EXPOSED` | STRICT | **hech qachon** | 7 ta yolg'on ijobiy |
| `BELLY_COVERED` | STRICT | **hech qachon** | kiyim ostidagi qorin |
| `BELLY_EXPOSED` | MEDIUM | STRICT | MEDIUM dagi yolg'on ijobiylarning eng katta manbai (10 dan 9). Ochiq qorin yalang'ochlik emas |

Natija:

| Sezgirlik | Oldin | Keyin |
|---|---|---|
| LOW | 0.008 | 0.008 |
| MEDIUM | 0.042 ❌ | **0.030** ✅ |
| STRICT | 0.122 ❌ | 0.055 |

`hijob` 2 → **0**, `portret` 4 → **0**, `plyaj` 3 → **0**.

---

## 5. Stage A darvozasi

Darvoza 237 tadan 6–9 tasini to'xtatdi (sezgirlikka qarab). Hammasi
`safe/` bo'lgani uchun bu zarar keltirmadi.

**Lekin bu ham o'lchanmagan xavf.** Agar darvoza NSFW rasmni to'xtatsa,
bu tuzatib bo'lmaydigan xato — Stage B umuman ishga tushmaydi. `nsfw/`
qismi qo'shilganda `evaluate.py` buni alohida ogohlantiradi.

---

## 6. Takrorlash

```bash
python3 -m pip install --user onnxruntime pillow numpy

# safe/ qismini qayta yig'ish (Commons dan)
python3 tools/collect_safe_set.py goldenset/ --per-category 24

# baholash
python3 tools/evaluate.py goldenset/ --all
python3 tools/evaluate.py goldenset/ --sensitivity MEDIUM --detail
```

Rasmlar `goldenset/` da qoladi va repozitoriyaga tushmaydi
(`.gitignore`). Manba URL va litsenziyalar `goldenset/manifest.json` da.

---

## 7. Keyingi qadam

1. `nsfw/` qismini yig'ish → recall o'lchash → **shundagina** aniqlik
   haqida to'liq gapirish mumkin bo'ladi.
2. `--sweep` bilan threshold'larni qayta sozlash.
3. `erkak_torso` bo'yicha mahsulot qarorini qabul qilish (3-bo'lim).
