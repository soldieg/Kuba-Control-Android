package com.mali2.inspector;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class AncIdentity {
    final String protocol;
    final int chipCode;

    private AncIdentity(String protocol, int chipCode) {
        this.protocol = protocol;
        this.chipCode = chipCode;
    }

    String family() {
        return chipCode == 0x07 || chipCode == 0xA7 ? "JL710N" : "Família desconhecida";
    }

    static AncIdentity read(InputStream source, OutputStream output) throws IOException {
        DataInputStream input = new DataInputStream(source);
        byte[] version = query(input, output, 2, 4);
        if (version.length != 8 || version[7] != 0) {
            throw new IOException("Formato desconhecido na resposta de versão do protocolo ANC");
        }
        String protocol = new String(version, 1, 6, StandardCharsets.US_ASCII);
        if (!"V2.1.1".equals(protocol)) {
            throw new IOException("Protocolo ANC " + protocol + " sem compatibilidade verificada");
        }
        byte[] chip = query(input, output, 3, 0x39);
        if (chip.length != 2) throw new IOException("Formato desconhecido na resposta de família do chip");
        return new AncIdentity(protocol, chip[1] & 0xff);
    }

    private static byte[] query(DataInputStream input, OutputStream output, int seq, int op)
        throws IOException {
        byte[] request = {(byte) 0xaa, 0x55, (byte) seq, 0, 1, 0, (byte) op};
        int crc = crc16(0, request);
        byte[] frame = java.util.Arrays.copyOf(request, 9);
        frame[7] = (byte) crc;
        frame[8] = (byte) (crc >>> 8);
        output.write(frame);
        output.flush();

        byte[] header = new byte[6];
        input.readFully(header);
        if (u16(header, 0) != 0x55aa || u16(header, 2) != seq) {
            throw new IOException("Cabeçalho ou sequência da resposta inválidos");
        }
        int length = u16(header, 4);
        if (length < 2 || length > 256) throw new IOException("Tamanho da resposta inválido");
        byte[] data = new byte[length];
        input.readFully(data);
        int receivedCrc = input.readUnsignedByte() | (input.readUnsignedByte() << 8);
        if (receivedCrc != crc16(crc16(0, header), data) || (data[0] & 0xff) != op) {
            throw new IOException("Checksum ou tipo de resposta inválido");
        }
        return data;
    }

    private static int u16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static int crc16(int crc, byte[] bytes) {
        for (byte value : bytes) {
            crc ^= (value & 0xff) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = ((crc << 1) ^ ((crc & 0x8000) == 0 ? 0 : 0x1021)) & 0xffff;
            }
        }
        return crc;
    }
}
