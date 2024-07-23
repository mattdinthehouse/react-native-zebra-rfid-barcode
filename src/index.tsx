import { NativeEventEmitter, NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-zebra-rfid-barcode' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const ZebraRfidBarcode = NativeModules.ZebraRfidBarcode
  ? NativeModules.ZebraRfidBarcode
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export const getAllDevices = async (): Promise<string[]> => {
  return await ZebraRfidBarcode.getAllDevices();
};

export const connectToDevice = (deviceName: string) => {
  return ZebraRfidBarcode.connectToDevice(deviceName);
};

export const setRfidPowerLevel = (level: "max" | "half" | "min") => {
  return ZebraRfidBarcode.setRfidPowerLevel(level);
};

export interface ZebraResultPayload {
  data: string;
}
export interface ZebraRfidResultPayload {
  data: string[];
}

export const ZebraEventEmitter = new NativeEventEmitter(ZebraRfidBarcode);

export enum ZebraEvent {
  ON_DEVICE_CONNECTED = 'onZebraDeviceConnected',
  ON_RFID = 'onZebraRFIDReaded',
  ON_BARCODE = 'onZebraBarcodeScanned',
  ON_TRIGGER_PRESSED = 'onZebraTriggerPressed',
  ON_TRIGGER_RELEASED = 'onZebraTriggerReleased',
}
