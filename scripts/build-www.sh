#!/usr/bin/env bash
# Arma www/ (la carpeta que Capacitor empaqueta en el APK) a partir de los
# archivos web del repo. La app Android arranca en la versión móvil
# (movil.html), que es la que usan los clientes en el celular.
# No modifica index.html, movil.html ni admin.html del repo.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "$ROOT/www"
mkdir -p "$ROOT/www"

# Entrada de la app: la versión móvil, copiada como index.html.
cp "$ROOT/movil.html" "$ROOT/www/index.html"
cp "$ROOT/movil.html" "$ROOT/www/movil.html"
cp "$ROOT/admin.html" "$ROOT/www/admin.html"
# La versión de PC queda disponible dentro de la app como escritorio.html.
cp "$ROOT/index.html" "$ROOT/www/escritorio.html"
cp "$ROOT/manifest.json" "$ROOT/www/manifest.json"
cp "$ROOT/manifest-movil.json" "$ROOT/www/manifest-movil.json"
cp "$ROOT/sw.js" "$ROOT/www/sw.js"

# "Ver versión PC" debe abrir la copia de escritorio, no volver a la entrada móvil.
python3 - "$ROOT/www/index.html" <<'PY'
import sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
s = s.replace('href="index.html"', 'href="escritorio.html"', 1)
open(p, 'w', encoding='utf-8').write(s)
PY

echo "www/ listo: $(ls "$ROOT/www")"
