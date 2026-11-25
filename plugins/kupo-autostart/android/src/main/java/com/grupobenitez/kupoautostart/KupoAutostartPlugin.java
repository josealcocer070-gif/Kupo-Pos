package com.grupobenitez.kupoautostart;

import com.getcapacitor.Plugin;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * KupoAutostart — abrir KUPO POS solo al prender la tablet (v196).
 *
 * No tiene métodos JS: el trabajo lo hace BootReceiver, declarado en el
 * manifiesto de este plugin (se fusiona con el de la app en `cap sync`).
 * La clase existe para que Capacitor registre el plugin como los demás.
 */
@CapacitorPlugin(name = "KupoAutostart")
public class KupoAutostartPlugin extends Plugin {
}
