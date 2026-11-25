package com.grupobenitez.kupoautostart;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * KupoAutostart — abrir KUPO POS solo al prender la tablet (v196) +
 * permiso "mostrar sobre otras apps" (v199, Lalo 2026-11-25).
 *
 * El trabajo de arrancar lo hace BootReceiver, declarado en el manifiesto
 * de este plugin (se fusiona con el de la app en `cap sync`). El receptor
 * NO se toca: abre la actividad principal con el intent de lanzamiento del
 * propio paquete y el PIN se sigue pidiendo al entrar.
 *
 * En Android 10+ el sistema no deja arrancar una actividad desde segundo
 * plano (como hace el receptor al prender) sin el permiso
 * SYSTEM_ALERT_WINDOW ("mostrar sobre otras apps"). Por eso la tablet no
 * abría sola aunque el receptor sí estaba en el APK (v196).
 *
 * Estos dos métodos NO hacen nada solos: la app los llama solo cuando el
 * usuario toca el botón en Administrador de licencia (nunca automático en
 * cada arranque):
 * - puedeMostrarSobreOtrasApps: dice si el permiso ya está concedido.
 * - abrirAjustesMostrarSobreOtras: abre los ajustes del sistema para
 *   concederlo una sola vez (ACTION_MANAGE_OVERLAY_PERMISSION).
 */
@CapacitorPlugin(name = "KupoAutostart")
public class KupoAutostartPlugin extends Plugin {

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
}
