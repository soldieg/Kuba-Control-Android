package com.mali2.inspector;

enum SdkConnectionState {
    IDLE("Não iniciado"),
    CONNECTING("Conectando"),
    SPP_CONNECTED("Transporte aberto · aguardando RCSP"),
    READY("Pronto para consultas"),
    FAILED("Falha de conexão"),
    DISCONNECTING("Desconectando"),
    DISCONNECTED("Desconectado");

    final String label;

    SdkConnectionState(String label) {
        this.label = label;
    }

    // Values from StateCode in the bundled JieLi RCSP SDK.
    static SdkConnectionState fromJieli(int code) {
        switch (code) {
            case 0: return DISCONNECTED;
            case 1: return READY;
            case 2: return FAILED;
            case 3: return CONNECTING;
            case 4: return SPP_CONNECTED;
            default: return null;
        }
    }

    boolean isActive() {
        return this == CONNECTING || this == SPP_CONNECTED || this == READY || this == DISCONNECTING;
    }

    boolean canQuery() {
        return this == READY;
    }
}
