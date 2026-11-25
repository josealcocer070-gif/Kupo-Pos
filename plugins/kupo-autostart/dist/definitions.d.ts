export interface KupoAutostartPlugin {
    puedeMostrarSobreOtrasApps(): Promise<{
        puede: boolean;
    }>;
    abrirAjustesMostrarSobreOtras(): Promise<void>;
}
declare const KupoAutostart: KupoAutostartPlugin;
export default KupoAutostart;
