#!/usr/bin/env python3
"""
Modelni INT8 ga o'tkazish (statik kvantizatsiya).

--------------------------------------------------------------------------
NEGA STATIK, DINAMIK EMAS
--------------------------------------------------------------------------

`quantize_dynamic` konvolyutsiyalarni `ConvInteger` operatoriga o'tkazadi va
ONNX Runtime CPU uni qo'llab-quvvatlamaydi:

    NOT_IMPLEMENTED: Could not find an implementation for ConvInteger(10)

Statik kvantizatsiya QDQ formatida (QuantizeLinear / DequantizeLinear +
oddiy Conv) ishlaydi va u ORT CPU da to'liq qo'llab-quvvatlanadi.

--------------------------------------------------------------------------
KALIBRATSIYA MA'LUMOTI
--------------------------------------------------------------------------

Statik kvantizatsiya aktivatsiyalar diapazonini bilish uchun tipik kirish
namunalarini talab qiladi. **Belgilash (label) kerak emas** — faqat kirish
taqsimoti muhim.

Shuning uchun `goldenset/safe/` ishlatiladi va u yetarli: kalibratsiya
uchun kerak bo'lgan narsa — modelning haqiqiy foydalanishda ko'radigan
piksel taqsimoti, va ekran nusxalari aynan shu.

Kirish `evaluate.py` bilan bir xil yo'ldan o'tadi (ekran taqlidi +
letterbox), aks holda kalibratsiya noto'g'ri diapazon beradi.

--------------------------------------------------------------------------
CHIQISH BOSHINI CHIQARIB TASHLASH — MAJBURIY
--------------------------------------------------------------------------

YOLOv8 ning chiqish boshi (`/model.22/*`) bbox dekodlash arifmetikasini
bajaradi: Slice, Sub, Div, Mul, Concat. Uni kvantlash natijani BUTUNLAY
buzadi — model hamma rasmda aynan 0.000 ball qaytaradi.

Bu jimgina xato: metrikalar "FPR 0.000" ko'rsatadi va bu muvaffaqiyatga
o'xshaydi. Aslida hech narsani aniqlamaydigan model ham 0 % FPR beradi.

Shuning uchun bu skript chiqish boshini avtomatik chiqarib tashlaydi VA
kvantizatsiyadan keyin natijani tekshiradi.

--------------------------------------------------------------------------
FOYDALANISH
--------------------------------------------------------------------------

    python3 tools/quantize.py <model.onnx> <chiqish.onnx> goldenset/
"""

from __future__ import annotations

import argparse
import random
import sys
from pathlib import Path

import numpy as np

try:
    from onnxruntime.quantization import CalibrationDataReader, QuantFormat, QuantType, quantize_static
    from onnxruntime.quantization.shape_inference import quant_pre_process
except ImportError:
    sys.exit("python3 -m pip install --user onnxruntime")

sys.path.insert(0, str(Path(__file__).resolve().parent))
from evaluate import IMAGE_EXT, preprocess, simulate_screen                # noqa: E402
from PIL import Image                                                     # noqa: E402


