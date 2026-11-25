export interface KupoAutostartPlugin {
    puedeMostrarSobreOtrasApps(): Promise<{
        puede: boolean;
    }>;
    abrirAjustesMostrarSobreOtras(): Promise<void>;
    puedeNotificar(): Promise<{
        puede: boolean;
    }>;
    pedirPermisoNotificar(): Promise<{
        puede?: boolean;
        abrioAjustes?: boolean;
    }>;
    probarArranque(): Promise<{
        publicada: boolean;
    }>;
}
declare const KupoAutostart: KupoAutostartPlugin;
export default KupoAutostart;
