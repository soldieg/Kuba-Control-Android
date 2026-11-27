package com.mali2.inspector;

import java.util.ArrayList;
import java.util.List;

final class BleRawCapture {
    static final int MAX_PACKETS = 512;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    static final class Packet {
        final String address;
        final int rssiDbm;
        final long elapsedMs;
        final String advertisedName;
        final String rawHex;
        String decoderStatus;

        Packet(String address, int rssiDbm, long elapsedMs, String advertisedName, String rawHex) {
            this.address = address;
            this.rssiDbm = rssiDbm;
            this.elapsedMs = elapsedMs;
            this.advertisedName = advertisedName;
            this.rawHex = rawHex;
        }
    }

    private final ArrayList<Packet> packets = new ArrayList<>();
    private int omitted;

    Packet record(String address, int rssi, long elapsedMs, String name, byte[] data) {
        if (packets.size() == MAX_PACKETS) {
            omitted++;
            return null;
        }
        Packet packet = new Packet(address, rssi, elapsedMs, name, hex(data));
        packets.add(packet);
        return packet;
    }

    List<Packet> packets() { return packets; }
    int omittedCount() { return omitted; }

    private static String hex(byte[] data) {
        if (data == null) return null;
        char[] result = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int value = data[i] & 0xff;
            result[i * 2] = HEX[value >>> 4];
            result[i * 2 + 1] = HEX[value & 0xf];
        }
        return new String(result);
    }
}
