#!/usr/bin/env python3
"""Genera assets/icon.png (1024x1024) para @capacitor/assets.

Fuente: el icono más grande incrustado en manifest.json del repo (data URI
PNG). No se sube ningún binario al repo: este script corre en CI y genera el
icono ahí. Requiere Pillow (pip install pillow).
"""
import base64
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main() -> None:
    manifest = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))
    icons = manifest.get("icons", [])
    best = None
    best_size = 0
    for icon in icons:
        src = icon.get("src", "")
        if not src.startswith("data:image/png;base64,"):
            continue
        try:
            size = int(str(icon.get("sizes", "0x0")).split("x")[0])
        except ValueError:
            size = 0
        if size > best_size:
            best_size = size
            best = src
    if best is None:
        print("No se encontró un icono PNG en manifest.json", file=sys.stderr)
        sys.exit(1)

    raw = base64.b64decode(best.split(",", 1)[1])
    from PIL import Image

    import io

    img = Image.open(io.BytesIO(raw)).convert("RGB")
    img = img.resize((1024, 1024), Image.LANCZOS)
    out = ROOT / "assets" / "icon.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    print(f"Icono generado: {out} (1024x1024, desde {best_size}x{best_size})")


if __name__ == "__main__":
    main()
