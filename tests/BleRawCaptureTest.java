package com.mali2.inspector;

public final class BleRawCaptureTest {
    public static void main(String[] args) {
        BleRawCapture capture = new BleRawCapture();
        BleRawCapture.Packet first = capture.record("AA:BB:CC:DD:EE:FF", -72, 17, "Mali", new byte[] {0, 1, (byte) 0x80, (byte) 0xff});
        check("000180ff".equals(first.rawHex));
        check(first.rssiDbm == -72 && first.elapsedMs == 17);
        check("Mali".equals(first.advertisedName));
        check(capture.record("AA:BB:CC:DD:EE:FF", -71, 19, null, null).rawHex == null);
        for (int i = 2; i < BleRawCapture.MAX_PACKETS; i++) capture.record(null, -1, i, null, new byte[0]);
        check(capture.record(null, -1, 1000, null, new byte[] {1}) == null);
        check(capture.packets().size() == BleRawCapture.MAX_PACKETS);
        check(capture.omittedCount() == 1);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Raw BLE capture changed the data or exceeded its limit");
    }
}
