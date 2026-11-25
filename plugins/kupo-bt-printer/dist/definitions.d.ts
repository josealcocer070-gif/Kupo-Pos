export interface BtDevice {
    name: string;
    address: string;
}
export interface KupoBtPrinterPlugin {
    requestPermissions(): Promise<{
        granted: boolean;
    }>;
    listDevices(): Promise<{
        devices: BtDevice[];
    }>;
    isConnected(): Promise<{
        connected: boolean;
        name?: string;
        address?: string;
    }>;
    connect(options: {
        address: string;
    }): Promise<{
        name: string;
        address: string;
    }>;
    write(options: {
        data: string;
    }): Promise<void>;
    disconnect(): Promise<void>;
}
declare const KupoBtPrinter: KupoBtPrinterPlugin;
export default KupoBtPrinter;
