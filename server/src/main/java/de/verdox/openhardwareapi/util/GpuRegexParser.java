package de.verdox.openhardwareapi.util;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public final class GpuRegexParser {

    public enum Vendor {NVIDIA, AMD, INTEL}

    public static final class ParsedGpu {
        public final Vendor vendor;
        public final String series;   // RTX | GTX | GT | RX | Arc
        public final String number;   // 5090 | 780 | 6700 | A770 ...
        public final String suffix;   // Ti | SUPER | XT | XTX | "" (leer)

        public ParsedGpu(Vendor v, String series, String number, String suffix) {
            this.vendor = v;
            this.series = series;
            this.number = number;
            this.suffix = suffix == null ? "" : suffix;
        }

        public String canonical() {
            String sfx = suffix.isBlank() ? "" : " " + suffix;
            return HardwareSpecService.normalizeModel(series + " " + number + sfx);
        }

        @Override
        public String toString() {
            return canonical();
        }
    }

    // --- Wichtig: KEINE Wortgrenze zwischen Zahl und Suffix, damit "RX9060XT" und "RTX4070Ti" matchen ---

    // NVIDIA:  (nvidia|geforce)? (rtx|gtx|gt) [sep?] digits [sep?] (ti|super|s)?
    private static final java.util.regex.Pattern NVIDIA = java.util.regex.Pattern.compile("(?i)\\b(?:nvidia|ge\\s*force)?\\s*(rtx|gtx|gt)\\s*[-_ ]*([1-9]\\d{2,3})(?:\\s*[-_ ]*(ti|super|s))?");

    // AMD:     (amd|radeon)? rx [sep?] digits [sep?] (xtx|xt|x)?
    private static final java.util.regex.Pattern AMD = java.util.regex.Pattern.compile(
            "(?i)\\b" +
                    "(?:amd|radeon)?" +                // optional AMD oder Radeon davor
                    "\\s*" +
                    "(?:" +
                    "(rx)\\s*[-_ ]*(\\d{3,4})(?:\\s*[-_ ]*(xtx|xt|x))?" + // RX 400–7000er Karten etc.
                    "|" +
                    "(hd)\\s*[-_ ]*(\\d{3,4})" +                        // Radeon HD 4000–7000
                    "|" +
                    "(?:rx\\s*)?(vega)\\s*([0-9]{1,2})" +               // RX Vega 56, Vega 64 etc.
                    "|" +
                    "(vi{1,3}|vii)"                                     // Radeon VI oder VII (roman numerals)
                    + ")",
            java.util.regex.Pattern.CASE_INSENSITIVE
    );

    // Intel:   (intel|arc)? a [sep?] digits   → kanonisch "A770"
    private static final java.util.regex.Pattern INTEL = java.util.regex.Pattern.compile("(?i)\\b(?:intel\\s*)?(?:arc\\s*)?(a)\\s*[-_ ]*([1-9]\\d{2,3})");

    public static Optional<ParsedGpu> parse(String raw) {
        try {
            if (raw == null || raw.isBlank()) return Optional.empty();
            String s = normalize(raw);
            var m = NVIDIA.matcher(s);
            if (m.find()) {
                String series = m.group(1).toUpperCase(java.util.Locale.ROOT); // RTX/GTX/GT
                String num = m.group(2);
                String sufRaw = m.group(3);
                String suffix = (sufRaw == null) ? "" : switch (sufRaw.toLowerCase(java.util.Locale.ROOT)) {
                    case "ti" -> "Ti";
                    case "super", "s" -> "SUPER";  // “4060S” → SUPER
                    default -> "";
                };
                return Optional.of(new ParsedGpu(Vendor.NVIDIA, series, num, suffix));
            }

            m = AMD.matcher(s);
            if (m.find()) {
                String series = m.group(1).toUpperCase(java.util.Locale.ROOT); // RX
                String num = m.group(2);
                String sufRaw = m.group(3);
                String suffix = (sufRaw == null) ? "" : switch (sufRaw.toLowerCase(java.util.Locale.ROOT)) {
                    case "xtx" -> "XTX";
                    case "xt" -> "XT";
                    case "x" -> "X";
                    default -> "";
                };
                return Optional.of(new ParsedGpu(Vendor.AMD, series, num, suffix));
            }

            m = INTEL.matcher(s);
            if (m.find()) {
                return Optional.of(new ParsedGpu(Vendor.INTEL, "Arc", "A" + m.group(2), ""));
            }

            return Optional.empty();
        }
        catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String normalize(String in) {
        String s = in.toLowerCase(java.util.Locale.ROOT);
        // Schreibfehler-/Trennfixe
        s = s.replaceAll("ge\\s*force", "geforce");
        s = s.replaceAll("ra\\s*deon", "radeon");
        s = Arrays.stream(s.split("\\s+"))
                .filter(word -> {
                    if (word.toLowerCase().startsWith("s")) {
                        return word.equalsIgnoreCase("super");
                    }
                    return true;
                })
                .collect(Collectors.joining(" "));
        // alles andere so lassen – wir erlauben ohnehin „kein/optional“-Separator
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }
}
