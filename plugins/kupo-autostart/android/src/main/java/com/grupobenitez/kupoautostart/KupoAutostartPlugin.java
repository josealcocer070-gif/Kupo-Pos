package com.grupobenitez.kupoautostart;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * KupoAutostart — abrir KUPO POS solo al prender la tablet (v196) +
 * permiso "mostrar sobre otras apps" (v199, Lalo 2026-11-25) +
 * arranque por notificación tipo alarma (v200, Lalo 2026-11-25).
 *
 * El trabajo de arrancar lo hace BootReceiver, declarado en el manifiesto
 * de este plugin (se fusiona con el de la app en `cap sync`). El receptor
 * NO se toca: publica un aviso con full-screen intent y la app se abre
 * sola como una alarma. El PIN se sigue pidiendo al entrar.
 *
 * Por qué la notificación (v200): en Android 10+ el sistema no deja
 * arrancar una actividad desde segundo plano (como hace el receptor al
 * prender) sin el permiso SYSTEM_ALERT_WINDOW ("mostrar sobre otras
 * apps"), y la tablet AUMI lo tiene DESHABILITADO DE FÁBRICA ("Función
 * no disponible — Se desactivó esta función porque ralentiza el
 * teléfono"), así que ese camino está muerto en este aparato. El camino
 * que sí deja Android es el de las alarmas: una notificación de alta
 * prioridad con full-screen intent abre la actividad aunque la app no
 * esté abierta. Para que el aviso se vea hace falta el permiso de
 * notificaciones (POST_NOTIFICATIONS, Android 13+), que se pide una sola
 * vez desde el botón en Administrador de licencia.
 *
 * Métodos viejos (v199) se quedan intactos aunque ya no se usen desde el
 * bloque de arranque: puedeMostrarSobreOtrasApps y
 * abrirAjustesMostrarSobreOtras.
 */
@CapacitorPlugin(
    name = "KupoAutostart",
    permissions = {
        @Permission(alias = "notificaciones", strings = { Manifest.permission.POST_NOTIFICATIONS })
    }
)
public class KupoAutostartPlugin extends Plugin {

    private static final String PERM_NOTIFICACIONES = "notificaciones";
    public static final String CANAL_ARRANQUE_ID = "kupo_arranque";
    private static final int AVISO_ARRANQUE_ID = 1001;

    /** Crea el canal del aviso de arranque (IMPORTANCE_HIGH: suena y se ve
     *  como una alarma). Sin canal no hay notificación en Android 8+. */
    public static void crearCanalArranque(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel canal = new NotificationChannel(
                CANAL_ARRANQUE_ID,
                "Arranque de KUPO POS",
                NotificationManager.IMPORTANCE_HIGH);
        canal.setDescription("Aviso que abre KUPO POS sola al prender la tablet, como una alarma.");
        nm.createNotificationChannel(canal);
    }

    /** Publica el aviso de arranque con full-screen intent hacia la
     *  actividad principal: Android abre la app sola al prender, como una
     *  alarma. Si la notificación no se puede mostrar, no se rompe nada. */
    public static void publicarAvisoArranque(Context ctx) {
        try {
            crearCanalArranque(ctx);
            Intent launch = ctx.getPackageManager()
                    .getLaunchIntentForPackage(ctx.getPackageName());
            if (launch == null) return;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent fullScreen = PendingIntent.getActivity(
                    ctx, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification aviso = new Notification.Builder(ctx, CANAL_ARRANQUE_ID)
                    .setSmallIcon(ctx.getApplicationInfo().icon)
                    .setContentTitle("KUPO POS")
                    .setContentText("Toca aquí si la app no se abrió sola.")
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setPriority(Notification.PRIORITY_MAX)
                    .setFullScreenIntent(fullScreen, true)
                    .setContentIntent(fullScreen)
                    .setAutoCancel(true)
                    .build();
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(AVISO_ARRANQUE_ID, aviso);
        } catch (Exception e) {
            // Sin aviso no se abre sola, pero nada se rompe.
        }
    }


    @PluginMethod
    public void puedeMostrarSobreOtrasApps(PluginCall call) {
        boolean puede;
        if (Build.VERSION.SDK_INT >= 23) {
            puede = Settings.canDrawOverlays(getContext());
        } else {
            puede = true;
        }
        JSObject ret = new JSObject();
        ret.put("puede", puede);
        call.resolve(ret);
    }

    @PluginMethod
    public void abrirAjustesMostrarSobreOtras(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            i.setData(Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("no se pudo abrir los ajustes");
        }
    }

    /** ¿KUPO POS puede mandar notificaciones? En Android 13+ hace falta el
     *  permiso POST_NOTIFICATIONS; en versiones viejas siempre se puede. */
    @PluginMethod
    public void puedeNotificar(PluginCall call) {
        boolean puede;
        if (Build.VERSION.SDK_INT >= 24) {
            NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            puede = nm != null && nm.areNotificationsEnabled();
        } else {
            puede = true;
        }
        JSObject ret = new JSObject();
        ret.put("puede", puede);
        call.resolve(ret);
    }

    /** Pide el permiso de notificaciones (POST_NOTIFICATIONS, Android 13+).
     *  En Android 12 o menos no hay permiso que pedir: ya se puede.
     *  Resuelve {puede:true} si quedó concedido. Si se niega, abre los
     *  ajustes de notificaciones de la app para prenderlo a mano y resuelve
     *  {abrioAjustes:true}. */
    @PluginMethod
    public void pedirPermisoNotificar(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33) {
            JSObject ret = new JSObject();
            ret.put("puede", true);
            call.resolve(ret);
            return;
        }
        if (hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            JSObject ret = new JSObject();
            ret.put("puede", true);
            call.resolve(ret);
            return;
        }
        try {
            requestPermissionForAlias(PERM_NOTIFICACIONES, call, "permisoNotificarCallback");
        } catch (Exception e) {
            abrirAjustesNotificaciones(call);
        }
    }

    @PermissionCallback
    private void permisoNotificarCallback(PluginCall call) {
        if (call == null) return;
        if (hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            JSObject ret = new JSObject();
            ret.put("puede", true);
            call.resolve(ret);
            return;
        }
        abrirAjustesNotificaciones(call);
    }

    private void abrirAjustesNotificaciones(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            JSObject ret = new JSObject();
            ret.put("abrioAjustes", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("no se pudo abrir los ajustes de notificaciones");
        }
    }

    /** Publica AHORA MISMO el aviso de arranque (para probar sin reiniciar
     *  la tablet): la app debe abrirse sola como una alarma. */
    @PluginMethod
    public void probarArranque(PluginCall call) {
        publicarAvisoArranque(getContext());
        JSObject ret = new JSObject();
        ret.put("publicada", true);
        call.resolve(ret);
    }
}
