package com.mali2.inspector;

public final class BleBatteryReadingTest {
    public static void main(String[] args) {
        String target = "AA:BB:CC:DD:EE:FF";
        check(BleBatteryReading.matches(target, "aa:bb:cc:dd:ee:ff"));
        check(!BleBatteryReading.matches(target, null));
        check(!BleBatteryReading.matches(target, "AA:BB:CC:DD:EE:00"));
        check(BleBatteryReading.level(1, false).equals("1%"));
        check(BleBatteryReading.level(100, true).equals("100% (carregando)"));
        check(BleBatteryReading.level(0, true).equals("indisponível"));
        check(BleBatteryReading.level(101, false).equals("indisponível"));
        check(BleBatteryReading.level(-1, false).equals("indisponível"));
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Atribuição ou nível de bateria incorreto");
    }
}
