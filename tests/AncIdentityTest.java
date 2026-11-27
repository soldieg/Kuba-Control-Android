package com.mali2.inspector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HexFormat;

public final class AncIdentityTest {
    private static byte[] hex(String text) { return HexFormat.of().parseHex(text); }

    public static void main(String[] args) throws Exception {
        byte[] both = hex("AA55020008000456322E312E31002241AA55030002003907C768");
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        AncIdentity result = AncIdentity.read(new ByteArrayInputStream(both), sent);
        if (!"V2.1.1".equals(result.protocol) || result.chipCode != 7 || !"JL710N".equals(result.family())) {
            throw new AssertionError("Identidade decodificada incorretamente");
        }
        if (!HexFormat.of().withUpperCase().formatHex(sent.toByteArray()).equals("AA550200010004E336AA5503000100394C7B")) {
            throw new AssertionError("Consultas diferentes da captura Windows");
        }
        both[15] ^= 1;
        sent.reset();
        try {
            AncIdentity.read(new ByteArrayInputStream(both), sent);
            throw new AssertionError("CRC corrompido aceito");
        } catch (IOException expected) {
            if (sent.size() != 9) throw new AssertionError("Identificacao continuou apos falha");
        }
    }
}
