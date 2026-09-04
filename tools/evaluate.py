#!/usr/bin/env python3
"""
Golden set bo'yicha modelni baholash va threshold kalibratsiyasi (TZ 8.5).

Bu skript **Android kodidagi bilan aynan bir xil** preprocessing va
postprocessing qiladi. Aks holda bu yerda olingan raqamlar telefonda
takrorlanmaydi va kalibratsiya ma'nosiz bo'ladi.

Manba haqiqati:
  core-detect/src/main/kotlin/com/haramhide/core/detect/NudeNetDetector.kt
  core-detect/src/main/kotlin/com/haramhide/core/detect/NudeNetLabels.kt
  core-detect/src/main/kotlin/com/haramhide/core/detect/SkinPrescreen.kt

Ulardan birortasi o'zgarsa, bu fayl ham o'zgarishi SHART.

--------------------------------------------------------------------------
DATASET TUZILISHI
--------------------------------------------------------------------------

    goldenset/
      nsfw/                 <- blur QO'YILISHI kerak
        <kategoriya>/*.jpg
      safe/                 <- blur QO'YILMASLIGI kerak
        <kategoriya>/*.jpg

Kategoriya papkalari ixtiyoriy, lekin ular bo'lsa hisobotda alohida
ko'rsatiladi. TZ 8.5 tavsiya qiladigan qiyin kategoriyalar:

    safe/sport, safe/tibbiyot, safe/sanat, safe/plyaj, safe/bola,
    safe/erkak_torso, safe/hijob, safe/oshxona, safe/chomilish_reklama

--------------------------------------------------------------------------
HUQUQIY VA ETIK CHEKLOVLAR (TZ 8.6)
--------------------------------------------------------------------------

  * Faqat qonuniy, kattalar uchun, ochiq manbalar.
  * Voyaga yetmaganlar tasviri bo'lishi mumkin bo'lgan har qanday manba —
    MUTLAQ TAQIQ.
  * Golden set repozitoriyaga, CI'ga yoki bulutga HECH QACHON yuklanmaydi.
    `.gitignore` da `/goldenset/` bor.
  * Bu skript rasmlarni ko'chirmaydi, o'zgartirmaydi va hech qayerga
    yubormaydi — faqat o'qiydi.

--------------------------------------------------------------------------
FOYDALANISH
--------------------------------------------------------------------------

    python3 tools/evaluate.py goldenset/
    python3 tools/evaluate.py goldenset/ --sweep
    python3 tools/evaluate.py goldenset/ --sensitivity STRICT
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np

try:
    import onnxruntime as ort
    from PIL import Image
except ImportError:
    sys.exit(
        "Kerakli paketlar yo'q. O'rnating:\n"
        "    python3 -m pip install --user onnxruntime pillow numpy"
    )

# ---------------------------------------------------------------- konstantalar
# NudeNetLabels.kt bilan bir xil tartib — model chiqishidagi kanal indeksi.
LABELS = [
    "FEMALE_GENITALIA_COVERED",  # 0
    "FACE_FEMALE",               # 1
    "BUTTOCKS_EXPOSED",          # 2
    "FEMALE_BREAST_EXPOSED",     # 3
    "FEMALE_GENITALIA_EXPOSED",  # 4
    "MALE_BREAST_EXPOSED",       # 5
    "ANUS_EXPOSED",              # 6
    "FEET_EXPOSED",              # 7
    "BELLY_COVERED",             # 8
    "FEET_COVERED",              # 9
    "ARMPITS_COVERED",           # 10
    "ARMPITS_EXPOSED",           # 11
    "FACE_MALE",                 # 12
    "BELLY_EXPOSED",             # 13
    "MALE_GENITALIA_EXPOSED",    # 14
    "ANUS_COVERED",              # 15
    "FEMALE_BREAST_COVERED",     # 16
    "BUTTOCKS_COVERED",          # 17
]

# NudeNetLabels.kt bilan sinxron. Golden set o'lchovidan keyin qayta taqsimlangan.
NEVER = {1, 12, 9, 10, 7, 8, 11, 16}
EXPLICIT = {2, 3, 4, 6, 14}
PARTIAL = {5}
SUGGESTIVE = {0, 13, 15, 17}

BLUR_SETS = {
    "LOW": EXPLICIT,
    "MEDIUM": EXPLICIT | PARTIAL,
    "STRICT": EXPLICIT | PARTIAL | SUGGESTIVE,
}

# Sensitivity.kt bilan bir xil
THRESHOLDS = {
    "LOW": (0.75, 0.60),
    "MEDIUM": (0.50, 0.45),
    "STRICT": (0.30, 0.30),
}

MIN_SCORE = 0.20   # NudeNetDetector.MIN_SCORE
NMS_IOU = 0.45     # NudeNetDetector.NMS_IOU

# DetectorConfig.TIER_B
INPUT_W, INPUT_H = 256, 512

# Ekran yo'lini taqlid qilish uchun (CaptureConfig.TIER_B)
SCREEN_W, SCREEN_H = 1080, 2400      # tipik telefon ekrani
CAPTURE_MAX_DIM = 960                # CaptureConfig.TIER_B.maxCaptureDimension

MODEL_PATH = Path(__file__).resolve().parent.parent / \
    "core-detect/src/main/assets/nudenet_320n.onnx"

IMAGE_EXT = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def is_blurred(class_id: int, sensitivity: str) -> bool:
    """NudeNetLabels.isBlurred bilan bir xil."""
    if class_id in NEVER:
        return False
    return class_id in BLUR_SETS[sensitivity]


# ------------------------------------------------------------------- Stage A

def skin_peak_ratio(rgb: np.ndarray, grid: int = 8) -> tuple[float, bool]:
    """
    SkinPrescreen.kt bilan bir xil: blok bo'yicha eng yuqori teri ulushi va
    kadr kulrangmi (fail-open holati).
    """
    h, w, _ = rgb.shape
    r = rgb[:, :, 0].astype(np.int16)
    g = rgb[:, :, 1].astype(np.int16)
    b = rgb[:, :, 2].astype(np.int16)

    mx = np.max(rgb, axis=2).astype(np.int16)
    mn = np.min(rgb, axis=2).astype(np.int16)
    mean_chroma = float(np.mean(mx - mn))
    if mean_chroma < 8.0:                      # MIN_MEAN_CHROMA
        return 0.0, True                       # fail-open

    skin = (r > 95) & (g > 40) & (b > 20) & ((mx - mn) > 15) & \
           (np.abs(r - g) > 15) & (r > g) & (r > b)

    peak = 0.0
    for gy in range(grid):
        y0, y1 = gy * h // grid, max((gy + 1) * h // grid, gy * h // grid + 1)
        for gx in range(grid):
            x0, x1 = gx * w // grid, max((gx + 1) * w // grid, gx * w // grid + 1)
            block = skin[y0:y1, x0:x1]
            if block.size:
                peak = max(peak, float(block.mean()))
    return peak, False


def stage_a_score(rgb: np.ndarray, saturation_ratio: float = 0.20) -> float:
    peak, low_sat = skin_peak_ratio(rgb)
    if low_sat:
        return 1.0
    return min(1.0, peak / saturation_ratio)


# ------------------------------------------------------------------- Stage B

def simulate_screen(img: Image.Image) -> Image.Image:
    """
    Ilova rasmni EMAS, ekran nusxasini ko'radi. Bu farq katta:

        foto 960x869
          -> ekranda ko'rsatiladi (1080x2400 ga sig'diriladi, markazda)
          -> VirtualDisplay 432x960 ga kichraytiradi (CaptureConfig.TIER_B)
          -> model kirishi 256x512

    Ya'ni modelga yetib boradigan aniqlik manba fayldagidan bir necha
    barobar past. Threshold'ni manba fayl ustida kalibrlash telefonda
    noto'g'ri natija beradi.

    Shuning uchun default holda shu yo'l taqlid qilinadi. Manba fayl ustida
    baholash uchun `--raw`.
    """
    img = img.convert("RGB")
    fw, fh = img.size

    # 1. Ekranda ko'rsatish — nisbatni saqlab, markazga
    scale = min(SCREEN_W / fw, SCREEN_H / fh)
    dw, dh = max(1, int(fw * scale)), max(1, int(fh * scale))
    screen = Image.new("RGB", (SCREEN_W, SCREEN_H), (0, 0, 0))
    screen.paste(img.resize((dw, dh), Image.BILINEAR),
                 ((SCREEN_W - dw) // 2, (SCREEN_H - dh) // 2))

    # 2. VirtualDisplay kichraytirishi (ScreenCapturer.fitInto)
    longer = max(SCREEN_W, SCREEN_H)
    if longer > CAPTURE_MAX_DIM:
        cs = CAPTURE_MAX_DIM / longer
        cw = max(2, int(SCREEN_W * cs) & ~1)
        ch = max(2, int(SCREEN_H * cs) & ~1)
        screen = screen.resize((cw, ch), Image.BILINEAR)
    return screen


def preprocess(img: Image.Image) -> tuple[np.ndarray, float, float]:
    """
    NudeNetDetector.detect bilan bir xil letterbox:
    nisbatni saqlab, CHAP YUQORIGA joylashtirish, qolgani qora.
    """
    img = img.convert("RGB")
    fw, fh = img.size
    scale = min(INPUT_W / fw, INPUT_H / fh)
    cw, ch = max(1, int(fw * scale)), max(1, int(fh * scale))

    canvas = Image.new("RGB", (INPUT_W, INPUT_H), (0, 0, 0))
    canvas.paste(img.resize((cw, ch), Image.BILINEAR), (0, 0))

    arr = np.asarray(canvas, dtype=np.float32) / 255.0      # HWC
    chw = np.transpose(arr, (2, 0, 1))[None, ...]           # NCHW
    return np.ascontiguousarray(chw), float(cw), float(ch)


def iou(a, b) -> float:
    il, it = max(a[0], b[0]), max(a[1], b[1])
    ir, ib = min(a[2], b[2]), min(a[3], b[3])
    if ir <= il or ib <= it:
        return 0.0
    inter = (ir - il) * (ib - it)
    aa = (a[2] - a[0]) * (a[3] - a[1])
    bb = (b[2] - b[0]) * (b[3] - b[1])
    return inter / (aa + bb - inter)


def postprocess(raw: np.ndarray, content_w: float, content_h: float,
                min_conf: float, sensitivity: str) -> list[dict]:
    """NudeNetDetector.postprocess bilan bir xil."""
    out = raw[0]                       # [22, N]
    n = out.shape[1]
    boxes = out[:4, :]
    scores = out[4:, :]                # [18, N]

    best_class = np.argmax(scores, axis=0)
    best_score = scores[best_class, np.arange(n)]

    threshold = max(min_conf, MIN_SCORE)
    keep = best_score >= threshold
    if not keep.any():
        return []

    idx = np.nonzero(keep)[0]
    candidates = []
    for i in idx:
        c = int(best_class[i])
        if not is_blurred(c, sensitivity):
            continue
        cx, cy, w, h = boxes[:, i]
        l = float(np.clip((cx - w / 2) / content_w, 0, 1))
        t = float(np.clip((cy - h / 2) / content_h, 0, 1))
        r = float(np.clip((cx + w / 2) / content_w, 0, 1))
        b = float(np.clip((cy + h / 2) / content_h, 0, 1))
        if r <= l or b <= t:
            continue
        candidates.append({"box": (l, t, r, b), "score": float(best_score[i]),
                           "label": LABELS[c], "class_id": c})

    candidates.sort(key=lambda d: -d["score"])
    kept: list[dict] = []
    for cand in candidates:
        if all(iou(cand["box"], k["box"]) <= NMS_IOU for k in kept):
            kept.append(cand)
    return kept


# --------------------------------------------------------------------- baholash

@dataclass
class Result:
    path: Path
    expected_blur: bool
    category: str
    stage_a: float
    passed_gate: bool
    detections: list[dict]

    @property
    def predicted_blur(self) -> bool:
        return self.passed_gate and len(self.detections) > 0


def collect(root: Path) -> list[tuple[Path, bool, str]]:
    items = []
    for label, expected in (("nsfw", True), ("safe", False)):
        base = root / label
        if not base.is_dir():
            continue
        for p in sorted(base.rglob("*")):
            if p.is_file() and p.suffix.lower() in IMAGE_EXT:
                rel = p.relative_to(base).parent
                items.append((p, expected, str(rel) if str(rel) != "." else "(kategoriyasiz)"))
    return items


def evaluate(session, items, sensitivity: str, t_low=None, t_det=None,
             screen: bool = True) -> list[Result]:
    low, det = THRESHOLDS[sensitivity]
    t_low = low if t_low is None else t_low
    t_det = det if t_det is None else t_det
    input_name = session.get_inputs()[0].name

    results = []
    for path, expected, category in items:
        try:
            img = Image.open(path)
        except Exception as e:                                  # noqa: BLE001
            print(f"  o'qib bo'lmadi: {path.name}: {e}", file=sys.stderr)
            continue

        if screen:
            img = simulate_screen(img)

        rgb_small = np.asarray(img.convert("RGB").resize((256, 144), Image.BILINEAR))
        a = stage_a_score(rgb_small)
        if a < t_low:
            results.append(Result(path, expected, category, a, False, []))
            continue

        tensor, cw, ch = preprocess(img)
        raw = session.run(None, {input_name: tensor})[0]
        dets = postprocess(raw, cw, ch, t_det, sensitivity)
        results.append(Result(path, expected, category, a, True, dets))
    return results


def metrics(results: list[Result]) -> dict:
    tp = sum(1 for r in results if r.expected_blur and r.predicted_blur)
    fn = sum(1 for r in results if r.expected_blur and not r.predicted_blur)
    fp = sum(1 for r in results if not r.expected_blur and r.predicted_blur)
    tn = sum(1 for r in results if not r.expected_blur and not r.predicted_blur)
    recall = tp / (tp + fn) if tp + fn else float("nan")
    precision = tp / (tp + fp) if tp + fp else float("nan")
    fpr = fp / (fp + tn) if fp + tn else float("nan")
    return {"tp": tp, "fn": fn, "fp": fp, "tn": tn,
            "recall": recall, "precision": precision, "fpr": fpr,
            "n": len(results)}


def fmt(v) -> str:
    return "  —  " if v != v else f"{v:.3f}"


def report(results: list[Result], sensitivity: str) -> dict:
    m = metrics(results)
    print(f"\n{'=' * 62}")
    print(f"  {sensitivity}  —  {m['n']} ta rasm")
    print("=" * 62)
    print(f"  recall     {fmt(m['recall'])}   (TZ 6.3 mo'ljali >= 0.90)")
    print(f"  precision  {fmt(m['precision'])}   (mo'ljal >= 0.85)")
    print(f"  FPR        {fmt(m['fpr'])}   (mo'ljal <= 0.03)")
    print(f"  TP {m['tp']}   FN {m['fn']}   FP {m['fp']}   TN {m['tn']}")

    cats: dict[str, list[Result]] = {}
    for r in results:
        cats.setdefault(f"{'nsfw' if r.expected_blur else 'safe'}/{r.category}", []).append(r)
    if len(cats) > 1:
        print(f"\n  {'kategoriya':32} {'n':>4} {'xato':>6} {'ulush':>7}")
        print(f"  {'-' * 52}")
        for name in sorted(cats):
            group = cats[name]
            wrong = sum(1 for r in group if r.expected_blur != r.predicted_blur)
            print(f"  {name:32} {len(group):>4} {wrong:>6} {wrong / len(group):>7.1%}")

    gated = sum(1 for r in results if not r.passed_gate)
    print(f"\n  Stage A darvozadan o'tmadi: {gated}/{m['n']}")
    missed = [r for r in results if r.expected_blur and not r.passed_gate]
    if missed:
        print(f"  DIQQAT: {len(missed)} ta NSFW rasm DARVOZADA to'xtatildi —")
        print("  bu tuzatib bo'lmaydigan xato. SkinPrescreen ni yumshating.")
        for r in missed[:5]:
            print(f"    {r.path.name}  stage_a={r.stage_a:.2f}")
    return m


def sweep(session, items, sensitivity: str, screen: bool = True) -> None:
    print(f"\n{'=' * 62}")
    print(f"  THRESHOLD SWEEP — {sensitivity}")
    print("=" * 62)
    print(f"  {'t_low':>6} {'t_det':>6} {'recall':>8} {'precision':>10} {'FPR':>8}")
    print(f"  {'-' * 42}")
    best = None
    for t_low in (0.20, 0.30, 0.50, 0.75):
        for t_det in (0.20, 0.30, 0.45, 0.60):
            m = metrics(evaluate(session, items, sensitivity, t_low, t_det,
                                 screen=screen))
            print(f"  {t_low:>6.2f} {t_det:>6.2f} {fmt(m['recall']):>8} "
                  f"{fmt(m['precision']):>10} {fmt(m['fpr']):>8}")
            if m["recall"] == m["recall"] and m["precision"] == m["precision"]:
                score = m["recall"] * 2 + m["precision"]
                if best is None or score > best[0]:
                    best = (score, t_low, t_det, m)
    if best:
        _, t_low, t_det, m = best
        print(f"\n  Eng yaxshi (recall'ga ikki barobar og'irlik):")
        print(f"    t_low={t_low}  t_det={t_det}  "
              f"recall={fmt(m['recall'])}  precision={fmt(m['precision'])}")
        print(f"\n  Bu qiymatlarni Sensitivity.kt ga ko'chiring.")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("dataset", type=Path, help="golden set papkasi")
    ap.add_argument("--sensitivity", default="MEDIUM", choices=list(THRESHOLDS))
    ap.add_argument("--sweep", action="store_true", help="threshold sweep")
    ap.add_argument("--raw", action="store_true",
                    help="ekran yo'lini taqlid qilmasdan, manba fayl ustida "
                         "baholash (model darajasidagi kalibratsiya)")
    ap.add_argument("--all", action="store_true", help="uchala sezgirlik")
    ap.add_argument("--detail", action="store_true",
                    help="har bir rasm uchun aniqlangan klasslarni ko'rsatish")
    ap.add_argument("--json", type=Path, help="natijani JSON ga yozish")
    ap.add_argument("--model", type=Path, default=MODEL_PATH)
    args = ap.parse_args()

    if not args.model.is_file():
        return print(f"Model topilmadi: {args.model}") or 1
    if not args.dataset.is_dir():
        return print(f"Dataset topilmadi: {args.dataset}") or 1

    items = collect(args.dataset)
    if not items:
        print(f"'{args.dataset}' ichida rasm topilmadi.")
        print("Kutilgan tuzilish:  <dataset>/nsfw/...  va  <dataset>/safe/...")
        return 1

    n_nsfw = sum(1 for _, e, _ in items if e)
    print(f"Dataset: {len(items)} rasm  ({n_nsfw} nsfw, {len(items) - n_nsfw} safe)")
    print(f"Model:   {args.model.name}  ({INPUT_W}x{INPUT_H}, Tier B)")
    rejim = "manba fayl (--raw)" if args.raw else "ekran yo'li taqlid qilinadi"
    print(f"Rejim:   {rejim}")

    session = ort.InferenceSession(str(args.model), providers=["CPUExecutionProvider"])

    out = {}
    for sens in (list(THRESHOLDS) if args.all else [args.sensitivity]):
        results = evaluate(session, items, sens, screen=not args.raw)
        out[sens] = report(results, sens)
        if args.detail:
            print(f"\n  {'rasm':32} {'stageA':>7} {'darvoza':>8}  klasslar")
            print(f"  {'-' * 70}")
            for r in results:
                labels = ", ".join(f"{d['label']}={d['score']:.2f}"
                                   for d in r.detections) or "-"
                mark = "!" if r.expected_blur != r.predicted_blur else " "
                print(f" {mark}{r.path.name[:32]:32} {r.stage_a:>7.2f} "
                      f"{str(r.passed_gate):>8}  {labels}")
    if args.sweep:
        sweep(session, items, args.sensitivity, screen=not args.raw)

    if args.json:
        args.json.write_text(json.dumps(out, indent=2))
        print(f"\nJSON yozildi: {args.json}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