class ScreenCalibrationReader(CalibrationDataReader):
    """`evaluate.py` bilan bir xil preprocessing — bu majburiy."""

    def __init__(self, images: list[Path], input_name: str):
        self.input_name = input_name
        self.images = images
        self.i = 0

    def get_next(self):
        while self.i < len(self.images):
            path = self.images[self.i]
            self.i += 1
            try:
                img = simulate_screen(Image.open(path))
                tensor, _, _ = preprocess(img)
                return {self.input_name: tensor}
            except Exception:                                    # noqa: BLE001
                continue
        return None


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("model", type=Path)
    ap.add_argument("output", type=Path)
    ap.add_argument("dataset", type=Path)
    ap.add_argument("--samples", type=int, default=100)
    ap.add_argument("--exclude-prefix", default="/model.22/",
                    help="kvantlanmaydigan node prefiksi (YOLOv8 chiqish boshi)")
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    images = [p for p in (args.dataset / "safe").rglob("*")
              if p.is_file() and p.suffix.lower() in IMAGE_EXT]
    if not images:
        print(f"Kalibratsiya rasmlari topilmadi: {args.dataset}/safe")
        return 1

    random.Random(args.seed).shuffle(images)
    images = images[:args.samples]
    print(f"Kalibratsiya: {len(images)} rasm")

    import onnxruntime as ort
    input_name = ort.InferenceSession(
        str(args.model), providers=["CPUExecutionProvider"]
    ).get_inputs()[0].name

    prepared = args.output.with_suffix(".prep.onnx")
    print("Shakl xulosasi (pre-process)...")
    quant_pre_process(str(args.model), str(prepared), skip_symbolic_shape=True)

    import onnx
    head = [n.name for n in onnx.load(str(prepared)).graph.node
            if n.name.startswith(args.exclude_prefix)]
    print(f"Chiqish boshi chiqarib tashlanadi: {len(head)} node "
          f"('{args.exclude_prefix}')")
    if not head:
        print("  DIQQAT: chiqish boshi topilmadi. Model buzilishi mumkin.")

    print("Kvantizatsiya (QDQ, INT8)...")
    quantize_static(
        model_input=str(prepared),
        model_output=str(args.output),
        calibration_data_reader=ScreenCalibrationReader(images, input_name),
        quant_format=QuantFormat.QDQ,
        activation_type=QuantType.QUInt8,
        weight_type=QuantType.QInt8,
        per_channel=False,
        nodes_to_exclude=head,
    )
    prepared.unlink(missing_ok=True)

    a = args.model.stat().st_size / 1e6
    b = args.output.stat().st_size / 1e6
    print(f"\n{args.model.name}: {a:.1f} MB -> {args.output.name}: {b:.1f} MB ({b / a:.0%})")

    ok = validate(args.model, args.output, images[:8], input_name)
    if not ok:
        print("\nKVANTIZATSIYA MUVAFFAQIYATSIZ — model ishlatilmasin.")
        return 1

    print("\nEndi aniqlikni tekshiring — TZ 8.5: rasmiy metrikalar")
    print("kvantizatsiyadan KEYIN hisoblanadi:")
    print(f"    python3 tools/evaluate.py {args.dataset} --all --model {args.output}")
    print("\nDIQQAT: FPR o'z-o'zidan yetarli emas. INT8 ballarni pasaytiradi,")
    print("ya'ni RECALL tushadi — buni `nsfw/` to'plamisiz o'lchab bo'lmaydi.")
    return 0


def validate(fp32: Path, int8: Path, probes: list[Path], input_name: str) -> bool:
    """
    Kvantlangan model tirikmi tekshiradi.

    Eng xavfli xato — o'lik model: u hamma rasmda 0.000 qaytaradi va
    metrikalarda bu "FPR 0.000" bo'lib, muvaffaqiyatga o'xshab ko'rinadi.
    """
    import onnxruntime as ort
    from PIL import Image

    a = ort.InferenceSession(str(fp32), providers=["CPUExecutionProvider"])
    b = ort.InferenceSession(str(int8), providers=["CPUExecutionProvider"])

    pa, pb = [], []
    for p in probes:
        try:
            tensor, _, _ = preprocess(simulate_screen(Image.open(p)))
        except Exception:                                        # noqa: BLE001
            continue
        pa.append(float(a.run(None, {input_name: tensor})[0][0][4:, :].max()))
        pb.append(float(b.run(None, {input_name: tensor})[0][0][4:, :].max()))

    if not pa:
        print("\nTEKSHIRUV: probe rasmlari o'qilmadi.")
        return False

    print("\nTEKSHIRUV — eng yuqori klass bali:")
    print("  fp32:  " + " ".join(f"{v:.3f}" for v in pa))
    print("  int8:  " + " ".join(f"{v:.3f}" for v in pb))

    if max(pb) < 1e-6:
        print("  XATO: model hamma rasmda 0.000 qaytaradi — u O'LIK.")
        return False

    ratio = sum(pb) / sum(pa) if sum(pa) > 0 else 0
    print(f"  int8/fp32 nisbati: {ratio:.2f}")
    if ratio < 0.85:
        print(f"  OGOHLANTIRISH: ballar {1 - ratio:.0%} ga tushdi.")
        print("  Bu recall pasayishini anglatadi va uni `nsfw/` to'plamisiz")
        print("  o'lchab bo'lmaydi. Threshold'ni qayta kalibrlash SHART.")
    return True


if __name__ == "__main__":
    sys.exit(main())
