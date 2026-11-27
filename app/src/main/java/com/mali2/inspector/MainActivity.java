package com.mali2.inspector;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local-only Mali 2 diagnostic UI. Does not modify headphone settings or flash firmware.
 * The SDK is the ONLY part allowed to send read-only authenticated RCSP requests,
 * and only after explicit user confirmation. Raw SPP is read-only.
 */
public final class MainActivity extends Activity {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int REQUEST_CONNECT = 71;
    private static final int REQUEST_SCAN = 72;
    private static final int REQUEST_SAVE = 73;
    private static final int EVENT_LIMIT = 420;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<JSONObject> events = new ArrayList<>();
    private final ArrayList<BluetoothDevice> paired = new ArrayList<>();
    private final ArrayList<String> names = new ArrayList<>();
    private final ArrayList<String> observations = new ArrayList<>();
    private LinearLayout content;
    private TextView status, results, timeline;
    private Spinner deviceSpinner;
    private BluetoothDevice selected;
    private volatile BluetoothSocket rawSocket;
    private BluetoothSocket identitySocket;
    private volatile boolean readingRaw = false;
    private long rawBytes = 0;
    private volatile String rawState = "NAO_TESTADO";
    private String sdkState = "NAO_TESTADO";
    private JieliBridge jieli;
    private AudioManager audio;
    private boolean monitorEnabled = false;
    private volatile boolean destroyed = false;
    private String lastAudio = "";
    private final Runnable pollAudio = new Runnable() {
        @Override public void run() {
            if (destroyed || !monitorEnabled) return;
            String v = audioSnapshot();
            if (!v.equals(lastAudio)) {
                lastAudio = v;
                log("ANDROID_AUDIO", "MUDANCA_OBSERVADA", v);
            }
            main.postDelayed(this, 350);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        setTitle("Mali 2 Inspector");
        buildScreen();
        record("APP", "INICIADO", "Android " + Build.VERSION.RELEASE + "; SDK: " + Build.VERSION.SDK_INT);
        ensureConnectPermission();
    }

    private int dp(float value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private TextView txt(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(sp); t.setTextColor(color);
        t.setPadding(dp(2), dp(5), dp(2), dp(5));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private void title(String text) {
        TextView v = txt(text, 18, 0xFF26354D, true);
        v.setPadding(dp(2),dp(18),dp(2),dp(7)); content.addView(v);
    }
    private void paragraph(String text) {
        TextView v = txt(text, 13, 0xFF46566B, false); content.addView(v);
    }
    private void button(String label, Runnable action) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.topMargin = dp(5); content.addView(b, lp);
        b.setOnClickListener(v -> action.run());
    }
    private void buildScreen() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(0xFFF7F9FC);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(15), dp(18), dp(26)); scroll.addView(content);
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        setContentView(scroll);
        TextView header = txt("MALI 2  /  INSPECTOR", 24, 0xFF182C48, true);
        content.addView(header);
        paragraph("Android 8+ • Bluetooth Classic / JL_SPP / RCSP • coleta local, sem OTA e sem alterar o fone");
        title("1. Dispositivo emparelhado");
        deviceSpinner = new Spinner(this);
        content.addView(deviceSpinner, new LinearLayout.LayoutParams(-1, dp(56)));
        button("Atualizar fones emparelhados", this::listDevices);
        button("Selecionar este fone e ler dados Android", this::selectAndReadAndroid);
        title("2. Conexão real com o fone");
        paragraph("São testes distintos. Não abra SPP bruto e RCSP do SDK ao mesmo tempo. SPP bruto lê bytes, mas não envia nada.");
        button("Ler família do chip e protocolo ANC", this::readAncIdentity);
        button("Testar SPP puro — SOMENTE RECEBER", this::testRaw);
        button("Encerrar SPP puro", this::stopRaw);
        button("Conectar com SDK JieLi — SPP + autenticação", this::connectJieli);
        button("Consultar RCSP: identificação, bateria, EQ e modos", this::queryJieli);
        button("Desconectar SDK JieLi", this::disconnectJieli);
        button("Abrir demonstração de controles", () -> DemoPanel.show(this));
        title("3. Controles sensíveis / modo jogo");
        paragraph("Toque em um gesto e execute-o no fone. O monitor registra o volume Android e se há reprodução de áudio. Só isso NÃO confirma um evento AVRCP nem o estado interno do modo jogo.");
        button("Iniciar / parar monitor de áudio", this::toggleMonitor);
        String[] gestures = {
            "ESQUERDO · 1 toque (volume)", "DIREITO · 1 toque (volume)",
            "ESQUERDO · 2 toques (play/pausa)", "DIREITO · 2 toques (play/pausa)",
            "ESQUERDO · 3 toques (faixa)", "DIREITO · 3 toques (faixa)",
            "ESQUERDO · segurar (modo jogo)", "DIREITO · segurar (modo jogo)"
        };
        for (String gesture : gestures) button("Marcar: " + gesture, () -> markGesture(gesture));
        title("4. Evidências e relatório");
        status = txt("Aguardando permissão Bluetooth...", 15, 0xFF1F5076, true); content.addView(status);
        results = txt("Nenhum dado interno confirmado.", 13, 0xFF1D3348, false); content.addView(results);
        button("Exportar relatório JSON (sem enviar à internet)", this::saveReport);
        timeline = txt("Sem eventos.", 12, 0xFF4C5B6D, false); content.addView(timeline);
        updateUi();
    }

