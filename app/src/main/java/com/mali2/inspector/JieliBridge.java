package com.mali2.inspector;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.jieli.bluetooth.bean.BluetoothOption;
import com.jieli.bluetooth.bean.base.BaseError;
import com.jieli.bluetooth.bean.base.VoiceMode;
import com.jieli.bluetooth.bean.device.DeviceInfo;
import com.jieli.bluetooth.bean.device.eq.EqInfo;
import com.jieli.bluetooth.bean.device.status.BatteryInfo;
import com.jieli.bluetooth.bean.device.voice.VolumeInfo;
import com.jieli.bluetooth.bean.response.ADVInfoResponse;
import com.jieli.bluetooth.constant.AttrAndFunCode;
import com.jieli.bluetooth.constant.BluetoothConstant;
import com.jieli.bluetooth.constant.ErrorCode;
import com.jieli.bluetooth.impl.rcsp.RCSPController;
import com.jieli.bluetooth.interfaces.rcsp.callback.BTRcspEventCallback;
import com.jieli.bluetooth.interfaces.rcsp.callback.OnRcspActionCallback;
import com.jieli.bluetooth.interfaces.rcsp.callback.OnRcspCallback;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.lang.reflect.Method;

/**
 * SDK JieLi transport, with device-authentication enabled; no OTA/setters.
 * All queries are user-initiated and read-only. Transport connected != RCSP authenticated.
 * Compiled against the official jl_bluetooth_rcsp_V4.2.0_40250-release.aar.
 */
public final class JieliBridge {
    interface Sink { void log(String category,String state,String description); }
    private final MainActivity activity;
    private final Sink sink;
    private final RCSPController controller;
    private static final int COMMAND_TIMEOUT_MS = 8000;
    private static final int PENDING_REPORT_MS = 60000;
    private static final int GET_TARGET_INFO_OPCODE = 0x03;

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile BluetoothDevice device;
    private volatile SdkConnectionState state = SdkConnectionState.IDLE;
    private volatile boolean queried;
    private volatile int attempt;
    private volatile long attemptStart;
    private final Runnable pending = this::logPending;

