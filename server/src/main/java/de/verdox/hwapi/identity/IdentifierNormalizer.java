package de.verdox.hwapi.identity;

import java.text.Normalizer;
import java.util.Locale;

/** Canonicalizes identifiers before they are persisted or looked up. */
public final class IdentifierNormalizer {
    private IdentifierNormalizer() {
    }

    public static String gtin(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        if (digits.length() == 12) digits = "0" + digits;
        if (digits.length() != 8 && digits.length() != 13 && digits.length() != 14) return null;
        return isValidGtin(digits) ? digits : null;
    }

    public static String mpn(String raw) {
        if (raw == null) return null;
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", "");
        return normalized.isBlank() ? null : normalized;
    }

    public static String asin(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private static boolean isValidGtin(String digits) {
        int checkDigit = Character.digit(digits.charAt(digits.length() - 1), 10);
        int sum = 0;
        boolean multiplyByThree = true;
        for (int i = digits.length() - 2; i >= 0; i--) {
            int digit = Character.digit(digits.charAt(i), 10);
            sum += digit * (multiplyByThree ? 3 : 1);
            multiplyByThree = !multiplyByThree;
        }
        return (10 - (sum % 10)) % 10 == checkDigit;
    }
}
