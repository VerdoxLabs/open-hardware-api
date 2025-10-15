package de.verdox.openhardwareapi.util;

public final class ProductCodeUtils {
    public static String digits(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("\\D+", "");
        return d.isEmpty() ? null : d;
    }

    /**
     * Nimmt 12- oder 13-stellig; liefert gültige GTIN-13 oder null.
     */
    public static String normalizeToGtin13(String raw) {
        String d = digits(raw);
        if (d == null) return null;
        if (d.length() == 12) d = "0" + d;        // UPC→GTIN-13
        if (d.length() != 13 || !isValidGtin13(d)) return null;
        return d;
    }

    public static boolean isValidGtin13(String gtin13) {
        String d = digits(gtin13);
        if (d == null || d.length() != 13) return false;
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int val = d.charAt(11 - i) - '0';
            sum += (i % 2 == 0) ? 3 * val : val; // von rechts: pos1*3, pos2*1, …
        }
        int cd = (10 - (sum % 10)) % 10;
        return cd == (d.charAt(12) - '0');
    }
}
