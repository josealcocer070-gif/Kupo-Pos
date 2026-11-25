package com.grupobenitez.kupoautostart;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BootReceiver — abre KUPO POS al prender la tablet (v196, Lalo 2026-11-24;
 * método tipo alarma desde v200, Lalo 2026-11-25).
 *
 * Escucha BOOT_COMPLETED (y QUICKBOOT_POWERON, que mandan algunas tablets
 * en vez del primero) y publica el aviso de arranque del plugin: una
 * notificación de alta prioridad con full-screen intent hacia la actividad
 * principal, así Android abre la app sola como una alarma.
 *
 * Antes (v196-v199) arrancaba la actividad directo con startActivity, pero
 * Android 10+ lo bloquea (BAL) sin el permiso "mostrar sobre otras apps",
 * y la tablet AUMI lo tiene deshabilitado de fábrica: por eso ya no se
 * abre directo, se publica el aviso. El PIN se sigue pidiendo al entrar
 * (la app abre en el inicio de sesión). Si el aviso no se puede publicar,
 * se registra y no se rompe nada: el receptor nunca tumba el arranque
 * del sistema.
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
            KupoAutostartPlugin.publicarAvisoArranque(context);
            Log.i(TAG, "Aviso de arranque publicado al prender la tablet.");
        } catch (Exception e) {
            Log.w(TAG, "No se pudo publicar el aviso de arranque: " + e.getMessage());
        }
    }
}
