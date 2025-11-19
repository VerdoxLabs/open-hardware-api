package de.verdox.hwapi.priceapi.io.ebay;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;

public interface EbayDateParser {

    LocalDate parse(String value);

    // === Hilfsmethoden für alle Parser ===

    static LocalDate parseWithFormats(String value, Locale locale, List<DateTimeFormatter> formatters) {
        String v = normalize(value);
        v = normalizeLocaleSpecific(v, locale);  // <<< NEU: lokalspezifische Fixups (Monats-Synonyme, kaputte Akzente etc.)
        for (DateTimeFormatter f : formatters) {
            try {
                return LocalDate.parse(v, f);
            } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("Unknown date format: \"" + value + "\" for locale " + locale);
    }

    /** Basale Normalisierung + Label-Stripping */
    private static String normalize(String s) {
        if (s == null) return null;

        String out = s.replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .trim()
                .replaceAll("\\s+", " ");

        // Führende Labels in mehreren Sprachen entfernen
        // + neu: "sprzedane" (PL)
        out = out.replaceFirst("(?iu)^(verkauft(?:\\s+am)?|sold|vendu\\s+le|vendu|vendido\\s+el|vendido|venduto\\s+il|venduto|verkocht|sprzedano|sprzedane)\\s+", "");

        // Füllwörter direkt nach dem Label entfernen (le/il/am/el)
        out = out.replaceFirst("(?iu)^(le|il|am|el)\\s+", "");

        // Englische Ordinals (12th → 12)
        out = out.replaceAll("(?i)\\b(\\d{1,2})(st|nd|rd|th)\\b", "$1");

        // Abschließende Kommas
        out = out.replaceAll("\\s*,\\s*$", "");

        // Unicode-Normalisierung hilft bei Akzenten (paź, août)
        out = java.text.Normalizer.normalize(out, java.text.Normalizer.Form.NFC);
        return out;
    }


    /** Locale-spezifische Reparaturen (Monats-Synonyme / Encoding-Havarien) */
    private static String normalizeLocaleSpecific(String s, Locale locale) {
        if (s == null || s.isBlank()) return s;

        String lang = locale.getLanguage().toLowerCase(Locale.ROOT);
        String out = s;

        // FR – häufige Encoding-Fixes
        out = out.replace("ao�t", "août")
                .replace("d�c", "déc")
                .replace("f�vr", "févr");

        if (lang.equals("it")) {
            // "sett." -> "set"; Punkte bei 3-Buchstaben-Formen entfernen
            out = out.replaceAll("(?i)\\bsett\\.", "set")
                    .replaceAll("(?i)\\b(gen|feb|mar|apr|mag|giu|lug|ago|set|ott|nov|dic)\\.", "$1");
        }

        if (lang.equals("es")) {
            // eBay/Verkäufer schreiben oft "sep" (statt "sept.")
            // Mappe mehrere inoffizielle Varianten sauber auf "sept."
            out = out.replaceAll("(?iu)\\bsep\\.?\\b", "sept.");
            // Falls jemand "set" (port./it. Einfluss) tippt, ebenso auf "sept."
            out = out.replaceAll("(?iu)\\bset\\.?\\b", "sept.");
            // Optional: Punkte bei gültigen 3-Buchstaben-Formen erlauben/entfernen
            // (Hier nicht nötig, da wir auf "sept." normalisieren)
        }

        // PL – nichts weiter nötig (NFC in normalize() reicht für „paź“)
        return out;
    }


    private static DateTimeFormatter pattern(String p, Locale locale) {
        return new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .parseLenient()
                .appendPattern(p)
                .toFormatter(locale)
                .withResolverStyle(ResolverStyle.SMART);
    }

    private static DateTimeFormatter germanDay_MMM_Year(Locale locale, boolean dotAfterAbbrev) {
        DateTimeFormatterBuilder b = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .parseLenient()
                .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
                .appendLiteral('.')
                .appendLiteral(' ')
                .appendText(ChronoField.MONTH_OF_YEAR);
        if (dotAfterAbbrev) b.optionalStart().appendLiteral('.').optionalEnd();
        b.appendLiteral(' ')
                .appendValue(ChronoField.YEAR, 4);
        return b.toFormatter(locale).withResolverStyle(ResolverStyle.SMART);
    }

    // === Unterklassen / Implementierungen ===

    // DE: plus englische Monat-Formate, da eBay gelegentlich mischt ("Verkauft Sep 17, 2025")
    EbayDateParser GERMANY = value -> parseWithFormats(value, Locale.GERMANY, List.of(
            germanDay_MMM_Year(Locale.GERMANY, false), // 12. Okt 2025
            germanDay_MMM_Year(Locale.GERMANY, true),  // 12. Okt. 2025
            pattern("d.M.uuuu", Locale.GERMANY),       // 12.10.2025
            pattern("dd.MM.uuuu", Locale.GERMANY),
            // cross-locale tolerances
            pattern("MMM d, uuuu", Locale.ENGLISH),    // Sep 17, 2025
            pattern("MMMM d, uuuu", Locale.ENGLISH),   // September 17, 2025
            pattern("d MMM uuuu", Locale.ENGLISH)      // 17 Sep 2025 (falls gemischt)
    ));

    EbayDateParser AUSTRIA = value -> parseWithFormats(value, new Locale("de", "AT"), List.of(
            germanDay_MMM_Year(new Locale("de", "AT"), false),
            germanDay_MMM_Year(new Locale("de", "AT"), true),
            pattern("d.M.uuuu", new Locale("de", "AT")),
            pattern("dd.MM.uuuu", new Locale("de", "AT")),
            // cross-locale tolerances (fix für "Verkauft Sep 17, 2025")
            pattern("MMM d, uuuu", Locale.ENGLISH),
            pattern("MMMM d, uuuu", Locale.ENGLISH),
            pattern("d MMM uuuu", Locale.ENGLISH)
    ));

    EbayDateParser SWITZERLAND = value -> parseWithFormats(value, new Locale("de", "CH"), List.of(
            germanDay_MMM_Year(new Locale("de", "CH"), false),
            germanDay_MMM_Year(new Locale("de", "CH"), true),
            pattern("d.M.uuuu", new Locale("de", "CH")),
            pattern("dd.MM.uuuu", new Locale("de", "CH")),
            // cross-locale tolerances
            pattern("MMM d, uuuu", Locale.ENGLISH),
            pattern("MMMM d, uuuu", Locale.ENGLISH),
            pattern("d MMM uuuu", Locale.ENGLISH)
    ));

    // US: zusätzlich "d MMM uuuu" (z.B. "17 Sep 2025")
    EbayDateParser USA = value -> parseWithFormats(value, Locale.US, List.of(
            pattern("MMM d, uuuu", Locale.US),   // Oct 12, 2025
            pattern("MMMM d, uuuu", Locale.US),  // October 12, 2025
            pattern("M/d/uuuu", Locale.US),      // 10/12/2025
            pattern("d MMM uuuu", Locale.ENGLISH),  // 17 Sep 2025
            pattern("d MMMM uuuu", Locale.ENGLISH)  // 17 September 2025
    ));

    // UK: bereits "d MMM uuuu"; belassen + extra Month-first zur Sicherheit
    EbayDateParser UK = value -> parseWithFormats(value, Locale.UK, List.of(
            pattern("d MMM uuuu", Locale.UK),
            pattern("dd MMM uuuu", Locale.UK),
            pattern("d MMMM uuuu", Locale.UK),
            pattern("dd MMMM uuuu", Locale.UK),
            pattern("dd/MM/uuuu", Locale.UK),     // 20/09/2025
            // toleranter EN-Fallback (falls Seite Month-first rendert)
            pattern("MMM d, uuuu", Locale.ENGLISH),
            pattern("MMMM d, uuuu", Locale.ENGLISH)
    ));

    // IE: inkl. Month-first (fix für "Sold Sep 17, 2025")
    EbayDateParser IRELAND = value -> parseWithFormats(value, new Locale("en", "IE"), List.of(
            pattern("d MMM uuuu", new Locale("en", "IE")),
            pattern("d MMMM uuuu", new Locale("en", "IE")),
            pattern("dd/MM/uuuu", new Locale("en", "IE")),
            pattern("MMM d, uuuu", Locale.ENGLISH),
            pattern("MMMM d, uuuu", Locale.ENGLISH)
    ));

    // FR: Prefix "Vendu le" wird in normalize entfernt; Encoding-Fixes (août) in normalizeLocaleSpecific
    EbayDateParser FRANCE = value -> parseWithFormats(value, Locale.FRANCE, List.of(
            pattern("d MMM uuuu", Locale.FRANCE),   // 12 oct. 2025
            pattern("d MMMM uuuu", Locale.FRANCE),  // 12 octobre 2025
            pattern("dd/MM/uuuu", Locale.FRANCE)
    ));

    // IT: "sett." → "set" in normalizeLocaleSpecific
    EbayDateParser ITALY = value -> parseWithFormats(value, Locale.ITALY, List.of(
            pattern("d MMM uuuu", Locale.ITALY),   // 12 ott 2025
            pattern("d MMMM uuuu", Locale.ITALY),  // 12 ottobre 2025
            pattern("dd/MM/uuuu", Locale.ITALY)
    ));

    // ES: "sep" → "sept." in normalizeLocaleSpecific
// SPAIN: ES-Formate + EN-Fallbacks (für gemischte Seiten)
    EbayDateParser SPAIN = value -> parseWithFormats(value, new Locale("es", "ES"), List.of(
            pattern("d MMM uuuu", new Locale("es", "ES")),   // 1 sept. 2025 (oder via normalize: 1 sep -> 1 sept.)
            pattern("d MMMM uuuu", new Locale("es", "ES")),  // 1 septiembre 2025
            pattern("dd/MM/uuuu", new Locale("es", "ES")),
            // Fallbacks für „Sep 1, 2025“ oder „1 Sep 2025“ mit englischer Monatsschreibweise
            pattern("d MMM uuuu", Locale.ENGLISH),           // 1 Sep 2025
            pattern("d MMMM uuuu", Locale.ENGLISH),          // 1 September 2025
            pattern("MMM d, uuuu", Locale.ENGLISH),          // Sep 1, 2025
            pattern("MMMM d, uuuu", Locale.ENGLISH)          // September 1, 2025
    ));

    // BE-FR: Unterstützt Mischform "Vendu le Sep 17, 2025"
    EbayDateParser BELGIUM_FR = value -> parseWithFormats(value, new Locale("fr", "BE"), List.of(
            pattern("d MMM uuuu", new Locale("fr", "BE")),
            pattern("d MMMM uuuu", new Locale("fr", "BE")),
            pattern("dd/MM/uuuu", new Locale("fr", "BE")),
            pattern("MMM d, uuuu", Locale.ENGLISH),
            pattern("MMMM d, uuuu", Locale.ENGLISH)
    ));

    EbayDateParser BELGIUM_NL = value -> parseWithFormats(value, new Locale("nl", "BE"), List.of(
            pattern("d MMM uuuu", new Locale("nl", "BE")),   // 12 okt. 2025
            pattern("d MMMM uuuu", new Locale("nl", "BE")),  // 12 oktober 2025
            pattern("dd-MM-uuuu", new Locale("nl", "BE")),
            // gelegentliche EN-Anzeigen
            pattern("MMM d, uuuu", Locale.ENGLISH),
            pattern("d MMM uuuu", Locale.ENGLISH)
    ));

    EbayDateParser NETHERLANDS = value -> parseWithFormats(value, new Locale("nl", "NL"), List.of(
            pattern("d MMM uuuu", new Locale("nl", "NL")),
            pattern("d MMMM uuuu", new Locale("nl", "NL")),
            pattern("dd-MM-uuuu", new Locale("nl", "NL"))
    ));

    EbayDateParser POLAND = value -> parseWithFormats(value, new Locale("pl", "PL"), List.of(
            pattern("d MMM uuuu", new Locale("pl", "PL")),
            pattern("d MMMM uuuu", new Locale("pl", "PL")),
            pattern("dd.MM.uuuu", new Locale("pl", "PL")),
            pattern("dd-MM-uuuu", new Locale("pl", "PL"))
    ));

    EbayDateParser AUSTRALIA = value -> parseWithFormats(value, new Locale("en", "AU"), List.of(
            pattern("d MMM uuuu", new Locale("en", "AU")),
            pattern("d MMMM uuuu", new Locale("en", "AU")),
            pattern("dd/MM/uuuu", new Locale("en", "AU")),
            pattern("MMM d, uuuu", Locale.ENGLISH),
            pattern("MMMM d, uuuu", Locale.ENGLISH)
    ));

    EbayDateParser HONGKONG = value -> parseWithFormats(value, new Locale("en", "HK"), List.of(
            pattern("d MMM uuuu", new Locale("en", "HK")),
            pattern("d MMMM uuuu", new Locale("en", "HK")),
            pattern("dd/MM/uuuu", new Locale("en", "HK")),
            pattern("MMM d, uuuu", Locale.ENGLISH),
            pattern("MMMM d, uuuu", Locale.ENGLISH)
    ));

    EbayDateParser SINGAPORE = value -> parseWithFormats(value, new Locale("en", "SG"), List.of(
            pattern("d MMM uuuu", new Locale("en", "SG")),
            pattern("d MMMM uuuu", new Locale("en", "SG")),
            pattern("dd/MM/uuuu", new Locale("en", "SG")),
            pattern("MMM d, uuuu", Locale.ENGLISH),
            pattern("MMMM d, uuuu", Locale.ENGLISH)
    ));
}
