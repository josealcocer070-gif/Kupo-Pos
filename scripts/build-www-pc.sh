#!/usr/bin/env bash
# Arma www/ para el APK versión PC (tablet): la app arranca directo en
# la versión de PC (index.html), sin pasar por la versión móvil.
# No modifica index.html, movil.html ni admin.html del repo.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "$ROOT/www"
mkdir -p "$ROOT/www"

# Entrada de la app: la versión PC, copiada como index.html.
cp "$ROOT/index.html" "$ROOT/www/index.html"
cp "$ROOT/index.html" "$ROOT/www/escritorio.html"
cp "$ROOT/movil.html" "$ROOT/www/movil.html"
cp "$ROOT/admin.html" "$ROOT/www/admin.html"
cp "$ROOT/manifest.json" "$ROOT/www/manifest.json"
cp "$ROOT/manifest-movil.json" "$ROOT/www/manifest-movil.json"
cp "$ROOT/sw.js" "$ROOT/www/sw.js"
cp "$ROOT/publicidad.html" "$ROOT/www/publicidad.html"
# index.html y movil.html cargan Supabase con <script src="supabase.min.js"> (relativo): sin este archivo en www/ la app depende del respaldo por CDN para crear el cliente (v214). En CI el archivo está en la raíz del checkout.
cp "$ROOT/supabase.min.js" "$ROOT/www/supabase.min.js"

echo "www/ listo: $(ls "$ROOT/www")"
