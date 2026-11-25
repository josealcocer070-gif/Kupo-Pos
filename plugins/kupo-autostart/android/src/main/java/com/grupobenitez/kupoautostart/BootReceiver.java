package com.grupobenitez.kupoautostart;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BootReceiver — abre KUPO POS al prender la tablet (v196, Lalo 2026-11-24).
 *
 * Escucha BOOT_COMPLETED (y QUICKBOOT_POWERON, que mandan algunas tablets
 * en vez del primero) y arranca la actividad principal de la app con el
 * intent de lanzamiento del propio paquete, sin inventar la clase: así
 * abre la MainActivity de Capacitor tal como la abriría el ícono.
 *
 * El PIN se sigue pidiendo al entrar (la app abre en el inicio de sesión).
 * Si Android no deja arrancar la actividad, se registra y no se rompe
 * nada: el receptor nunca tumba el arranque del sistema.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "KupoAutostart";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) return;
        try {
            Intent launch = context.getPackageManager()
                    .getLaunchIntentForPackage(context.getPackageName());
            if (launch == null) {
                Log.w(TAG, "Sin intent de lanzamiento para " + context.getPackageName());
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
            Log.i(TAG, "KUPO POS abierto al prender la tablet.");
        } catch (Exception e) {
            Log.w(TAG, "No se pudo abrir KUPO POS al prender: " + e.getMessage());
        }
    }
}
