#!/usr/bin/env python3
"""Parchea android/app/build.gradle (generado por `npx cap add android`) para
firmar el APK release con un keystore guardado en android/keystore.properties.

Solo se ejecuta en CI cuando existen los secretos de firma; sin ellos no se
corre y el build sigue siendo debug. Si el formato de build.gradle no es el
esperado, avisa y no rompe nada (el build release simplemente no se firmaría).
"""
import re
import sys
from pathlib import Path

GRADLE = Path("android/app/build.gradle")


def main() -> None:
    if not GRADLE.exists():
        print("No existe android/app/build.gradle; no se parchea firma.")
        return
    s = GRADLE.read_text(encoding="utf-8")
    if "signingConfigs.release" in s:
        print("build.gradle ya tiene signingConfigs.release; no se duplica.")
        return

    signing = """
    signingConfigs {
        release {
            def props = new Properties()
            def propsFile = rootProject.file('keystore.properties')
            if (propsFile.exists()) {
                props.load(new FileInputStream(propsFile))
                storeFile file(props['storeFile'])
                storePassword props['storePassword']
                keyAlias props['keyAlias']
                keyPassword props['keyPassword']
            }
        }
    }
"""
    # Insertar el bloque antes de buildTypes {
    if "buildTypes {" not in s:
        print("No se encontró 'buildTypes {' en build.gradle; no se parchea.")
        return
    s = s.replace("    buildTypes {", signing + "    buildTypes {", 1)

    # Usar la config de firma en el buildType release.
    m = re.search(
        r"(buildTypes \{\s*release \{\s*)",
        s,
    )
    if not m:
        print("No se encontró el bloque release; no se parchea.")
        return
    s = s[: m.end(1)] + "signingConfig signingConfigs.release\n            " + s[m.end(1):]
    GRADLE.write_text(s, encoding="utf-8")
    print("build.gradle parcheado con firma release (keystore.properties).")


if __name__ == "__main__":
    main()
