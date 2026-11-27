#!/usr/bin/env bash
# Arma www/ para el APK Kiosco KUPO (Lalo 2026-11-26): la app arranca directo
# en el Kiosco (kiosco.html), sin pasar por el POS.
# No modifica index.html, movil.html, admin.html ni kiosco.html del repo.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "$ROOT/www"
mkdir -p "$ROOT/www"

# Entrada de la app: el Kiosco, copiado como index.html.
cp "$ROOT/kiosco.html" "$ROOT/www/index.html"
cp "$ROOT/kiosco.html" "$ROOT/www/kiosco.html"
cp "$ROOT/movil.html" "$ROOT/www/movil.html"
# La versión de PC queda disponible dentro de la app como escritorio.html.
cp "$ROOT/index.html" "$ROOT/www/escritorio.html"
cp "$ROOT/manifest.json" "$ROOT/www/manifest.json"
cp "$ROOT/manifest-movil.json" "$ROOT/www/manifest-movil.json"
cp "$ROOT/sw.js" "$ROOT/www/sw.js"
cp "$ROOT/publicidad.html" "$ROOT/www/publicidad.html"
# kiosco.html carga Supabase con <script src="supabase.min.js"> (relativo, sin
# respaldo por CDN): sin este archivo en www/ el Kiosco no leería productos
# ni mandaría pedidos. En CI el archivo está en la raíz del checkout.
cp "$ROOT/supabase.min.js" "$ROOT/www/supabase.min.js"
# Iconos sólidos+flat del Kiosco (v176, Lalo 2026-11-26): 9 PNG en iconos/,
# referenciados como iconos/icono-*.png desde kiosco.html; van dentro del
# APK para que el menú se vea bien aunque el internet falle.
cp -r "$ROOT/iconos" "$ROOT/www/iconos"

# kiosco.html no tiene enlaces href="index.html", así que no lleva el parche
# que el script de Admin aplica a admin.html (no hay nada que redirigir).

echo "www/ listo: $(ls "$ROOT/www")"
