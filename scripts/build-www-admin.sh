#!/usr/bin/env bash
# Arma www/ para el APK KUPO Admin (Lalo 2026-11-25): la app arranca directo
# en el Centro de administración (admin.html), sin pasar por el POS.
# No modifica index.html, movil.html ni admin.html del repo.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "$ROOT/www"
mkdir -p "$ROOT/www"

# Entrada de la app: el Centro de administración, copiado como index.html.
cp "$ROOT/admin.html" "$ROOT/www/index.html"
cp "$ROOT/admin.html" "$ROOT/www/admin.html"
cp "$ROOT/movil.html" "$ROOT/www/movil.html"
# La versión de PC queda disponible dentro de la app como escritorio.html.
cp "$ROOT/index.html" "$ROOT/www/escritorio.html"
cp "$ROOT/manifest.json" "$ROOT/www/manifest.json"
cp "$ROOT/manifest-movil.json" "$ROOT/www/manifest-movil.json"
cp "$ROOT/sw.js" "$ROOT/www/sw.js"
cp "$ROOT/publicidad.html" "$ROOT/www/publicidad.html"

# "Abrir KUPO POS" debe abrir la copia de escritorio, no volver a la Admin.
python3 - "$ROOT/www/index.html" <<'PY'
import sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
s = s.replace('href="index.html"', 'href="escritorio.html"', 1)
open(p, 'w', encoding='utf-8').write(s)
PY

echo "www/ listo: $(ls "$ROOT/www")"
