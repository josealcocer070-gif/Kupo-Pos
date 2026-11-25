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

echo "www/ listo: $(ls "$ROOT/www")"
