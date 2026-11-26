package com.grupobenitez.kupoupdater;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * KupoUpdater — actualizador dentro de KUPO POS (v198, Lalo 2026-11-25).
 *
 * Como Snaptube: la app revisa version.json, avisa en pantalla y al tocar
 * "Actualizar ahora" este plugin descarga el APK y lo instala encima (misma
 * firma, no se borra nada).
 *
 * La primera vez Android pide permitir "instalar apps desconocidas" para
 * KUPO POS: si el permiso falta, descargarEInstalar NO descarga nada y
 * devuelve necesitaPermiso=true; la app muestra el botón "Abrir ajustes"
 * (abrirAjustesInstalacion) y el usuario lo activa una sola vez.
 *
 * V1.20.15: si la descarga se corta, se reanuda donde se quedó (header
 * Range: el parcial queda en la caché interna) y el tiempo de lectura sube
 * a 120 s para que el internet lento de Palizada no la corte a la mitad.
 *
 * V1.20.25 / V1.19.10 / V1.19.7 (Lalo 2026-11-26: "el actualizador debe
 * funcionar bien por los clientes"): dos fallos reales obligaron el cambio.
 * (1) La instalación usaba un intent IMPLÍCITO (ACTION_VIEW + FileProvider)
 * y Android se lo entregaba a cualquier app que manejara APKs: en el POCO
 * C71 de Lalo lo agarraba "APK Installer by Uptodown" y daba "Error 114".
 * Ahora se instala con la API de sesiones del PackageInstaller (crear
 * sesión, escribir el APK, commit con PendingIntent de estado): solo el
 * instalador del sistema toca el paquete, ningún instalador de terceros.
 * (2) La descarga podía llegar truncada por internet inestable y el plugin
 * la instalaba sin verificarla ("Se produjo un error durante el análisis
 * del paquete", 2026-11-26). Ahora ANTES de instalar se verifica el archivo:
 * tamaño igual al esperado (Content-Length) e integridad del ZIP (cabecera
 * PK y registro End of Central Directory sano). Si falla, se borra y se
 * vuelve a descargar solo, hasta 3 intentos; si sigue fallando, se rechaza
 * con un mensaje claro en vez de lanzar el instalador.
 * El APK baja por HTTPS desde el release oficial de GitHub (las URLs de
 * github.com redirigen; HttpURLConnection las sigue). Se guarda en la
 * caché interna (kupo-updater/kupo-pos-update.apk).
 */
@CapacitorPlugin(name = "KupoUpdater")
public class KupoUpdaterPlugin extends Plugin {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile long progresoDescargado = 0;
    private volatile long progresoTotal = -1;
    private volatile String progresoEstado = "inactivo";

    /* Llamada y receptor de la instalación en curso (sesión PackageInstaller).
       La llamada se resuelve o se rechaza cuando llega el estado final de la
       sesión; mientras tanto queda pendiente. */
    private volatile PluginCall llamadaInstalacion = null;
    private volatile BroadcastReceiver receptorInstalacion = null;

    /* PackageInstaller.EXTRA_INTENT es una API oculta (@hide): no está en
       el SDK público y el compilador no la encuentra (ese fue el único
       error del build). El valor real es "android.content.pm.extra.INTENT":
       el Intent que se abre cuando la instalación pide la confirmación
       del usuario. */
    private static final String EXTRA_INTENT_SESION = "android.content.pm.extra.INTENT";

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
        if (llamadaInstalacion != null) {
            call.reject("ya hay una actualización en curso");
            return;
        }
        executor.execute(() -> {
            try {
                progresoEstado = "descargando";
                progresoDescargado = 0;
                progresoTotal = -1;
                File dir = new File(getContext().getCacheDir(), "kupo-updater");
                if (!dir.exists()) dir.mkdirs();
                File apk = new File(dir, "kupo-pos-update.apk");
                /* Hasta 3 intentos: si la descarga se corta por la red, el
                   parcial queda y el siguiente intento la reanuda (Range);
                   si el archivo baja pero sale corrupto, se borra y el
                   siguiente intento baja de cero. */
                boolean verificado = false;
                for (int intento = 1; intento <= 3 && !verificado; intento++) {
                    try {
                        descargarApk(url, apk);
                    } catch (Exception e) {
                        if (intento == 3) throw e;
                        continue;
                    }
                    if (verificarApk(apk, progresoTotal)) {
                        verificado = true;
                    } else {
                        apk.delete();
                        progresoDescargado = 0;
                        progresoTotal = -1;
                    }
                }
                if (!verificado) {
                    progresoEstado = "error";
                    call.reject("La descarga llegó incompleta, inténtalo de nuevo con mejor señal");
                    return;
                }
                progresoEstado = "instalando";
                emitirProgreso();
                /* La llamada se resuelve o se rechaza en el receptor de
                   estado de la sesión: éxito = la app se reemplaza;
                   fallo o cancelación = mensaje claro y el botón
                   "Actualizar ahora" queda habilitado para reintentar. */
                instalarPorSesion(apk, call);
            } catch (Exception e) {
                progresoEstado = "error";
                String detalle = e.getMessage() != null ? e.getMessage() : "";
                call.reject("no se pudo descargar el APK" + (detalle.isEmpty() ? "" : " (" + detalle + ")"));
            }
        });
    }

    /* Descarga el APK a `apk` con reanudación (V1.20.15, sin cambios):
       header Range si hay un parcial, 206 sigue donde se quedó, 200
       empieza de cero, 416 borra el parcial y empieza de cero. Lanza
       IOException si la red o el servidor fallan; deja progresoTotal con
       el tamaño total esperado (-1 si el servidor no lo dice). */
    private void descargarApk(String url, File apk) throws Exception {
        HttpURLConnection conn = null;
        try {
            long yaDescargado = apk.exists() ? apk.length() : 0;
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.setRequestProperty("User-Agent", "KUPO-POS-Updater");
            if (yaDescargado > 0) conn.setRequestProperty("Range", "bytes=" + yaDescargado + "-");
            conn.connect();
            int code = conn.getResponseCode();
            boolean reanudar = false;
            if (code == 206 && yaDescargado > 0) {
                reanudar = true;
            } else if (code == 200) {
                if (yaDescargado > 0) apk.delete();
                yaDescargado = 0;
            } else if (code == 416) {
                conn.disconnect();
                apk.delete();
                yaDescargado = 0;
                conn = (HttpURLConnection) u.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(120000);
                conn.setRequestProperty("User-Agent", "KUPO-POS-Updater");
                conn.connect();
                code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new IOException("la descarga falló (HTTP " + code + ")");
                }
            } else if (code < 200 || code >= 300) {
                throw new IOException("la descarga falló (HTTP " + code + ")");
            }
            long restante = conn.getContentLengthLong();
            if (restante < 0) {
                progresoTotal = -1;
            } else {
                progresoTotal = reanudar ? yaDescargado + restante : restante;
            }
            progresoDescargado = reanudar ? yaDescargado : 0;
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(apk, reanudar)) {
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
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /* Verifica el APK descargado ANTES de instalarlo. Un APK es un ZIP:
       (a) su tamaño debe ser igual al total esperado cuando el servidor
       lo dijo (esperado > 0); (b) debe empezar con la cabecera local
       PK\003\004; (c) su registro End of Central Directory (firma
       PK\005\006) debe estar sano: posición + 22 + comentario == tamaño
       del archivo y directorio central (offset + tamaño) termina justo
       donde empieza el EOCD. Una descarga truncada pierde el EOCD o
       queda con menos bytes: eso es justo lo que causaba "error durante
       el análisis del paquete". Si algo no cuadra, false (el llamador
       borra el archivo y lo vuelve a descargar). */
    private boolean verificarApk(File apk, long esperado) {
        try {
            long tam = apk.length();
            if (tam < 22) return false;
            if (esperado > 0 && tam != esperado) return false;
            try (RandomAccessFile raf = new RandomAccessFile(apk, "r")) {
                byte[] ini = new byte[4];
                raf.readFully(ini);
                if (ini[0] != 0x50 || ini[1] != 0x4B || ini[2] != 0x03 || ini[3] != 0x04) return false;
                long cola = Math.min(tam, 65557L);
                byte[] buf = new byte[(int) cola];
                raf.seek(tam - cola);
                raf.readFully(buf);
                for (int i = buf.length - 22; i >= 0; i--) {
                    if (buf[i] == 0x50 && buf[i + 1] == 0x4B && buf[i + 2] == 0x05 && buf[i + 3] == 0x06) {
                        long posAbs = tam - cola + i;
                        int comentario = (buf[i + 20] & 0xFF) | ((buf[i + 21] & 0xFF) << 8);
                        if (posAbs + 22 + comentario != tam) continue;
                        long cdTam = leerU32(buf, i + 12);
                        long cdIni = leerU32(buf, i + 16);
                        if (cdIni + cdTam != posAbs) continue;
                        return true;
                    }
                }
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private long leerU32(byte[] b, int off) {
        return (b[off] & 0xFFL)
                | ((b[off + 1] & 0xFFL) << 8)
                | ((b[off + 2] & 0xFFL) << 16)
                | ((b[off + 3] & 0xFFL) << 24);
    }

    /* Instala el APK verificado con la API de sesiones del PackageInstaller
       (API 21+, minSdk del proyecto 23): solo el instalador del sistema
       toca el paquete, ningún instalador de terceros puede interceptarlo.
       La llamada queda pendiente hasta el estado final de la sesión:
       el usuario confirma en la ventana del sistema (STATUS_PENDING_USER_ACTION)
       y si confirma, la app se reemplaza; si cancela o falla, se rechaza
       la llamada con un mensaje claro. El receptor se registra dinámico
       (no exportado: nadie más puede mandar estados falsos) y se quita
       solo al llegar el estado final. */
    private void instalarPorSesion(File apk, PluginCall call) {
        String accion = getContext().getPackageName() + ".KUPO_INSTALACION";
        BroadcastReceiver receptor = new BroadcastReceiver() {
            @Override
            @SuppressWarnings("deprecation")
            public void onReceive(Context ctx, Intent intent) {
                int estado = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                if (estado == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirmar = intent.getParcelableExtra(EXTRA_INTENT_SESION);
                    if (confirmar != null) {
                        confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            ctx.startActivity(confirmar);
                        } catch (Exception e) {
                            terminarInstalacion(false, "no se pudo abrir la confirmación de instalación");
                        }
                    }
                    return;
                }
                if (estado == PackageInstaller.STATUS_SUCCESS) {
                    terminarInstalacion(true, null);
                } else {
                    String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    terminarInstalacion(false, mensajeFalloInstalacion(estado, msg));
                }
            }
        };
        receptorInstalacion = receptor;
        llamadaInstalacion = call;
        try {
            IntentFilter filtro = new IntentFilter(accion);
            if (Build.VERSION.SDK_INT >= 33) {
                getContext().registerReceiver(receptor, filtro, Context.RECEIVER_NOT_EXPORTED);
            } else {
                getContext().registerReceiver(receptor, filtro);
            }
            PackageInstaller installer = getContext().getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setSize(apk.length());
            int sessionId = installer.createSession(params);
            int banderas = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) banderas |= PendingIntent.FLAG_MUTABLE;
            Intent intentEstado = new Intent(accion);
            PendingIntent pi = PendingIntent.getBroadcast(getContext(), sessionId, intentEstado, banderas);
            IntentSender remitente = pi.getIntentSender();
            PackageInstaller.Session sesion = installer.openSession(sessionId);
            try {
                try (InputStream in = new FileInputStream(apk);
                     OutputStream out = sesion.openWrite("kupo-pos-update.apk", 0, apk.length())) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    sesion.fsync(out);
                }
                sesion.commit(remitente);
            } finally {
                sesion.close();
            }
        } catch (Exception e) {
            String detalle = e.getMessage() != null ? e.getMessage() : "";
            terminarInstalacion(false, "no se pudo iniciar la instalación" + (detalle.isEmpty() ? "" : " (" + detalle + ")"));
        }
    }

    /* Resuelve o rechaza la llamada pendiente UNA sola vez y quita el
       receptor. En éxito la app se reemplaza y el proceso muere, así que
       el resolve es solo por si el proceso sobrevive. */
    private void terminarInstalacion(boolean ok, String error) {
        BroadcastReceiver receptor = receptorInstalacion;
        receptorInstalacion = null;
        if (receptor != null) {
            try {
                getContext().unregisterReceiver(receptor);
            } catch (Exception ignored) {
            }
        }
        PluginCall call = llamadaInstalacion;
        llamadaInstalacion = null;
        if (call == null) return;
        if (ok) {
            progresoEstado = "listo";
            JSObject ret = new JSObject();
            ret.put("ok", true);
            call.resolve(ret);
        } else {
            progresoEstado = "error";
            call.reject(error != null ? error : "La instalación no se completó. Vuelve a tocar 'Actualizar ahora' para intentarlo de nuevo.");
        }
    }

    private String mensajeFalloInstalacion(int estado, String msg) {
        switch (estado) {
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                return "No hay espacio suficiente en el celular para instalar la actualización. Libera espacio y vuelve a intentarlo.";
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return "La versión nueva no es compatible con este celular.";
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                return "La versión nueva no se puede instalar sobre la actual. Avísanos para revisarlo.";
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                return "Android bloqueó la instalación. Revisa el permiso de 'instalar apps desconocidas' para KUPO POS.";
            case PackageInstaller.STATUS_FAILURE_INVALID:
                return "El archivo de actualización no es válido. Vuelve a tocar 'Actualizar ahora' para descargarlo de nuevo.";
            default:
                return "La instalación no se completó. Vuelve a tocar 'Actualizar ahora' para intentarlo de nuevo.";
        }
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
