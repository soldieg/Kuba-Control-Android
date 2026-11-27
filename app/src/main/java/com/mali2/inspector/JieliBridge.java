package com.mali2.inspector;

import android.bluetooth.BluetoothDevice;

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
import com.jieli.bluetooth.constant.StateCode;
import com.jieli.bluetooth.impl.rcsp.RCSPController;
import com.jieli.bluetooth.interfaces.rcsp.callback.BTRcspEventCallback;
import com.jieli.bluetooth.interfaces.rcsp.callback.OnRcspActionCallback;

import java.util.List;
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
    private BluetoothDevice device;
    private boolean connected = false;
    private boolean queried = false;
    private final BTRcspEventCallback callback = new BTRcspEventCallback() {
        @Override public void onConnection(BluetoothDevice target, int status) {
            if (!matches(target)) return;
            connected = status == StateCode.CONNECTION_OK;
            activity.runOnUiThread(() -> activity.sdkConnected(connected));
            sink.log("RCSP_TRANSPORTE", connected?"CONECTADO_SDK":"ESTADO="+status,
                "Conexão do SDK: status="+status+" (a autenticação ainda exige resposta RCSP)");
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
                .setTimeoutMs(5000);
            RCSPController.init(a.getApplicationContext(),option);
        } else {
            sink.log("RCSP_SDK", "JA_INICIALIZADO", "Instância RCSP do processo existente, não altera autenticação");
        }
        controller=RCSPController.getInstance();
        controller.addBTRcspEventCallback(callback);
        sink.log("RCSP_SDK", "INICIALIZADO", "JieLi SDK oficial + PREFER_SPP + isUseDeviceAuth(true); sem OTA");
    }
    boolean isConnected(){ return connected && device != null; }
    private boolean matches(BluetoothDevice target) {
        return device != null && target != null && device.getAddress().equalsIgnoreCase(target.getAddress());
    }
    void connect(BluetoothDevice selected) {
        if (device != null && !device.getAddress().equalsIgnoreCase(selected.getAddress())) disconnect();
        device=selected; connected=false; queried=false;
        sink.log("RCSP_SDK", "CONECTANDO", "Transport=SPP; autenticação oficial habilitada; alvo="+selected.getAddress());
        controller.connectDevice(selected, BluetoothConstant.PROTOCOL_TYPE_SPP);
    }
    void queryAll() {
        BluetoothDevice d=device;
        if (!isConnected()) { sink.log("RCSP", "SEM_TRANSPORTE", "Conecte primeiro"); return; }
        if (queried) { sink.log("RCSP", "CONSULTA_EM_ANDAMENTO", "Não enviar duas rajadas de consultas"); return; }
        queried=true;
        sink.log("RCSP_ID", "SOLICITADO", "Comando oficial requestDeviceInfo; aguardando resposta/autenticação");
        try {
            controller.requestDeviceInfo(d, 0xffffffff, new OnRcspActionCallback<DeviceInfo>() {
                @Override public void onSuccess(BluetoothDevice dev, DeviceInfo info) {
                    if (!matches(dev)) return;
                    if (info == null) {
                        queried = false;
                        sink.log("RCSP_ID", "RESPOSTA_NULA", "SDK retornou objeto nulo; nenhuma leitura interna confirmada");
                        return;
                    }
                    sink.log("RCSP_ID", "RESPOSTA_RCSP_SDK_RECEBIDA", String.valueOf(info));
                    sink.log("RCSP_ID_CAMPOS", "CAMPOS_PRESENTES", safeGetters(info,
                        "getName", "getDeviceName", "getFirmwareVersion", "getFirmwareVersionCode", "getVersionName", "getVersionCode", "getSdkType", "getVid", "getPid", "getUid", "getPlatform", "getAuthVersion", "getSysExpandFunc"));
                    Object expand = tryGetter(info, "getSysExpandFunc");
                    if (expand != null) {
                        sink.log("RCSP_MODO_JOGO", "ESTADO_SE_SDK_EXPUSER", safeGetters(expand, "isGameMode", "getGameMode"));
                    }
                    // Only after first non-null SDK response do we run other read-only functions.
                    querySystem(d);
                }
                @Override public void onError(BluetoothDevice dev, BaseError error) {
                    queried=false;
                    if (matches(dev)) sink.log("RCSP_ID", "ERRO_OU_INCOMPATIVEL", String.valueOf(error));
                }
            });
        } catch (Throwable e) { queried=false; sink.log("RCSP_ID","EXCECAO", e.toString()); }
    }
    private void querySystem(BluetoothDevice d) {
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
        if(device!=null) controller.disconnectDevice(device);
        connected=false; queried=false;
        activity.runOnUiThread(()->activity.sdkConnected(false));
    }
    void close() {
        disconnect();
        controller.removeBTRcspEventCallback(callback);
    }
}