    private void ensureConnectPermission() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_CONNECT);
        } else listDevices();
    }
    private boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQUEST_CONNECT) {
            if (granted) listDevices(); else log("PERMISSAO", "NEGADA", "BLUETOOTH_CONNECT é necessária para listar e conectar");
        } else if (requestCode == REQUEST_SCAN) {
            if (granted) startJieli(); else log("PERMISSAO", "NEGADA", "BLUETOOTH_SCAN necessária para caminho SDK neste aparelho");
        }
    }

    private BluetoothAdapter adapter() {
        BluetoothManager mgr = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        return mgr == null ? null : mgr.getAdapter();
    }
    private void listDevices() {
        if (!hasConnectPermission()) { ensureConnectPermission(); return; }
        try {
            BluetoothAdapter adapter = adapter();
            if (adapter == null) { log("BLUETOOTH", "INEXISTENTE", "Android não disponibiliza adaptador"); return; }
            if (!adapter.isEnabled()) { log("BLUETOOTH", "DESLIGADO", "Ligue o Bluetooth e tente novamente"); return; }
            paired.clear(); names.clear();
            Set<BluetoothDevice> bond = adapter.getBondedDevices();
            if (bond != null) {
                for (BluetoothDevice dev : bond) {
                    String name = dev.getName();
                    if (name != null && name.toLowerCase(Locale.ROOT).contains("mali")) {
                        paired.add(dev); names.add(name + "  ·  " + dev.getAddress());
                    }
                }
            }
            if (paired.isEmpty()) names.add("Nenhum 'Mali' emparelhado — emparelhe nas Configurações Android");
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
            deviceSpinner.setAdapter(spinnerAdapter);
            log("BLUETOOTH", "PAREADOS", "Fones com nome Mali=" + paired.size() + "; não são conexões RCSP");
        } catch (SecurityException err) { log("PERMISSAO", "ERRO", err.toString()); }
    }
    private boolean selectDevice() {
        int pos = deviceSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= paired.size()) { log("ALVO", "SEM_FONE", "Emparelhe o Mali 2 no Android primeiro"); return false; }
        selected = paired.get(pos);
        log("ALVO", "SELECIONADO", selected.getName() + " / " + selected.getAddress());
        return true;
    }
    private void selectAndReadAndroid() {
        if (!hasConnectPermission()) { ensureConnectPermission(); return; }
        if (!selectDevice()) return;
        try {
            log("ANDROID_BATTERY", "NAO_DISPONIVEL", "A API pública Android não fornece a bateria Classic nesta tela. A consulta RCSP pode informar bateria quando suportada.");
            android.os.ParcelUuid[] uuids = selected.getUuids();
            StringBuilder all = new StringBuilder();
            if (uuids != null) for (android.os.ParcelUuid p : uuids) all.append(p.toString()).append(";");
            log("ANDROID_UUID", "LISTA_CONHECIDA", all.length() == 0 ? "Sem lista UUID/cache" : all.toString());
            log("ANDROID_A2DP_HFP", "NAO_E_CODEC_ATIVO", "bondState=" + selected.getBondState() + " type=" + selected.getType());
        } catch (Exception e) { log("ANDROID_BATTERY", "ERRO", e.toString()); }
    }

    private void readAncIdentity() {
        if (!hasConnectPermission()) { ensureConnectPermission(); return; }
        if (identitySocket != null || rawSocket != null || "CONECTANDO".equals(rawState)
                || "CONECTANDO".equals(sdkState) || "CONECTADO".equals(sdkState)) {
            log("ANC_IDENTIDADE", "OCUPADO", "Encerre as outras sessões Bluetooth antes de consultar.");
            return;
        }
        if (!selectDevice()) return;
        try {
            BluetoothSocket socket = selected.createRfcommSocketToServiceRecord(SPP_UUID);
            identitySocket = socket;
            Runnable timeout = () -> {
                log("ANC_IDENTIDADE", "TEMPO_LIMITE", "Sem resposta completa em 15 segundos.");
                closeIdentitySocket(socket);
            };
            log("ANC_IDENTIDADE", "CONSULTANDO", "Leitura de protocolo ANC e família do chip via RFCOMM.");
            main.postDelayed(timeout, 15000);
            io.execute(() -> {
                try {
                    socket.connect();
                    AncIdentity identity = AncIdentity.read(socket.getInputStream(), socket.getOutputStream());
                    log("ANC_IDENTIDADE", "RESPOSTA_VALIDADA", String.format(Locale.ROOT,
                        "Protocolo ANC %s · %s (0x%02X)",
                        identity.protocol, identity.family(), identity.chipCode));
                } catch (IOException | SecurityException error) {
                    if (!destroyed) log("ANC_IDENTIDADE", "SEM_LEITURA", String.valueOf(error.getMessage()));
                } finally {
                    closeIdentitySocket(socket);
                    main.post(() -> {
                        main.removeCallbacks(timeout);
                        if (identitySocket == socket) identitySocket = null;
                    });
                }
            });
        } catch (IOException | SecurityException error) {
            log("ANC_IDENTIDADE", "ERRO", String.valueOf(error.getMessage()));
        }
    }

    private static void closeIdentitySocket(BluetoothSocket socket) {
        if (socket == null) return;
        try { socket.close(); } catch (IOException ignored) { }
    }

    private void testRaw() {
        if (!hasConnectPermission()) { ensureConnectPermission(); return; }
        if (identitySocket != null || rawSocket != null || "CONECTANDO".equals(rawState) || readingRaw) { log("SPP", "JA_ATIVO", "Feche antes de tentar novamente"); return; }
        if ("CONECTADO".equals(sdkState) || "CONECTANDO".equals(sdkState)) { log("SPP", "BLOQUEADO", "Desconecte o SDK JieLi primeiro"); return; }
        if (!selectDevice()) return;
        new AlertDialog.Builder(this).setTitle("Abrir SPP somente leitura?")
            .setMessage("Este teste abre um socket RFCOMM autenticado pelo pareamento Android. Ele NÃO envia comandos RCSP, não altera o fone e pode competir com conexões de outros aplicativos.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Abrir", (d,w) -> {
                try {
                    BluetoothSocket socket = selected.createRfcommSocketToServiceRecord(SPP_UUID);
                    rawSocket = socket;
                    rawState = "CONECTANDO";
                    io.execute(() -> listenRaw(socket));
                } catch (IOException | SecurityException error) {
                    log("SPP", "ERRO", String.valueOf(error.getMessage()));
                }
            }).show();
    }
    private void listenRaw(BluetoothSocket socket) {
        try (BluetoothSocket connection = socket) {
            connection.connect();
            readingRaw = true;
            rawState = "ABERTO";
            log("SPP", "SOCKET_ABERTO", "Socket RFCOMM estabelecido, aguardando RX.");
            InputStream in = connection.getInputStream();
            byte[] buffer = new byte[1024];
            while (readingRaw) {
                int count = in.read(buffer);
                if (count < 0) break;
                rawBytes += count;
                log("SPP_RX", "BYTES_RECEBIDOS",
                    "n=" + count + "; total=" + rawBytes + "; amostra=" + hex(buffer, count));
            }
        } catch (IOException | SecurityException error) {
            if (!destroyed) log("SPP", "ERRO", String.valueOf(error.getMessage()));
        } finally {
            readingRaw = false;
            rawState = "FECHADO";
            rawSocket = null;
            log("SPP", "FECHADO", "bytesRX=" + rawBytes);
        }
    }

    private String hex(byte[] b, int n) {
        StringBuilder s = new StringBuilder();
        int cap = Math.min(32, n);
        for (int i=0;i<cap;i++) s.append(String.format(Locale.ROOT, "%02X", b[i] & 0xff));
        if (n > cap) s.append("... [AMOSTRA LIMITADA]");
        return s.toString();
    }
    private void stopRaw() {
        readingRaw = false;
        BluetoothSocket sock = rawSocket;
        try { if (sock != null) sock.close(); } catch (Exception ignored) { }
        log("SPP", "PARAR_SOLICITADO", "Fechamento de socket solicitado");
    }
    private void connectJieli() {
        if (!hasConnectPermission()) { ensureConnectPermission(); return; }
        if (identitySocket != null || readingRaw || rawSocket != null || "CONECTANDO".equals(rawState)) {
            log("RCSP", "BLOQUEADO", "Aguarde a identificação ou encerre a escuta SPP."); return;
        }
        if (!selectDevice()) return;
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setTitle("Permissão adicional")
                .setMessage("O SDK da JieLi pode utilizar descoberta Bluetooth para estabelecer sua conexão. O app não coleta localização.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Permitir", (d,w)->requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN},REQUEST_SCAN)).show();
            return;
        }
        startJieli();
    }
    private void startJieli() {
        if (selected == null || identitySocket != null || rawSocket != null || "CONECTANDO".equals(rawState)) return;
        new AlertDialog.Builder(this).setTitle("Autenticação oficial JieLi")
            .setMessage("Conectar pelo SDK oficial usando BluetoothOption.PREFER_SPP e autenticação ativada. Essa etapa utiliza pacotes de negociação do SDK, mas nenhuma gravação, OTA ou alteração de configuração.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Conectar", (d,w) -> {
                try {
                    if (jieli == null) jieli = new JieliBridge(this, this::log);
                    sdkState = "CONECTANDO";
                    jieli.connect(selected);
                } catch (Throwable e) {
                    sdkState="ERRO"; log("RCSP", "ERRO_INICIALIZACAO", e.toString());
                }
            }).show();
    }
    private void queryJieli() {
        if (jieli == null || !jieli.isConnected()) {
            log("RCSP", "SEM_CONEXAO", "A conexão SDK precisa ser confirmada antes das consultas"); return;
        }
        new AlertDialog.Builder(this).setTitle("Consultar informações internas?")
            .setMessage("Envia apenas comandos documentados de LEITURA usando a sessão JieLi. Identificação, bateria, volume, EQ, ANC, configurações de gestos e detecção de uso, caso suportados.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Consultar", (d,w) -> {
                try { jieli.queryAll(); } catch (Throwable e) { log("RCSP", "ERRO_CONSULTA", e.toString()); }
            }).show();
    }
    private void disconnectJieli() {
        if (jieli != null) {
            try { jieli.disconnect(); } catch(Throwable e) { log("RCSP", "ERRO_DESCONECTAR", e.toString()); }
        }
        sdkState="FECHADO";
        log("RCSP", "FECHADO", "Desconexão solicitada");
    }
    void sdkConnected(boolean connected) { sdkState = connected ? "CONECTADO" : "DESCONECTADO"; updateUi(); }

    private void toggleMonitor() {
        monitorEnabled = !monitorEnabled;
        getWindow().setFlags(monitorEnabled ? WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON : 0,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        main.removeCallbacks(pollAudio);
        lastAudio = "";
        log("MONITOR", monitorEnabled ? "INICIADO" : "PARADO", "Amostragem a cada 350 ms enquanto a tela está aberta");
        if (monitorEnabled) main.post(pollAudio);
    }
    private String audioSnapshot() {
        try {
            int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            boolean active = audio.isMusicActive();
            return "volumeAndroid="+cur+"/"+max+"; musicaAtiva="+active;
        } catch (Exception e) { return "audioIndisponivel:"+e.getMessage(); }
    }
    private void markGesture(String gesture) {
        if (!monitorEnabled) toggleMonitor();
        log("GESTO_MANUAL", "MARCADO", gesture+"; antes="+audioSnapshot());
        main.postDelayed(() -> log("GESTO_AMOSTRA_1S", "AMOSTRA", gesture+"; depois="+audioSnapshot()), 1000);
        main.postDelayed(() -> log("GESTO_AMOSTRA_3S", "AMOSTRA", gesture+"; depois="+audioSnapshot()), 3000);
        if (gesture.contains("segurar")) {
            new AlertDialog.Builder(this).setTitle("Modo jogo")
                .setMessage("Depois de segurar, ouviu um aviso de modo jogo? Isso é confirmação manual, NÃO leitura do firmware.")
                .setPositiveButton("Sim", (d,w)->log("MODO_JOGO_MANUAL","SIM",gesture))
                .setNegativeButton("Não / não sei", (d,w)->log("MODO_JOGO_MANUAL","NAO_CONFIRMADO",gesture)).show();
        }
    }
    interface EventSink { void emit(String origin,String state,String note); }
    void log(String source, String state, String note) { runOnUiThread(() -> record(source,state,note)); }
    private void record(String source, String state, String note) {
        try {
            JSONObject j = new JSONObject();
            j.put("timestampUtc", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US) {{ setTimeZone(java.util.TimeZone.getTimeZone("UTC")); }}.format(new Date()));
            j.put("origin",source); j.put("status",state); j.put("detail",note == null ? "" : note);
            events.add(j);
            if (events.size() > EVENT_LIMIT) events.remove(0);
            updateUi();
        } catch (Exception ignored) { }
    }
    private void updateUi() {
        if (status == null || results == null || timeline == null) return;
        status.setText("SPP bruto: " + rawState + "  |  RX: " + rawBytes + " bytes  |  RCSP: " + sdkState);
        StringBuilder info = new StringBuilder();
        info.append("Alvo: ").append(selected == null ? "não selecionado" : selected.getName()+" / "+selected.getAddress());
        info.append("\nFonte atual: Android + respostas reais JieLi, se houver.\n");
        for (int i=events.size()-1;i>=0 && i>events.size()-7;i--) {
            JSONObject e = events.get(i);
            if (e.optString("origin").startsWith("RCSP_") || e.optString("origin").equals("ANC_IDENTIDADE")) {
                info.append(e.optString("origin")).append(": ").append(e.optString("detail")).append("\n");
            }
        }
        results.setText(info.toString());
        StringBuilder tail = new StringBuilder("Últimos eventos:\n");
        for (int i=events.size()-1, c=0;i>=0 && c<25;i--,c++) {
            JSONObject x=events.get(i);
            tail.append(x.optString("timestampUtc").substring(11,23)).append("  ")
                .append(x.optString("origin")).append(" / ").append(x.optString("status"))
                .append("\n  ").append(x.optString("detail")).append("\n");
        }
        timeline.setText(tail.toString());
    }
    private void saveReport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json"); intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,"mali2_android_diagnostico.json");
        startActivityForResult(intent, REQUEST_SAVE);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode != REQUEST_SAVE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            JSONObject out = new JSONObject();
            out.put("appVersion","1.1.1"); out.put("platform","Android");
            out.put("selectedMac", selected == null ? JSONObject.NULL : selected.getAddress());
            out.put("sppState",rawState);out.put("sppBytesReceived",rawBytes);
            out.put("rcspTransportStatus",sdkState);
            out.put("confirmedInternalData","Conferir respostas em ANC_IDENTIDADE e callbacks RCSP_*");
            out.put("note","Medição Android não é prova de bateria interna; SDK genérico não confirma suporte do modelo.");
            out.put("events",new JSONArray(events));
            OutputStream stream = getContentResolver().openOutputStream(uri);
            if (stream == null) throw new IOException("Não foi possível abrir destino");
            try (OutputStream os=stream) { os.write(out.toString(2).getBytes(StandardCharsets.UTF_8)); }
            log("EXPORTACAO","SALVA","JSON exportado por escolha do usuário");
        } catch (Exception e) { log("EXPORTACAO","ERRO",e.toString()); }
    }
    @Override protected void onDestroy() {
        destroyed=true; monitorEnabled=false;main.removeCallbacks(pollAudio);
        closeIdentitySocket(identitySocket);
        stopRaw();
        if (jieli!=null) try {jieli.close();} catch(Throwable ignored){}
        io.shutdownNow();
        super.onDestroy();
    }
}
