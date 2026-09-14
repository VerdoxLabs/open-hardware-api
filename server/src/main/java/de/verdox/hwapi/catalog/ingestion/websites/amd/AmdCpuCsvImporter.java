package de.verdox.hwapi.catalog.ingestion.websites.amd;

import de.verdox.hwapi.catalog.ingestion.ScrapingService;
import de.verdox.hwapi.catalog.domain.CPU;
import de.verdox.hwapi.catalog.domain.HardwareSpec;
import de.verdox.hwapi.catalog.domain.HardwareTypes;


import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class AmdCpuCsvImporter {

    private static final String COL_NAME = "Name";
    private static final String COL_CORES = "# of CPU Cores";
    private static final String COL_THREADS = "# of Threads";
    private static final String COL_BASE_CLOCK = "Base Clock";
    private static final String COL_BOOST_CLOCK = "Max. Boost Clock";
    private static final String COL_L3 = "L3 Cache";
    private static final String COL_TDP = "Default TDP";
    private static final String COL_SOCKET = "CPU Socket";
    private static final String COL_GRAPHICS_MODEL = "Graphics Model";
    private static final String COL_LAUNCH_DATE = "Launch Date";
    private static final String COL_MPN_BOXED = "Product ID Boxed";
    private static final String COL_MPN_TRAY = "Product ID Tray";
    private static final String COL_MPN_MPK = "Product ID MPK";

    private static final DateTimeFormatter AMD_DATE =
            DateTimeFormatter.ofPattern("M/d/yyyy");

    private static final Pattern CLOCK_PATTERN =
            Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*(ghz|mhz)", Pattern.CASE_INSENSITIVE);

    private static final Pattern POWER_PATTERN =
            Pattern.compile("([0-9]+(?:[.,][0-9]+)?)");

    private static final Pattern CACHE_MB_PATTERN =
            Pattern.compile("([0-9]+)");

    private AmdCpuCsvImporter() {}

    // ========= NEW: Reader import =========
    public static Set<CPU> importFrom(Reader reader) throws IOException {
        CSVParser parser = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(reader);
        return parser.stream().parallel().map(AmdCpuCsvImporter::mapRecord).filter(Objects::nonNull).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toSet());
    }

    // ========= CSV-Record zu CPU =========
    private static final Pattern AMD_MPN_PATTERN =
            Pattern.compile("^(\\d{3}-\\d{6,12})(BOX|TRAY|MPK|AWOF|WOF|SBX)?$", Pattern.CASE_INSENSITIVE);

    private static boolean isValidAmdMpn(String s) {
        if (s == null) return false;
        return AMD_MPN_PATTERN.matcher(s.trim()).matches();
    }

    private static String extractAmdMpnBase(String mpn) {
        if (mpn == null) return null;
        Matcher m = AMD_MPN_PATTERN.matcher(mpn.trim());
        if (!m.matches()) return null;
        return m.group(1); // z.B. "100-000001595"
    }

    private static List<String> buildAmdMpnVariants(String anyValidMpn) {
        String base = extractAmdMpnBase(anyValidMpn);
        if (base == null) return Collections.emptyList();

        // gewünschte Varianten: Basis + BOX/TRAY/MPK sowie Basis ohne Suffix
        return List.of(
                base,
                base + "BOX",
                base + "AWOF",
                base + "WOF",
                base + "TRAY",
                base + "SBX",
                base + "MPK"
        );
    }

    private static Optional<CPU> mapRecord(CSVRecord r) {
        try {
            String rawName = trim(getCol(r, COL_NAME));
            if (rawName == null)
                return Optional.empty();

            String mpnBoxed = trim(getCol(r, COL_MPN_BOXED));
            String mpnTray  = trim(getCol(r, COL_MPN_TRAY));
            String mpnMpk   = trim(getCol(r, COL_MPN_MPK));

            // Skip rule: keine MPN vorhanden → keine CPU
            if (mpnBoxed == null && mpnTray == null && mpnMpk == null)
                return Optional.empty();

            CPU cpu = new CPU();
            cpu.setManufacturer("AMD");
            cpu.setModel(normalizeModel(rawName, "AMD"));

            // 1) Alles was im CSV steht erstmal übernehmen
            if (mpnBoxed != null) cpu.getMPNs().add(HardwareSpec.normalizeMpn(mpnBoxed));
            if (mpnTray  != null) cpu.getMPNs().add(HardwareSpec.normalizeMpn(mpnTray));
            if (mpnMpk   != null) cpu.getMPNs().add(HardwareSpec.normalizeMpn(mpnMpk));

            // 2) Irgendeine valide MPN finden (boxed -> tray -> mpk -> sonstige aus set)
            String seed =
                    isValidAmdMpn(mpnBoxed) ? mpnBoxed :
                            isValidAmdMpn(mpnTray)  ? mpnTray  :
                                    isValidAmdMpn(mpnMpk)   ? mpnMpk   :
                                            cpu.getMPNs().stream()
                                                    .filter(Objects::nonNull)
                                                    .map(String::trim).filter(s -> !s.isBlank())
                                                    .filter(AmdCpuCsvImporter::isValidAmdMpn)
                                                    .findFirst().orElse(null);

            // 3) Wenn valide gefunden -> Varianten erzeugen und hinzufügen
            if (seed != null) {
                for (String v : buildAmdMpnVariants(seed)) {
                    cpu.getMPNs().add(HardwareSpec.normalizeMpn(v));
                }
            }

            cpu.setSocket(parseSocket(trim(getCol(r, COL_SOCKET))));
            cpu.setCores(parseInt(getCol(r, COL_CORES)));
            cpu.setThreads(parseInt(getCol(r, COL_THREADS)));

            cpu.setPerformanceCores(cpu.getCores());
            cpu.setEfficiencyCores(0);

            double base = parseClock(trim(getCol(r, COL_BASE_CLOCK)));
            cpu.setBaseClockMhz(base);
            cpu.setBaseClockMhzPerformance(base);

            cpu.setBoostClockMhz(parseClock(trim(getCol(r, COL_BOOST_CLOCK))));
            cpu.setL3CacheMb(parseInt(getCol(r, COL_L3)));
            cpu.setTdpWatts(parseInt(getCol(r, COL_TDP)));

            String gfx = trim(getCol(r, COL_GRAPHICS_MODEL));
            cpu.setIntegratedGraphics(
                    gfx == null || gfx.equalsIgnoreCase("Discrete Graphics Card Required")
                            ? "Not available"
                            : stripTokens(gfx)
            );

            String launch = trim(getCol(r, COL_LAUNCH_DATE));
            if (launch != null) {
                LocalDate ld = parseDate(launch);
                if (ld != null) {
                    try { cpu.setLaunchDate(ld); } catch (Throwable ignored) {}
                }
            }
            cpu.checkIfLegal();
            return Optional.of(cpu);
        }
        catch (Throwable e){
            ScrapingService.LOGGER.log(Level.SEVERE, "Failed to parse CSV record", e);
            return Optional.empty();
        }
    }

    // ========= Parsing Helpers =========

    private static String trim(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static int parseInt(String raw) {
        if (raw == null) return 0;
        raw = raw.replaceAll("[^0-9.]", "");
        try { return (int) Double.parseDouble(raw); }
        catch (Exception e) { return 0; }
    }

    private static double parseClock(String raw) {
        if (raw == null) return 0;
        Matcher m = CLOCK_PATTERN.matcher(raw);
        if (!m.find()) return 0;

        double val = Double.parseDouble(m.group(1).replace(",", "."));
        boolean isGHz = m.group(2).toLowerCase().contains("ghz");
        return isGHz ? val * 1000 : val;
    }

    private static LocalDate parseDate(String raw) {
        try { return LocalDate.parse(raw, AMD_DATE); }
        catch (DateTimeParseException e) { return null; }
    }

    private static String stripTokens(String s) {
        return s.replace("™", "").replace("®", "").replaceAll("\\s+", " ").trim();
    }

    private static String normalizeModel(String name, String manufacturer) {
        name = stripTokens(name);

        if (manufacturer.equalsIgnoreCase("AMD"))
            name = name.replaceFirst("(?i)^AMD\\s+", "");

        if (manufacturer.equalsIgnoreCase("Intel"))
            name = name.replaceFirst("(?i)^Intel\\s+", "");

        name = name.replace("Processor", "");

        return name.trim().replaceAll("\\s+", " ");
    }

    private static HardwareTypes.CpuSocket parseSocket(String raw) {
        if (raw == null) return HardwareTypes.CpuSocket.UNKNOWN;
        raw = raw.toUpperCase(Locale.ROOT).replace(" ", "");
        try { return HardwareTypes.CpuSocket.valueOf(raw); }
        catch (Exception e) { return HardwareTypes.CpuSocket.UNKNOWN; }
    }

    private static String getCol(CSVRecord r, String logicalName) {
        if (r.isMapped(logicalName)) {
            return r.get(logicalName);
        }

        Map<String, String> map = r.toMap();
        for (String rawHeader : map.keySet()) {
            String cleaned = cleanHeader(rawHeader);
            if (cleaned.equals(logicalName)) {
                return map.get(rawHeader);
            }
        }
        return null;
    }

    private static String cleanHeader(String header) {
        if (header == null) return null;
        return header
                .replace("\uFEFF", "")   // Byte-Order-Mark (BOM) am Anfang
                .replace("\"", "")       // umschließende Anführungszeichen
                .trim();
    }

}