    private void logPending() {
        if (state.isActive() && !state.canQuery()) {
            sink.log("RCSP_SDK", "SEM_CONFIRMACAO",
                context() + " estado=" + state + " após " + (PENDING_REPORT_MS / 1000) + " s; aguardando callback final do SDK");
        }
    }
    private final OnRcspCallback rcspCallback = new OnRcspCallback() {
        @Override public void onRcspInit(BluetoothDevice target, int code) {
            if (!matches(target)) return;
            String result;
            switch (code) {
                case ErrorCode.ERR_NONE: result = "OK"; break;
                case ErrorCode.SUB_ERR_START_AUTH_DEVICE: result = "FALHA_AO_INICIAR_AUTENTICACAO"; break;
                case ErrorCode.SUB_ERR_AUTH_DEVICE: result = "FALHA_DE_AUTENTICACAO"; break;
                case ErrorCode.SUB_ERR_SEND_TIMEOUT: result = "TEMPO_LIMITE_RESPOSTA"; break;
                case ErrorCode.SUB_ERR_REMOTE_DEVICE_DISCONNECT: result = "DISPOSITIVO_DESCONECTADO"; break;
                default: result = "OUTRO_RESULTADO"; break;
            }
            sink.log("RCSP_INIT", result, context() + " codigo=" + code);
        }
        @Override public void onRcspError(BluetoothDevice target, BaseError error) {
            if (matches(target)) logError("RCSP_PROTOCOLO", error);
        }
    };
    private final BTRcspEventCallback callback = new BTRcspEventCallback() {
        @Override public void onConnection(BluetoothDevice target, int code) {
            activity.runOnUiThread(() -> {
                if (!matches(target)) return;
                SdkConnectionState next = SdkConnectionState.fromJieli(code);
                SdkConnectionState previous = state;
                sink.log("RCSP_TRANSPORTE", next == null ? "ESTADO_DESCONHECIDO" : next.name(),
                    context() + " codigo=" + code + " anterior=" + previous);
                if (next == null || (previous == SdkConnectionState.DISCONNECTING && next.isActive())) return;
                // A transport event can arrive after the SDK has already reported readiness.
                if (previous.canQuery() && (next == SdkConnectionState.CONNECTING
                        || next == SdkConnectionState.SPP_CONNECTED)) return;
                setState(next);
                if (!next.isActive() || next.canQuery()) main.removeCallbacks(pending);
                if (!next.isActive()) queried = false;
                if (next.canQuery() && !previous.canQuery()) queryAll();
            });
        }
        @Override public void onSppStatus(BluetoothDevice target, int code) {
            if (!matches(target)) return;
            SdkConnectionState observed = SdkConnectionState.fromJieli(code);
            sink.log("RCSP_SPP", observed == null ? "ESTADO_DESCONHECIDO" : observed.name(),
                context() + " codigo=" + code);
            if (observed == SdkConnectionState.SPP_CONNECTED || observed == SdkConnectionState.READY) {
                activity.runOnUiThread(() -> {
                    if (matches(target) && state == SdkConnectionState.CONNECTING) {
                        setState(SdkConnectionState.SPP_CONNECTED);
                    }
                });
            }
        }
        @Override public void onA2dpStatus(BluetoothDevice target, int code) {
            if (matches(target)) sink.log("RCSP_A2DP", "ESTADO", context() + " codigo=" + code);
        }
        @Override public void onHfpStatus(BluetoothDevice target, int code) {
            if (matches(target)) sink.log("RCSP_HFP", "ESTADO", context() + " codigo=" + code);
        }
        @Override public void onError(BaseError error) {
            if (device != null) logError("RCSP_SDK_ERRO", error);
        }
        @Override public void onBatteryChange(BluetoothDevice target, BatteryInfo info) {
            if (matches(target)) sink.log("RCSP_BATERIA", "EVENTO_DO_FONE", String.valueOf(info));
        }
        @Override public void onVolumeChange(BluetoothDevice target, VolumeInfo info) {
            if (matches(target)) sink.log("RCSP_VOLUME_INTERNO", "EVENTO_DO_FONE", String.valueOf(info));
        }
        @Override public void onEqChange(BluetoothDevice target, EqInfo info) {
            if (matches(target)) sink.log("RCSP_EQ", "EVENTO_DO_FONE", String.valueOf(info));
        }
        @Override public void onDeviceModeChange(BluetoothDevice target, int mode) {
            if (matches(target)) sink.log("RCSP_MODO", "EVENTO_DO_FONE", "modo="+mode);
        }
        @Override public void onCurrentVoiceMode(BluetoothDevice target, VoiceMode mode) {
            if (matches(target)) sink.log("RCSP_ANC_ATUAL", "EVENTO_DO_FONE", String.valueOf(mode));
        }
        @Override public void onVoiceModeList(BluetoothDevice target, List<VoiceMode> modes) {
            if (matches(target)) sink.log("RCSP_ANC_MODOS", "EVENTO_DO_FONE", String.valueOf(modes));
        }
    };
    JieliBridge(MainActivity a,Sink s) {
        activity=a; sink=s;
        if (!RCSPController.isInit()) {
            BluetoothOption option=BluetoothOption.createDefaultOption()
                .setPriority(BluetoothOption.PREFER_SPP)
                .setUseDeviceAuth(true)
                .setUseMultiDevice(false)
                .setTimeoutMs(COMMAND_TIMEOUT_MS);
            RCSPController.init(a.getApplicationContext(),option);
        } else {
            sink.log("RCSP_SDK", "JA_INICIALIZADO", "Instância RCSP do processo existente, não altera autenticação");
        }
        controller=RCSPController.getInstance();
        controller.addBTRcspEventCallback(callback);
        controller.registerOnRcspCallback(rcspCallback);
        sink.log("RCSP_SDK", "INICIALIZADO", "JieLi SDK oficial + PREFER_SPP + isUseDeviceAuth(true); sem OTA");
    }
    boolean isConnected() {
        return state.canQuery() && device != null;
    }
    private boolean matches(BluetoothDevice target) {
        BluetoothDevice current = device;
        return current != null && target != null && current.equals(target);
    }
    private void setState(SdkConnectionState next) {
        state = next;
        activity.sdkStateChanged(next);
    }
    private String context() {
        return "tentativa=" + attempt + " decorrido_ms=" + (SystemClock.elapsedRealtime() - attemptStart);
    }
    private void logError(String origin, BaseError error) {
        if (error == null) {
            sink.log(origin, "ERRO", context() + " sem código");
            return;
        }
        int opcode = error.getOpCode();
        String command = opcode == GET_TARGET_INFO_OPCODE ? " comando=GetTargetInfo" : "";
        String opcodeText = opcode < 0 ? "indisponivel" : String.format(Locale.ROOT, "0x%02X", opcode);
        sink.log(origin, "ERRO", context() + " codigo=" + error.getCode()
            + " subcodigo=" + error.getSubCode() + " opcode=" + opcodeText + command
            + " sequencia=" + error.getSn() + " motivo_sdk=" + error.getReason()
            + " mensagem=" + error.getMessage());
    }
    void connect(BluetoothDevice selected) {
        if (state.isActive()) {
            sink.log("RCSP_SDK", "JA_EM_USO", context() + " estado=" + state);
            return;
        }
        device = selected;
        queried = false;
        attempt++;
        attemptStart = SystemClock.elapsedRealtime();
        setState(SdkConnectionState.CONNECTING);
        try {
            BluetoothOption option = controller.getBluetoothOption();
            sink.log("RCSP_SDK", "CONECTANDO", context() + " transporte=SPP uuid=" + option.getSppUUID()
                + " autenticacao=" + option.isUseDeviceAuth() + " timeout_comando_ms=" + option.getTimeoutMs()
                + " pareamento=" + selected.getBondState()
                + " tipo=" + selected.getType() + " servicos_android=" + Arrays.toString(selected.getUuids()));
            main.postDelayed(pending, PENDING_REPORT_MS);
            controller.connectDevice(selected, BluetoothConstant.PROTOCOL_TYPE_SPP);
        } catch (RuntimeException | LinkageError error) {
            main.removeCallbacks(pending);
            setState(SdkConnectionState.FAILED);
            sink.log("RCSP_SDK", "ERRO_AO_CONECTAR", context() + " " + error);
        }
    }
    void queryAll() {
        BluetoothDevice d=device;
        if (!isConnected()) { sink.log("RCSP", "SEM_TRANSPORTE", "Conecte primeiro"); return; }
        if (queried) { sink.log("RCSP", "CONSULTA_EM_ANDAMENTO", "Não enviar duas rajadas de consultas"); return; }
        queried=true;
        try {
            DeviceInfo synced = controller.isRcspInit(d) ? controller.getDeviceInfo(d) : null;
            if (synced != null) {
                readDeviceInfo(d, synced, "SINCRONIZACAO_SDK");
                return;
            }
            sink.log("RCSP_ID", "SOLICITADO", "Comando oficial requestDeviceInfo; aguardando resposta");
            controller.requestDeviceInfo(d, 0xffffffff, new OnRcspActionCallback<DeviceInfo>() {
                @Override public void onSuccess(BluetoothDevice dev, DeviceInfo info) {
                    if (!isConnected() || !matches(dev)) return;
                    if (info == null) {
                        queried = false;
                        sink.log("RCSP_ID", "RESPOSTA_NULA", "SDK retornou objeto nulo; nenhuma leitura interna confirmada");
                        return;
                    }
                    readDeviceInfo(d, info, "RESPOSTA_RCSP_SDK_RECEBIDA");
                }
                @Override public void onError(BluetoothDevice dev, BaseError error) {
                    queried=false;
                    if (matches(dev)) logError("RCSP_ID", error);
                }
            });
        } catch (Throwable e) { queried=false; sink.log("RCSP_ID","EXCECAO", e.toString()); }
    }
    private void readDeviceInfo(BluetoothDevice target, DeviceInfo info, String source) {
        sink.log("RCSP_ID", source, "Identificação fornecida pelo SDK para esta sessão.");
        sink.log("RCSP_ID_CAMPOS", "CAMPOS_PRESENTES", safeGetters(info,
            "getName", "getDeviceName", "getFirmwareVersion", "getFirmwareVersionCode",
            "getVersionName", "getVersionCode", "getSdkType", "getVid", "getPid", "getUid",
            "getPlatform", "getAuthVersion", "getSysExpandFunc"));
        Object expand = tryGetter(info, "getSysExpandFunc");
        if (expand != null) {
            sink.log("RCSP_MODO_JOGO", "ESTADO_SE_SDK_EXPUSER", safeGetters(expand, "isGameMode", "getGameMode"));
        }
        querySystem(target);
    }
    private void querySystem(BluetoothDevice d) {
        if (!isConnected() || !matches(d)) return;
        try {
            int mask = (1 << AttrAndFunCode.SYS_INFO_ATTR_BATTERY)
                | (1 << AttrAndFunCode.SYS_INFO_ATTR_VOLUME)
                | (1 << AttrAndFunCode.SYS_INFO_ATTR_EQ);
            controller.getDevSysInfo(d, AttrAndFunCode.SYS_INFO_FUNCTION_PUBLIC, mask,
                new OnRcspActionCallback<Boolean>() {
                    @Override public void onSuccess(BluetoothDevice dev,Boolean result) {
                        if(matches(dev)) sink.log("RCSP_SYS_INFO","COMANDO_ACEITO","Aguardar callbacks de bateria, volume e EQ; result="+result);
                        querySettings(d);
                    }
                    @Override public void onError(BluetoothDevice dev,BaseError error) {
                        if(matches(dev)) sink.log("RCSP_SYS_INFO","NAO_SUPORTADO_OU_ERRO",String.valueOf(error));
                        querySettings(d);
                    }
                });
        } catch(Throwable e){ sink.log("RCSP_SYS_INFO","EXCECAO",e.toString()); querySettings(d); }
    }
    private void querySettings(BluetoothDevice d) {
        if (!isConnected() || !matches(d)) return;
        try {
            controller.getDeviceSettingsInfo(d, 0xffffffff, new OnRcspActionCallback<ADVInfoResponse>() {
                @Override public void onSuccess(BluetoothDevice dev, ADVInfoResponse info) {
                    if(matches(dev)) {
                        sink.log("RCSP_CONFIG","RESPOSTA_DO_FONE",String.valueOf(info));
                        if(info!=null){
                            sink.log("RCSP_TOQUES","DADOS_DO_FONE", "keys="+String.valueOf(info.getKeySettingsList()));
                            sink.log("RCSP_DETECCAO_ORELHA","DADOS_DO_FONE", "inEarSettings="+info.getInEarSettings());
                            sink.log("RCSP_TWS","DADOS_DO_FONE", "L="+info.getLeftDeviceQuantity()+" R="+info.getRightDeviceQuantity()+" estojo="+info.getChargingBinQuantity()+" (somente se compatível)");
                        }
                    }
                    queryNoise(d);
                }
                @Override public void onError(BluetoothDevice dev,BaseError error) {
                    if(matches(dev)) sink.log("RCSP_CONFIG","NAO_SUPORTADO_OU_ERRO",String.valueOf(error));
                    queryNoise(d);
                }
            });
        } catch(Throwable e) { sink.log("RCSP_CONFIG","EXCECAO",e.toString()); queryNoise(d); }
    }
    private void queryNoise(BluetoothDevice d) {
        if (!isConnected() || !matches(d)) return;
        try {
            controller.getCurrentVoiceMode(d,new OnRcspActionCallback<Boolean>(){
                @Override public void onSuccess(BluetoothDevice dev,Boolean v){
                    if(matches(dev)) sink.log("RCSP_ANC_ATUAL","CONSULTA_ACEITA","Resultado em callback onCurrentVoiceMode; ack="+v);
                    queryNoiseList(d);
                }
                @Override public void onError(BluetoothDevice dev,BaseError err){
                    if(matches(dev)) sink.log("RCSP_ANC_ATUAL","NAO_SUPORTADO_OU_ERRO",String.valueOf(err));
                    queryNoiseList(d);
                }
            });
        } catch(Throwable e){sink.log("RCSP_ANC_ATUAL","EXCECAO",e.toString());queryNoiseList(d);}
    }
    private void queryNoiseList(BluetoothDevice d) {
        if (!isConnected() || !matches(d)) return;
        try {
            controller.getAllVoiceModes(d,new OnRcspActionCallback<Boolean>(){
                @Override public void onSuccess(BluetoothDevice dev,Boolean v){
                    if(matches(dev)) sink.log("RCSP_ANC_MODOS","CONSULTA_ACEITA","Lista em callback onVoiceModeList; ack="+v);
                    queried=false;
                }
                @Override public void onError(BluetoothDevice dev,BaseError err){
                    if(matches(dev)) sink.log("RCSP_ANC_MODOS","NAO_SUPORTADO_OU_ERRO",String.valueOf(err));
                    queried=false;
                }
            });
        } catch(Throwable e){ queried=false; sink.log("RCSP_ANC_MODOS","EXCECAO",e.toString()); }
    }
    private static Object tryGetter(Object target, String method) {
        if (target == null || method.contains("Key") || method.contains("Secret") || method.contains("Password")) return null;
        try {
            Method fn = target.getClass().getMethod(method);
            if (fn.getParameterTypes().length != 0) return null;
            return fn.invoke(target);
        } catch (ReflectiveOperationException | SecurityException error) { return null; }
    }
    private static String safeGetters(Object value, String... names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            Object result = tryGetter(value, n);
            if (result != null) sb.append(n).append('=').append(String.valueOf(result)).append(';');
        }
        return sb.length() > 0 ? sb.toString() : "Nenhum destes getters esta disponivel na versao de SDK/firmware";
    }
    void disconnect() {
        BluetoothDevice current = device;
        if (current == null || !state.isActive() || state == SdkConnectionState.DISCONNECTING) return;
        main.removeCallbacks(pending);
        setState(SdkConnectionState.DISCONNECTING);
        queried = false;
        try {
            controller.disconnectDevice(current);
        } catch (RuntimeException | LinkageError error) {
            setState(SdkConnectionState.FAILED);
            sink.log("RCSP_SDK", "ERRO_AO_DESCONECTAR", context() + " " + error);
        }
    }
    void close() {
        main.removeCallbacks(pending);
        controller.unregisterOnRcspCallback(rcspCallback);
        controller.removeBTRcspEventCallback(callback);
        BluetoothDevice current = device;
        device = null;
        if (current != null && state.isActive()) controller.disconnectDevice(current);
    }
}
