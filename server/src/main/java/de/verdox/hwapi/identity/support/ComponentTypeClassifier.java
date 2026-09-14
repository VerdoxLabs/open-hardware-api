package de.verdox.hwapi.identity.support;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Leichte, katalog-unabhängige Klassifizierung eines (normalisierten) Produkttitels
 * auf eine Hardware-Komponentenart.
 *
 * <p>Nur Zeichenketten-Logik (Regex-Signale), <b>keine</b> Abhängigkeit zu
 * {@code HardwareSpecService} oder dem Katalog. Damit kann die
 * Product-Identity-Registry beim Fuzzy-Matching nach Komponentenart "gaten",
 * ohne an den Katalog gekoppelt zu werden.
 *
 * <p>Bewusst konservativ: erst wenn eindeutige, spezifische Signale vorhanden sind,
 * wird ein konkreter Typ gemeldet. Liegt kein klares Signal vor, wird
 * {@link ComponentKind#UNKNOWN} bzw. {@link ComponentKind#OTHER} zurückgegeben –
 * beides wirkt beim Gating <em>nicht</em> filternd (siehe {@link ComponentKind#isComponentType()}).
 *
 * <p>Die Logik ist eine Heuristik, kein perfekter Parser: bei Mehrfach-Signalen
 * (z.&nbsp;B. ein Bundle "Gaming PC i7 + RTX 4070") gewinnt der beste einzelne Treffer,
 * und ein explizites "PC"-Signal macht den Titel durchlässig (Bundle → kein Gating).
 */
public final class ComponentTypeClassifier {

    private ComponentTypeClassifier() {
    }

    /** Grobe Komponentenart, abgeleitet aus dem Titeltext. */
    public enum ComponentKind {
        CPU, GPU, RAM, STORAGE, MOTHERBOARD, PSU, CASE, COOLER, FAN, DISPLAY,
        PC, OTHER, UNKNOWN;

        /**
         * Nur diese Typen dürfen beim Matching als Gating-Filter wirken.
         * PC/OTHER/UNKNOWN bleiben durchlässig – ein PC-Bundle referenziert viele Teile
         * und soll nicht wegen eines Teil-Signals andere Komponenten ausschließen.
         */
        public boolean isComponentType() {
            switch (this) {
                case CPU:
                case GPU:
                case RAM:
                case STORAGE:
                case MOTHERBOARD:
                case PSU:
                case CASE:
                case COOLER:
                case FAN:
                case DISPLAY:
                    return true;
                default:
                    return false;
            }
        }
    }

    // --- Signale pro Typ (lowercase; auf dem bereits klein-geschriebenen Titel gematcht) ---

    private static final List<Pattern> GPU = List.of(
            Pattern.compile("grafikkarte"),
            Pattern.compile("graphics ?card"),
            Pattern.compile("geforce"),
            Pattern.compile("radeon"),
            Pattern.compile("\\brtx\\s?\\d{3,4}"),
            Pattern.compile("\\brtx\\b"),
            Pattern.compile("\\bgtx\\b"),
            Pattern.compile("\\brx\\s?\\d{3,4}"),
            Pattern.compile("quadro"),
            Pattern.compile("firepro"),
            Pattern.compile("amdgpu")
    );

    private static final List<Pattern> CPU = List.of(
            Pattern.compile("\\bryzen\\b"),
            Pattern.compile("threadripper"),
            Pattern.compile("core ?i[0-9]"),
            Pattern.compile("\\bi[3579]\\s?\\d{3,4}"),
            Pattern.compile("\\bcpu\\b"),
            Pattern.compile("processor"),
            Pattern.compile("celeron"),
            Pattern.compile("pentium"),
            Pattern.compile("xeon"),
            Pattern.compile("epyc"),
            Pattern.compile("phenom"),
            Pattern.compile("athlon")
    );

    private static final List<Pattern> RAM = List.of(
            Pattern.compile("\\bram\\b"),
            Pattern.compile("\\bmemor(?:y|ie)\\b"),
            Pattern.compile("arbeitspeicher"),
            Pattern.compile("ddr[2345]"),
            Pattern.compile("so-?dimm"),
            Pattern.compile("e-?dimm"),
            Pattern.compile("\\bdimm\\b"),
            Pattern.compile("r\\.?dimm"),
            Pattern.compile("\\bhbx\\b")
    );

    private static final List<Pattern> STORAGE = List.of(
            Pattern.compile("\\bssd\\b"),
            Pattern.compile("\\bhdd\\b"),
            Pattern.compile("nvme"),
            Pattern.compile("sata"),
            Pattern.compile("m\\.?2"),
            Pattern.compile("festplatte"),
            Pattern.compile("hard ?disk"),
            Pattern.compile("\\bstorage\\b"),
            Pattern.compile("\\b\\d+(\\.\\d+)?\\s?tb\\b")
    );

    private static final List<Pattern> MOTHERBOARD = List.of(
            Pattern.compile("mainboard"),
            Pattern.compile("motherboard"),
            Pattern.compile("\\bsocket\\b"),
            Pattern.compile("lga\\s?\\d{3,4}"),
            Pattern.compile("\\bam[345]\\b"),
            Pattern.compile("\\bz\\d{2,3}\\b"),
            Pattern.compile("\\bb\\d{2,3}\\b"),
            Pattern.compile("\\bx\\d{2,3}\\b"),
            Pattern.compile("\\ba\\d{2,3}\\b"),
            Pattern.compile("\\bchipset\\b")
    );

    private static final List<Pattern> PSU = List.of(
            Pattern.compile("netzteil"),
            Pattern.compile("power supply"),
            Pattern.compile("\\bpsu\\b"),
            Pattern.compile("\\b\\d{3,4}\\s?w\\b"),
            Pattern.compile("\\bwatt\\b"),
            Pattern.compile("\\batx\\b")
    );

    private static final List<Pattern> CASE = List.of(
            Pattern.compile("gehäuse"),
            Pattern.compile("\\bcase\\b"),
            Pattern.compile("mini-?itx"),
            Pattern.compile("mid-?tower"),
            Pattern.compile("full-?tower"),
            Pattern.compile("open ?frame")
    );

    private static final List<Pattern> COOLER = List.of(
            Pattern.compile("kühler"),
            Pattern.compile("cooler"),
            Pattern.compile("wasserkühlung"),
            Pattern.compile("\\baio\\b"),
            Pattern.compile("radiator"),
            Pattern.compile("kraken"),
            Pattern.compile("heat ?pipe")
    );

    private static final List<Pattern> FAN = List.of(
            Pattern.compile("lüfter"),
            Pattern.compile("\\bfan\\b"),
            Pattern.compile("rgb fan"),
            Pattern.compile("pwm fan"),
            Pattern.compile("case fan")
    );

    private static final List<Pattern> DISPLAY = List.of(
            Pattern.compile("\\bmonitor\\b"),
            Pattern.compile("\\bdisplay\\b"),
            Pattern.compile("\\b\\d{1,2}\\s?zoll\\b"),
            Pattern.compile("\\b\\d{1,2}\\s?inch\\b"),
            Pattern.compile("\\b4k\\b"),
            Pattern.compile("\\b144hz\\b"),
            Pattern.compile("uhd"),
            Pattern.compile("qhd"),
            Pattern.compile("wqhd"),
            Pattern.compile("curved")
    );

    private static final List<Pattern> PC = List.of(
            Pattern.compile("\\bpc\\b"),
            Pattern.compile("\\bcomputer\\b"),
            Pattern.compile("\\brechner\\b"),
            Pattern.compile("pc-?set"),
            Pattern.compile("\\bbundle\\b"),
            Pattern.compile("\\brig\\b")
    );

    /**
     * Klassifiziert einen Titel. Eingabe wird klein geschrieben; leere Eingabe → UNKNOWN.
     */
    public static ComponentKind classify(String title) {
        if (title == null || title.isBlank()) {
            return ComponentKind.UNKNOWN;
        }
        String t = title.toLowerCase(Locale.ROOT);

        // Explizites "PC"-Signal → ganzes System → durchlässig (kein Gating).
        if (countSignals(t, PC) > 0) {
            return ComponentKind.PC;
        }

        int[] scores = {
                countSignals(t, GPU),
                countSignals(t, CPU),
                countSignals(t, RAM),
                countSignals(t, STORAGE),
                countSignals(t, MOTHERBOARD),
                countSignals(t, PSU),
                countSignals(t, CASE),
                countSignals(t, COOLER),
                countSignals(t, FAN),
                countSignals(t, DISPLAY)
        };
        ComponentKind[] kinds = {
                ComponentKind.GPU,
                ComponentKind.CPU,
                ComponentKind.RAM,
                ComponentKind.STORAGE,
                ComponentKind.MOTHERBOARD,
                ComponentKind.PSU,
                ComponentKind.CASE,
                ComponentKind.COOLER,
                ComponentKind.FAN,
                ComponentKind.DISPLAY
        };

        ComponentKind best = ComponentKind.OTHER;
        int bestScore = 0;
        for (int i = 0; i < scores.length; i++) {
            // striktes ">": bei Unentschieden gewinnt der früher geprüfte Typ
            // (GPU, CPU zuerst) – das ist bei "Ryzen 5800X AM4" erwünscht (CPU vor Mainboard).
            if (scores[i] > bestScore) {
                bestScore = scores[i];
                best = kinds[i];
            }
        }
        return best;
    }

    /** Zählt, wie viele verschiedene Signale der Liste im Text vorkommen. */
    private static int countSignals(String t, List<Pattern> signals) {
        int n = 0;
        for (Pattern p : signals) {
            if (p.matcher(t).find()) {
                n++;
            }
        }
        return n;
    }
}
