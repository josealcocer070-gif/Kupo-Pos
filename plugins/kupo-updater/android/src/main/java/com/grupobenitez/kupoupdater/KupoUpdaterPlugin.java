package com.grupobenitez.kupoupdater;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * KupoUpdater — actualizador dentro de KUPO POS (v198, Lalo 2026-11-25).
 *
 * Como Snaptube: la app revisa version.json, avisa en pantalla y al tocar
 * "Actualizar ahora" este plugin descarga el APK y abre el instalador de
 * Android para instalar encima (misma firma, no se borra nada).
 *
 * La primera vez Android pide permitir "instalar apps desconocidas" para
 * KUPO POS: si el permiso falta, descargarEInstalar NO descarga nada y
 * devuelve necesitaPermiso=true; la app muestra el botón "Abrir ajustes"
 * (abrirAjustesInstalacion) y el usuario lo activa una sola vez. Después
 * cada actualización solo muestra la confirmación "Instalar" del sistema.
 *
 * El APK baja por HTTPS desde el release oficial de GitHub (las URLs de
 * github.com redirigen; HttpURLConnection las sigue). Se guarda en la
 * caché interna (kupo-updater/) y se sirve al instalador con FileProvider
 * y permiso temporal. SIN verificación de hash en v198 (el hash del APK
 * no existe antes del build): mejora futura, anotada en version.json.
 */
@CapacitorPlugin(name = "KupoUpdater")
public class KupoUpdaterPlugin extends Plugin {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile long progresoDescargado = 0;
    private volatile long progresoTotal = -1;
    private volatile String progresoEstado = "inactivo";

    private boolean puedeInstalarApps() {
        if (Build.VERSION.SDK_INT < 26) return true;
        return getContext().getPackageManager().canRequestPackageInstalls();
    }

    @PluginMethod
    public void puedeInstalar(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("puede", puedeInstalarApps());
        call.resolve(ret);
    }

    @PluginMethod
    public void abrirAjustesInstalacion(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            i.setData(Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("no se pudo abrir los ajustes");
        }
    }

    @PluginMethod
    public void obtenerProgreso(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("descargado", progresoDescargado);
        ret.put("total", progresoTotal);
        ret.put("porcentaje", progresoTotal > 0 ? (int) (progresoDescargado * 100 / progresoTotal) : 0);
        ret.put("estado", progresoEstado);
        call.resolve(ret);
    }

    @PluginMethod
    public void descargarEInstalar(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("falta la dirección del APK");
            return;
        }
        if (!puedeInstalarApps()) {
            JSObject ret = new JSObject();
            ret.put("ok", false);
            ret.put("necesitaPermiso", true);
            call.resolve(ret);
            return;
        }
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                progresoEstado = "descargando";
                progresoDescargado = 0;
                progresoTotal = -1;
                URL u = new URL(url);
                conn = (HttpURLConnection) u.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "KUPO-POS-Updater");
                conn.connect();
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    call.reject("la descarga falló (HTTP " + code + ")");
                    return;
                }
                progresoTotal = conn.getContentLengthLong();
                File dir = new File(getContext().getCacheDir(), "kupo-updater");
                if (!dir.exists()) dir.mkdirs();
                File apk = new File(dir, "kupo-pos-update.apk");
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] buf = new byte[8192];
                    int n;
                    long ultimoAviso = 0;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        progresoDescargado += n;
                        long ahora = System.currentTimeMillis();
                        if (ahora - ultimoAviso > 250) {
                            ultimoAviso = ahora;
                            emitirProgreso();
                        }
                    }
                }
                emitirProgreso();
                progresoEstado = "instalando";
                Uri uri = FileProvider.getUriForFile(
                        getContext(),
                        getContext().getPackageName() + ".fileprovider",
                        apk);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                progresoEstado = "listo";
                JSObject ret = new JSObject();
                ret.put("ok", true);
                ret.put("ruta", apk.getAbsolutePath());
                call.resolve(ret);
            } catch (Exception e) {
                progresoEstado = "error";
                String detalle = e.getMessage() != null ? e.getMessage() : "";
                call.reject("no se pudo descargar el APK" + (detalle.isEmpty() ? "" : " (" + detalle + ")"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void emitirProgreso() {
        JSObject ev = new JSObject();
        ev.put("descargado", progresoDescargado);
        ev.put("total", progresoTotal);
        ev.put("porcentaje", progresoTotal > 0 ? (int) (progresoDescargado * 100 / progresoTotal) : 0);
        ev.put("estado", progresoEstado);
        notifyListeners("progreso", ev);
    }
}
