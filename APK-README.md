# KUPO POS — APK para Android (sin Play Store)

Este workflow arma el APK de KUPO POS con Capacitor a partir de la PWA que ya
vive en este repo, y lo publica en el release `apk-latest` para instalarlo
directo en el celular de un cliente. La PWA de GitHub Pages no se toca.

## Cómo se arma

- La app Android arranca en la **versión móvil** (`movil.html`), copiada como
  entrada `www/index.html`. La versión de PC queda dentro de la app como
  `escritorio.html` (enlace "Ver versión PC").
- App ID: `com.grupobenitez.kupopos` · Nombre: `KUPO POS`.
- El icono sale del icono PNG incrustado en `manifest.json` (generado en CI a
  1024x1024, sin subir binarios al repo).
- Cada push a `main` que toque la web (`index.html`, `movil.html`,
  `admin.html`, `manifest*.json`, `sw.js`) o los archivos de Capacitor, y cada
  disparo manual, genera un APK nuevo.

## Descargar e instalar (para el cliente)

1. Abrir en el celular:
   `https://github.com/josealcocer070-gif/Kupo-Pos/releases/download/apk-latest/kupo-pos.apk`
2. Al descargar, Android pide permitir **"Instalar apps desconocidas"** para
   Chrome (o el navegador que se use). Permitirlo solo para esa instalación.
3. Abrir el archivo `kupo-pos.apk` descargado y tocar **Instalar**.
4. Abrir la app **KUPO POS**. Funciona sin internet tras la primera carga;
   los datos (licencia, productos, ventas) se guardan en el celular.

## Actualizaciones: aviso importante

- El APK que se publica sin secretos de firma es un **APK debug**: Android lo
  firma en CI con una llave temporal que cambia en cada build. Por eso, para
  actualizar a una versión nueva hay que **desinstalar la anterior y volver a
  instalar** (al desinstalar se borran los datos guardados en ese celular:
  antes hay que hacer corte/sincronizar lo vendido).
- Para que las actualizaciones se instalen encima sin borrar datos, hay que
  firmarlo siempre con la **misma llave de producción**, una sola vez:

## Firma de producción (una sola vez, la hace Lalo)

1. En una computadora con Java, generar el keystore (guardar bien la
   contraseña y el archivo; si se pierde no se puede volver a firmar igual):
   `keytool -genkeypair -v -keystore kupo-release.keystore -alias kupo -keyalg RSA -keysize 2048 -validity 10000`
2. Convertirlo a base64: `base64 -w0 kupo-release.keystore`
3. En GitHub, repo **Kupo-Pos → Settings → Secrets and variables → Actions →
   New repository secret**, crear estos 4 secretos:
   - `ANDROID_KEYSTORE_BASE64`: el base64 del paso 2
   - `KEYSTORE_PASSWORD`: la contraseña del keystore
   - `KEY_ALIAS`: `kupo`
   - `KEY_PASSWORD`: la contraseña de la llave
4. Volver a correr el workflow (Actions → Build APK → Run workflow). A partir
   de ahí el APK es release y firmado siempre con la misma llave: las
   actualizaciones se instalan encima sin desinstalar ni borrar datos.
5. **Nunca** subir el archivo `.keystore` al repo ni compartirlo por chat.

## Archivos de este APK

- `capacitor.config.json`, `package.json`: configuración de Capacitor.
- `scripts/build-www.sh`: copia la web a `www/` (entrada = `movil.html`).
- `scripts/make-icon.py`: genera el icono 1024x1024 desde `manifest.json`.
- `scripts/patch-signing.py`: activa la firma release solo si hay secretos.
- `.github/workflows/build-apk.yml`: build en GitHub Actions.
