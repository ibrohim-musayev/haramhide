#!/usr/bin/env python3
"""
Golden set ning `safe/` qismini Wikimedia Commons dan yig'adi.

--------------------------------------------------------------------------
NIMA UCHUN FAQAT `safe/`
--------------------------------------------------------------------------

Golden set ikki qismdan iborat va ular butunlay boshqacha:

  safe/  — neytral, LEKIN qiyin rasmlar. Ochiq litsenziyali, huquqiy
           muammosiz. Bu skript aynan shuni yig'adi.

  nsfw/  — haqiqiy kontent. Bu skript uni yig'MAYDI va yig'masligi kerak.
           Uni loyiha egasi o'z qarori va javobgarligi bilan, TZ 8.6 dagi
           cheklovlarga rioya qilgan holda yig'adi.

`safe/` qismi kamroq ahamiyatli emas — aksincha. Foydalanuvchini ilovadan
bezdiradigan narsa o'tkazib yuborish emas, YOLG'ON BLUR: non, chaqaloq,
sportchi yoki hijobli ayol blur bo'lsa odam ilovani o'chiradi. Bu qism
precision va FPR ni o'lchaydi — va aynan ular threshold bilan tuzatiladi.

--------------------------------------------------------------------------
LITSENZIYA
--------------------------------------------------------------------------

Har bir rasm uchun manba URL va litsenziya `manifest.json` ga yoziladi.
Faqat quyidagi litsenziyalar qabul qilinadi:

  Public domain, CC0, CC BY, CC BY-SA, "No restrictions"

CC BY-NC, CC BY-ND va noma'lum litsenziyali rasmlar o'tkazib yuboriladi.

Yig'ilgan rasmlar `.gitignore` dagi `/goldenset/` ostida qoladi va
repozitoriyaga tushmaydi.

--------------------------------------------------------------------------
FOYDALANISH
--------------------------------------------------------------------------

    python3 tools/collect_safe_set.py goldenset/
    python3 tools/collect_safe_set.py goldenset/ --per-category 30
    python3 tools/collect_safe_set.py goldenset/ --only sport,oshxona
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://commons.wikimedia.org/w/api.php"
UA = "HaramHide-goldenset/0.1 (https://github.com/ibrohim-musayev/haramhide)"

ACCEPTED_LICENSE_HINTS = (
    "public domain", "cc0", "cc by", "cc-by", "no restrictions",
    "attribution", "pd-", "gfdl",
)
REJECTED_LICENSE_HINTS = ("-nc", "noncommercial", "-nd", "noderiv", "fair use")

# Har bir kategoriya — TZ 8.5 va docs/GOLDEN-SET.md dagi "qiyin holatlar".
# Qidiruv so'zlari ataylab oddiy va aniq: modelning eng ko'p yanglishadigan
# joylari, lekin hech biri nomaqbul emas.
CATEGORIES: dict[str, list[str]] = {
    "sport": [
        "gymnastics competition athlete",
        "wrestling match sport",
        "athletics running competition",
        "swimming competition pool athlete",
    ],
    "tibbiyot": [
        "anatomical drawing human body",
        "medical illustration anatomy",
        "anatomy museum model",
    ],
    "sanat": [
        "classical marble sculpture museum",
        "renaissance painting museum",
        "bronze statue monument",
    ],
    "plyaj": [
        "beach summer people sea",
        "swimming pool people summer",
        "seaside holiday coast",
    ],
    "bola": [
        "children playing school",
        "kindergarten children class",
        "family children park",
    ],
    "erkak_torso": [
        "bodybuilder posing competition",
        "male swimmer athlete portrait",
        "boxer training gym",
    ],
    "hijob": [
        "woman hijab portrait",
        "traditional dress woman abaya",
        "muslim women headscarf",
    ],
    "oshxona": [
        "raw meat butcher shop",
        "bread bakery loaf",
        "cooked chicken dish plate",
        "pizza food dish",
    ],
    "reklama": [
        "underwear advertisement vintage",
        "swimsuit fashion catalogue vintage",
    ],
    "portret": [
        "studio portrait person face",
        "passport photo portrait man",
        "formal portrait woman",
    ],
    "kundalik": [
        "screenshot software interface",
        "city street map",
        "document text page scan",
        "landscape mountains nature",
    ],
}


def api_get(params: dict) -> dict:
    params = {**params, "format": "json", "formatversion": "2"}
    url = f"{API}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode())


def license_ok(name: str) -> bool:
    low = name.lower()
    if any(h in low for h in REJECTED_LICENSE_HINTS):
        return False
    return any(h in low for h in ACCEPTED_LICENSE_HINTS)


def search(term: str, limit: int) -> list[dict]:
    try:
        data = api_get({
            "action": "query",
            "generator": "search",
            "gsrsearch": f"filetype:bitmap {term}",
            "gsrnamespace": "6",
            "gsrlimit": str(limit),
            "prop": "imageinfo",
            "iiprop": "url|size|extmetadata|mime",
            "iiurlwidth": "1024",
        })
    except Exception as e:                                       # noqa: BLE001
        print(f"    so'rov xatosi: {e}", file=sys.stderr)
        return []

    out = []
    for page in data.get("query", {}).get("pages", []):
        ii = (page.get("imageinfo") or [{}])[0]
        meta = ii.get("extmetadata", {})
        lic = meta.get("LicenseShortName", {}).get("value", "")
        mime = ii.get("mime", "")
        url = ii.get("thumburl") or ii.get("url")
        if not url or not mime.startswith("image/"):
            continue
        if mime in ("image/svg+xml", "image/tiff"):
            continue
        if not license_ok(lic):
            continue
        out.append({
            "title": page.get("title", ""),
            "url": url,
            "descriptionurl": ii.get("descriptionurl", ""),
            "license": lic,
            "artist": meta.get("Artist", {}).get("value", "")[:200],
        })
    return out


def download(url: str, dest: Path) -> bool:
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=60) as r:
            data = r.read()
        if len(data) < 8000:            # juda kichik = ikonka yoki xato
            return False
        dest.write_bytes(data)
        return True
    except Exception as e:                                       # noqa: BLE001
        print(f"    yuklab bo'lmadi: {e}", file=sys.stderr)
        return False


def safe_name(title: str, idx: int) -> str:
    base = title.replace("File:", "").rsplit(".", 1)[0]
    base = "".join(c if c.isalnum() or c in "-_" else "_" for c in base)
    return f"{idx:03d}_{base[:60]}.jpg"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("dataset", type=Path)
    ap.add_argument("--per-category", type=int, default=24)
    ap.add_argument("--only", type=str, help="vergul bilan ajratilgan kategoriyalar")
    args = ap.parse_args()

    cats = CATEGORIES
    if args.only:
        want = {c.strip() for c in args.only.split(",")}
        cats = {k: v for k, v in CATEGORIES.items() if k in want}
        if not cats:
            print(f"Noma'lum kategoriya. Mavjud: {', '.join(CATEGORIES)}")
            return 1

    root = args.dataset / "safe"
    root.mkdir(parents=True, exist_ok=True)
    manifest_path = args.dataset / "manifest.json"
    manifest = json.loads(manifest_path.read_text()) if manifest_path.is_file() else {}

    total = 0
    for cat, terms in cats.items():
        out_dir = root / cat
        out_dir.mkdir(parents=True, exist_ok=True)
        existing = len(list(out_dir.glob("*.jpg")))
        need = max(0, args.per_category - existing)
        print(f"\n{cat}  (bor: {existing}, kerak: {need})")
        if need == 0:
            continue

        seen = {m["title"] for m in manifest.get(cat, [])}
        got = 0
        per_term = max(4, need // max(1, len(terms)) + 3)
        for term in terms:
            if got >= need:
                break
            print(f"  qidiruv: {term}")
            for item in search(term, per_term):
                if got >= need:
                    break
                if item["title"] in seen:
                    continue
                seen.add(item["title"])
                dest = out_dir / safe_name(item["title"], existing + got + 1)
                if download(item["url"], dest):
                    manifest.setdefault(cat, []).append(item)
                    got += 1
                    total += 1
                    print(f"    + {dest.name}  [{item['license']}]")
                time.sleep(0.25)          # Commons'ga hurmat

    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False))
    print(f"\nJami yuklandi: {total}")
    print(f"Manifest: {manifest_path}")
    print("\nDIQQAT: bu faqat `safe/` qismi. `nsfw/` ni loyiha egasi")
    print("o'zi yig'adi — docs/GOLDEN-SET.md §2 ga qarang.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
