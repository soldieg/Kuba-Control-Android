package com.mali2.inspector;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.jieli.bluetooth.bean.BleScanMessage;
import com.jieli.bluetooth.bean.BluetoothOption;
import com.jieli.bluetooth.constant.JL_DeviceType;
import com.jieli.bluetooth.utils.ParseDataUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
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

    private final BleRawCapture rawCapture = new BleRawCapture();
    private boolean active;
    private long startedNanos;
    private String startedAtUtc;
    private String resultStatus;
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
        startedNanos = SystemClock.elapsedRealtimeNanos();
        startedAtUtc = Instant.now().toString();
        resultStatus = "EM_ANDAMENTO";
        try {
            ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setLegacy(true).build();
            scanner.startScan(Collections.emptyList(), settings, this);
            main.postDelayed(timedStop, DURATION_MS);
            sink.emit("BLE_ESCUTA", "INICIADA", "Janela de 35 s. Correspondência pelo endereço clássico do anúncio.");
        } catch (RuntimeException | LinkageError error) {
            active = false;
            resultStatus = "ERRO_AO_INICIAR";
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
            resultStatus = "ERRO_ANDROID_" + errorCode;
            main.removeCallbacks(timedStop);
            sink.emit("BLE_ESCUTA", "ERRO", "Falha do Android ao iniciar a escuta; código=" + errorCode);
        });
    }

    private void inspect(ScanResult result) {
        if (!active) return;
        received++;
        ScanRecord record = result.getScanRecord();
        String address = result.getDevice().getAddress();
        byte[] data = record == null ? null : record.getBytes();
        long elapsed = Math.max(0, (result.getTimestampNanos() - startedNanos) / 1_000_000);
        BleRawCapture.Packet packet = rawCapture.record(address, result.getRssi(), elapsed,
            record == null ? null : record.getDeviceName(), data);
        String decoderStatus = inspectPayload(address, data);
        if (packet != null) packet.decoderStatus = decoderStatus;
    }

    private String inspectPayload(String address, byte[] data) {
        if (data == null) return "SEM_DADOS_ANDROID";
        if (BleBatteryReading.matches(selectedAddress, address)) radioMatches++;
        BleScanMessage message;
        try {
            message = ParseDataUtil.isFilterBleDevice(option, data);
        } catch (RuntimeException | LinkageError error) {
            decoderErrors++;
            if (decoderErrors == 1) sink.emit("BLE_ESCUTA", "ERRO_DECODIFICADOR", error.getClass().getSimpleName());
            return "ERRO_DECODIFICADOR";
        }
        if (message == null) return "NAO_RECONHECIDO";
        if (message.isOTA()) return "FORMATO_OTA";
        parsed++;
        int type = message.getDeviceType();
        if (type != JL_DeviceType.JL_DEVICE_TYPE_TWS_HEADSET_V1
                && type != JL_DeviceType.JL_DEVICE_TYPE_TWS_HEADSET_V2
                && type != JL_DeviceType.JL_DEVICE_TYPE_CHARGING_BIN) return "OUTRO_TIPO_JIELI";
        if (!BleBatteryReading.matches(selectedAddress, message.getEdrAddr())) return "TWS_SEM_CORRESPONDENCIA";
        matched++;

        String reading = "Esquerdo: " + BleBatteryReading.level(message.getLeftDeviceQuantity(), message.isLeftCharging())
            + " · Direito: " + BleBatteryReading.level(message.getRightDeviceQuantity(), message.isRightCharging())
            + " · Estojo: " + BleBatteryReading.level(message.getChargingBinQuantity(), message.isDeviceCharging());
        if (!reading.equals(lastReading)) {
            lastReading = reading;
            sink.emit("BLE_BATERIA", "ANUNCIO_ATRIBUIDO", reading);
        }
        return "ATRIBUIDO_AO_FONE";
    }

    JSONObject rawReport() throws JSONException {
        JSONArray packets = new JSONArray();
        for (BleRawCapture.Packet packet : rawCapture.packets()) {
            packets.put(new JSONObject()
                .put("bleAddress", packet.address == null ? JSONObject.NULL : packet.address)
                .put("rssiDbm", packet.rssiDbm)
                .put("elapsedMs", packet.elapsedMs)
                .put("advertisedName", packet.advertisedName == null ? JSONObject.NULL : packet.advertisedName)
                .put("rawHex", packet.rawHex == null ? JSONObject.NULL : packet.rawHex)
                .put("decoderStatus", packet.decoderStatus));
        }
        return new JSONObject()
            .put("startedAtUtc", startedAtUtc)
            .put("selectedClassicAddress", selectedAddress)
            .put("durationMs", DURATION_MS)
            .put("status", resultStatus)
            .put("source", "Android ScanRecord.getBytes()")
            .put("legacyOnly", true)
            .put("neverForLocation", true)
            .put("receivedCount", received)
            .put("omittedCount", rawCapture.omittedCount())
            .put("packets", packets);
    }

    void stop(String reason) {
        if (!active) return;
        active = false;
        resultStatus = reason;
        main.removeCallbacks(timedStop);
        try {
            scanner.stopScan(this);
        } catch (RuntimeException error) {
            sink.emit("BLE_ESCUTA", "ERRO_AO_PARAR", error.getClass().getSimpleName());
        }
        sink.emit("BLE_ESCUTA", reason, "anuncios=" + received + " decodificados_jieli=" + parsed
            + " tws_do_fone=" + matched + " endereco_de_radio_igual=" + radioMatches
            + " erros_decodificador=" + decoderErrors + " brutos_omitidos=" + rawCapture.omittedCount());
    }
}
