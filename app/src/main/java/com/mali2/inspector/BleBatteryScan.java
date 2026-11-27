package com.mali2.inspector;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Handler;
import android.os.Looper;

import com.jieli.bluetooth.bean.BleScanMessage;
import com.jieli.bluetooth.bean.BluetoothOption;
import com.jieli.bluetooth.constant.JL_DeviceType;
import com.jieli.bluetooth.utils.ParseDataUtil;

import java.util.Collections;
import java.util.List;

final class BleBatteryScan extends ScanCallback {
    private static final int DURATION_MS = 35000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BluetoothLeScanner scanner;
    private final String selectedAddress;
    private final MainActivity.EventSink sink;
    private final BluetoothOption option = BluetoothOption.createDefaultOption();
    private final Runnable timedStop = () -> stop("TEMPO_CONCLUIDO");

    private boolean active;
    private int received, parsed, matched, radioMatches, decoderErrors;
    private String lastReading;

    BleBatteryScan(BluetoothLeScanner scanner, String selectedAddress, MainActivity.EventSink sink) {
        this.scanner = scanner;
        this.selectedAddress = selectedAddress;
        this.sink = sink;
    }

    boolean isActive() {
        return active;
    }

    void start() {
        active = true;
        try {
            ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            scanner.startScan(Collections.emptyList(), settings, this);
            main.postDelayed(timedStop, DURATION_MS);
            sink.emit("BLE_ESCUTA", "INICIADA", "Janela de 35 s. Correspondência pelo endereço clássico do anúncio.");
        } catch (RuntimeException | LinkageError error) {
            active = false;
            sink.emit("BLE_ESCUTA", "ERRO", String.valueOf(error));
        }
    }

    @Override public void onScanResult(int callbackType, ScanResult result) {
        main.post(() -> inspect(result));
    }

    @Override public void onBatchScanResults(List<ScanResult> results) {
        main.post(() -> {
            for (ScanResult result : results) inspect(result);
        });
    }

    @Override public void onScanFailed(int errorCode) {
        main.post(() -> {
            if (!active) return;
            active = false;
            main.removeCallbacks(timedStop);
            sink.emit("BLE_ESCUTA", "ERRO", "Falha do Android ao iniciar a escuta; código=" + errorCode);
        });
    }

    private void inspect(ScanResult result) {
        if (!active) return;
        received++;
        ScanRecord record = result.getScanRecord();
        if (record == null) return;
        if (BleBatteryReading.matches(selectedAddress, result.getDevice().getAddress())) radioMatches++;
        BleScanMessage message;
        try {
            message = ParseDataUtil.isFilterBleDevice(option, record.getBytes());
        } catch (RuntimeException | LinkageError error) {
            decoderErrors++;
            if (decoderErrors == 1) sink.emit("BLE_ESCUTA", "ERRO_DECODIFICADOR", error.getClass().getSimpleName());
            return;
        }
        if (message == null || message.isOTA()) return;
        parsed++;
        int type = message.getDeviceType();
        if (type != JL_DeviceType.JL_DEVICE_TYPE_TWS_HEADSET_V1
                && type != JL_DeviceType.JL_DEVICE_TYPE_TWS_HEADSET_V2
                && type != JL_DeviceType.JL_DEVICE_TYPE_CHARGING_BIN) return;
        if (!BleBatteryReading.matches(selectedAddress, message.getEdrAddr())) return;
        matched++;

        String reading = "Esquerdo: " + BleBatteryReading.level(message.getLeftDeviceQuantity(), message.isLeftCharging())
            + " · Direito: " + BleBatteryReading.level(message.getRightDeviceQuantity(), message.isRightCharging())
            + " · Estojo: " + BleBatteryReading.level(message.getChargingBinQuantity(), message.isDeviceCharging());
        if (!reading.equals(lastReading)) {
            lastReading = reading;
            sink.emit("BLE_BATERIA", "ANUNCIO_ATRIBUIDO", reading);
        }
    }

    void stop(String reason) {
        if (!active) return;
        active = false;
        main.removeCallbacks(timedStop);
        try {
            scanner.stopScan(this);
        } catch (RuntimeException error) {
            sink.emit("BLE_ESCUTA", "ERRO_AO_PARAR", error.getClass().getSimpleName());
        }
        sink.emit("BLE_ESCUTA", reason, "anuncios=" + received + " decodificados_jieli=" + parsed
            + " tws_do_fone=" + matched + " endereco_de_radio_igual=" + radioMatches
            + " erros_decodificador=" + decoderErrors);
    }
}
