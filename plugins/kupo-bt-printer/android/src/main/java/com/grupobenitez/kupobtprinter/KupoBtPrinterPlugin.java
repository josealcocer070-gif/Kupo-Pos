package com.grupobenitez.kupobtprinter;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * KupoBtPrinter — Bluetooth clásico (SPP/RFCOMM) para impresoras térmicas ESC/POS.
 * Parte de KUPO POS v190 (Lalo 2026-11-24).
 *
 * El socket vive en esta instancia nativa, que vive mientras la app está
 * abierta: sobrevive a la recarga del WebView. El navegador siempre
 * desconecta el Bluetooth al recargar (Lalo 23 nov), por eso en el APK ya
 * no se usa Web Bluetooth para imprimir.
 *
 * Solo conecta a dispositivos ya emparejados en Ajustes de Android (la
 * POS-5890A pide PIN 0000 o 1234 al emparejar). No hace descubrimiento.
 */
@CapacitorPlugin(
    name = "KupoBtPrinter",
    permissions = {
        @Permission(alias = "bluetoothConnect", strings = { Manifest.permission.BLUETOOTH_CONNECT })
    }
)
public class KupoBtPrinterPlugin extends Plugin {

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String PERM_BT_CONNECT = "bluetoothConnect";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BluetoothSocket socket;
    private OutputStream out;
    private String connectedAddress;
    private String connectedName;

    private boolean needsBtConnectPermission() {
        return Build.VERSION.SDK_INT >= 31;
    }

    private boolean hasBtConnectPermission() {
        if (!needsBtConnectPermission()) return true;
        return hasPermission(Manifest.permission.BLUETOOTH_CONNECT);
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        if (!needsBtConnectPermission()) {
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
            return;
        }
        requestPermissionForAlias(PERM_BT_CONNECT, call, "btConnectPermCallback");
    }

    @PermissionCallback
    private void btConnectPermCallback(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", hasPermission(Manifest.permission.BLUETOOTH_CONNECT));
        call.resolve(ret);
    }

    @PluginMethod
    public void listDevices(PluginCall call) {
        executor.execute(() -> {
            if (!hasBtConnectPermission()) {
                call.reject("permiso Bluetooth denegado");
                return;
            }
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                call.reject("este aparato no tiene Bluetooth");
                return;
            }
            JSArray devices = new JSArray();
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded != null) {
                for (BluetoothDevice d : bonded) {
                    JSObject o = new JSObject();
                    o.put("name", d.getName() != null ? d.getName() : "");
                    o.put("address", d.getAddress());
                    devices.put(o);
                }
            }
            JSObject ret = new JSObject();
            ret.put("devices", devices);
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void isConnected(PluginCall call) {
        JSObject ret = new JSObject();
        boolean connected = socket != null && socket.isConnected();
        ret.put("connected", connected);
        if (connected) {
            ret.put("name", connectedName != null ? connectedName : "");
            ret.put("address", connectedAddress != null ? connectedAddress : "");
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void connect(PluginCall call) {
        String address = call.getString("address");
        if (address == null || address.isEmpty()) {
            call.reject("falta la dirección de la impresora");
            return;
        }
        executor.execute(() -> {
            if (!hasBtConnectPermission()) {
                call.reject("permiso Bluetooth denegado");
                return;
            }
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                call.reject("este aparato no tiene Bluetooth");
                return;
            }
            if (!adapter.isEnabled()) {
                call.reject("el Bluetooth está apagado");
                return;
            }
            try {
                closeQuietly();
                // v194: cancelDiscovery() exige BLUETOOTH_SCAN en Android 12+
                // y la app solo pide BLUETOOTH_CONNECT. La app no hace
                // descubrimiento, así que si falta el permiso se salta
                // sin romper la conexión (antes tronaba aquí y el toast
                // decía "Need BLUETOOTH_SCAN permission").
                try {
                    adapter.cancelDiscovery();
                } catch (Exception ignored) {
                    // Sin BLUETOOTH_SCAN no se puede cancelar el
                    // descubrimiento; la conexión SPP funciona igual.
                }
                BluetoothDevice device = adapter.getRemoteDevice(address);
                BluetoothSocket s = device.createRfcommSocketToServiceRecord(SPP_UUID);
                try {
                    s.connect();
                } catch (IOException firstFail) {
                    // Algunas térmicas baratas no aceptan el canal SDP: reintento
                    // por el canal RFCOMM 1 (truco clásico de SPP).
                    try { s.close(); } catch (IOException ignored) {}
                    try {
                        s = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", int.class).invoke(device, 1);
                        s.connect();
                    } catch (Exception retryFail) {
                        throw firstFail;
                    }
                }
                socket = s;
                out = s.getOutputStream();
                connectedAddress = address;
                connectedName = device.getName() != null ? device.getName() : "";
                JSObject ret = new JSObject();
                ret.put("name", connectedName);
                ret.put("address", connectedAddress);
                call.resolve(ret);
            } catch (Exception e) {
                closeQuietly();
                // v192: el motivo real (p. ej. "read failed, socket might closed")
                // llega al toast de la app en vez de un mensaje genérico.
                String detalle = e.getMessage() != null ? e.getMessage() : "";
                call.reject("no se pudo conectar a la impresora" + (detalle.isEmpty() ? "" : " (" + detalle + ")"));
            }
        });
    }

    @PluginMethod
    public void write(PluginCall call) {
        String data = call.getString("data");
        if (data == null) {
            call.reject("falta el ticket");
            return;
        }
        executor.execute(() -> {
            try {
                if (socket == null || !socket.isConnected() || out == null) {
                    call.reject("impresora no conectada");
                    return;
                }
                byte[] bytes = Base64.decode(data, Base64.DEFAULT);
                int off = 0;
                while (off < bytes.length) {
                    int n = Math.min(512, bytes.length - off);
                    out.write(bytes, off, n);
                    out.flush();
                    off += n;
                }
                call.resolve();
            } catch (Exception e) {
                call.reject("no se pudo escribir en la impresora");
            }
        });
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        executor.execute(() -> {
            closeQuietly();
            call.resolve();
        });
    }

    private void closeQuietly() {
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        out = null;
        socket = null;
        connectedAddress = null;
        connectedName = null;
    }

    @Override
    protected void handleOnDestroy() {
        executor.execute(this::closeQuietly);
    }
}
