#!/usr/bin/env python3
"""
Ochiq teri ulushini o'lchash (MediaPipe Selfie Multiclass Segmentation).

Model odamni 6 sinfga ajratadi: background, hair, body-skin, face-skin,
clothes, others. YUZ ALOHIDA sinf — ya'ni u ochiqlik deb hisoblanmaydi.

O'lchov:  body-skin / (body-skin + clothes)

Interfeys elementlari (tugmalar, matn) ba'zan xato tasniflanadi, shuning
uchun eng katta bog'langan komponent — ya'ni odamning o'zi — ajratib olinadi.

Model: Apache-2.0, MediaPipe. 16 MB, 256x256.
"""
from __future__ import annotations
import sys, argparse
from collections import deque
from pathlib import Path
import numpy as np

LABELS = ["background", "hair", "body-skin", "face-skin", "clothes", "others"]
BODY_SKIN, CLOTHES = 2, 4


def load(model_path: str):
    import tensorflow as tf
    it = tf.lite.Interpreter(model_path=model_path)
    it.allocate_tensors()
    return it, it.get_input_details()[0], it.get_output_details()[0]


def segment(it, inp, out, img):
    from PIL import Image
    S = inp["shape"][1]
    im = img.convert("RGB")
    w, h = im.size
    sc = S / max(w, h)
    nw, nh = max(1, int(w * sc)), max(1, int(h * sc))
    canvas = Image.new("RGB", (S, S), (0, 0, 0))
    canvas.paste(im.resize((nw, nh), Image.BILINEAR), ((S - nw) // 2, (S - nh) // 2))
    it.set_tensor(inp["index"], (np.asarray(canvas, np.float32) / 255.0)[None])
    it.invoke()
    return it.get_tensor(out["index"])[0].argmax(-1)


def largest_component(mask: np.ndarray) -> np.ndarray:
    seen = np.zeros_like(mask, bool)
    best, bestn = None, 0
    H, W = mask.shape
    for sy in range(H):
        for sx in range(W):
            if not mask[sy, sx] or seen[sy, sx]:
                continue
            q = deque([(sy, sx)]); seen[sy, sx] = True; comp = []
            while q:
                y, x = q.popleft(); comp.append((y, x))
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < H and 0 <= nx < W and mask[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True; q.append((ny, nx))
            if len(comp) > bestn:
                bestn, best = len(comp), comp
    m = np.zeros_like(mask, bool)
    for y, x in (best or []):
        m[y, x] = True
    return m


def skin_ratio(it, inp, out, img, min_person_px: int = 400):
    cls = segment(it, inp, out, img)
    comp = largest_component(cls != 0)
    if comp.sum() < min_person_px:
        return None, 0
    skin = int(((cls == BODY_SKIN) & comp).sum())
    cloth = int(((cls == CLOTHES) & comp).sum())
    if skin + cloth == 0:
        return None, int(comp.sum())
    return skin / (skin + cloth), int(comp.sum())


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("paths", nargs="+", type=Path)
    ap.add_argument("--model", required=True)
    ap.add_argument("--limit", type=int, default=12)
    args = ap.parse_args()

    from PIL import Image
    it, inp, out = load(args.model)
    exts = {".jpg", ".jpeg", ".png", ".webp"}

    for p in args.paths:
        files = ([p] if p.is_file()
                 else sorted(x for x in p.rglob("*") if x.suffix.lower() in exts)[:args.limit])
        ratios = []
        for f in files:
            try:
                r, px = skin_ratio(it, inp, out, Image.open(f))
            except Exception:
                continue
            if r is not None:
                ratios.append(r)
        if ratios:
            a = np.array(ratios)
            print(f"  {str(p)[-28:]:30} n={len(a):3}  "
                  f"medi={np.median(a)*100:5.1f}%  o'rt={a.mean()*100:5.1f}%  "
                  f"min={a.min()*100:5.1f}%  max={a.max()*100:5.1f}%")
        else:
            print(f"  {str(p)[-28:]:30} odam topilmadi")
    return 0


if __name__ == "__main__":
    sys.exit(main())
