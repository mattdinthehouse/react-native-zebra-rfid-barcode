package com.zebrarfidbarcode.barcode;

public interface IBarcodeScannedListener {
    void onBarcodeScanned(String barcode);
    void onTriggerPressed();
    void onTriggerReleased();
}
