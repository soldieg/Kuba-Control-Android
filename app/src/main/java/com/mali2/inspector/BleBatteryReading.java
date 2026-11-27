package com.mali2.inspector;

final class BleBatteryReading {
    private BleBatteryReading() { }

    static boolean matches(String selectedAddress, String advertisedAddress) {
        return selectedAddress != null && selectedAddress.equalsIgnoreCase(advertisedAddress);
    }

    static String level(int percent, boolean charging) {
        // JieLi uses zero to indicate an unavailable or offline component.
        if (percent < 1 || percent > 100) return "indisponível";
        return percent + "%" + (charging ? " (carregando)" : "");
    }
}
