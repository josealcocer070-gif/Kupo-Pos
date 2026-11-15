#!/usr/bin/env python3
"""Genera assets/icon.png (1024x1024) para @capacitor/assets.

Fuente (en orden): el icono más grande incrustado en manifest.json (data URI
PNG); si no hay, el archivo icon-512.png del repo (el icono oficial de KUPO
POS, subido como archivo). No se sube ningún binario al repo: este script
corre en CI y genera el icono ahí. Requiere Pillow (pip install pillow).
"""
import base64
import io
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

    from PIL import Image

    if best is not None:
        raw = base64.b64decode(best.split(",", 1)[1])
        img = Image.open(io.BytesIO(raw)).convert("RGBA")
        origen = f"data URI {best_size}x{best_size}"
    else:
        png = ROOT / "icon-512.png"
        if not png.exists():
            print(
                "No se encontró un icono PNG en manifest.json ni icon-512.png",
                file=sys.stderr,
            )
            sys.exit(1)
        img = Image.open(png).convert("RGBA")
        origen = "icon-512.png"

    img = img.resize((1024, 1024), Image.LANCZOS)
    out = ROOT / "assets" / "icon.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    print(f"Icono generado: {out} (1024x1024, desde {origen})")


if __name__ == "__main__":
    main()
