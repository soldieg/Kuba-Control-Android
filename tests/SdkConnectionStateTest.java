package com.mali2.inspector;

public final class SdkConnectionStateTest {
    public static void main(String[] args) {
        check(3, SdkConnectionState.CONNECTING, true, false);
        check(4, SdkConnectionState.SPP_CONNECTED, true, false);
        check(1, SdkConnectionState.READY, true, true);
        check(2, SdkConnectionState.FAILED, false, false);
        check(0, SdkConnectionState.DISCONNECTED, false, false);
        if (SdkConnectionState.fromJieli(99) != null) {
            throw new AssertionError("An unknown callback must preserve the displayed state");
        }
    }

    private static void check(int code, SdkConnectionState expected, boolean active, boolean ready) {
        SdkConnectionState state = SdkConnectionState.fromJieli(code);
        if (state != expected || state.isActive() != active || state.canQuery() != ready) {
            throw new AssertionError("Incorrect state for JieLi status " + code);
        }
    }
}
