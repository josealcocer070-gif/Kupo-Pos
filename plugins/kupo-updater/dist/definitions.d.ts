export interface ProgresoDescarga {
    descargado: number;
    total: number;
    porcentaje: number;
    estado: string;
}
export interface KupoUpdaterPlugin {
    puedeInstalar(): Promise<{
        puede: boolean;
    }>;
    abrirAjustesInstalacion(): Promise<void>;
    minimizar(): Promise<void>;
    cerrar(): Promise<void>;
    obtenerProgreso(): Promise<ProgresoDescarga>;
    descargarEInstalar(options: {
        url: string;
    }): Promise<{
        ok: boolean;
        necesitaPermiso?: boolean;
        ruta?: string;
    }>;
    addListener(eventName: 'progreso', listenerFunc: (info: ProgresoDescarga) => void): Promise<import('@capacitor/core').PluginListenerHandle>;
}
declare const KupoUpdater: KupoUpdaterPlugin;
export default KupoUpdater;
