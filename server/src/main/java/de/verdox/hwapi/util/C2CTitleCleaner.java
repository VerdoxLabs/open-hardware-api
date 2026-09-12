package de.verdox.hwapi.util;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Entfernt typisches C2C-Rauschen (Kleinanzeigen &amp; Co.) aus einem rohen Inseratstitel,
 * <b>bevor</b> er gegen die Product-Identity-Registry gematcht wird.
 *
 * <p>Es werden ausschließlich eindeutige Verkaufs-, Zustands- und Marketingbegriffe
 * entfernt – niemals Bestandteile von Modell-/Spezifikationen. Daher bewusst als
 * eigenständiger Pre-Cleaner und <em>nicht</em> in der kanonischen
 * {@code ProductRegistryService.normalizeTitle} eingebaut, die die Identitäts-Keys
 * aller Quellen definiert und stabil bleiben soll.
 *
 * <p>Wichtiger Konsistenz-Punkt: Der Cleaner muss sowohl bei der C2C-Suche
 * ({@code searchAggregatedC2C}) als auch bei der Registrierung <em>bestätigter</em>
 * C2C-Titel angewendet werden. Nur dann bleiben die exakten {@code TITLE_NORMALIZED}
 * Treffer der Lernschleife konsistent (gleiche Eingabe → gleiche normalisierte Form).
 */
public final class C2CTitleCleaner {

    private C2CTitleCleaner() {
    }

    /**
     * Mehrwort-Phrasen – werden vor den Einzelwörtern entfernt, damit keine
     * Teilstrings übrig bleiben (z.&nbsp;B. "wie neu" vor "neu").
     */
    private static final List<Pattern> PHRASES = List.of(
            Pattern.compile("wie neu", Pattern.CASE_INSENSITIVE),
            Pattern.compile("neu in folie", Pattern.CASE_INSENSITIVE),
            Pattern.compile("neueingebaut", Pattern.CASE_INSENSITIVE),
            Pattern.compile("kaum benutzt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("kaum gebraucht", Pattern.CASE_INSENSITIVE),
            Pattern.compile("kaum verwendet", Pattern.CASE_INSENSITIVE),
            Pattern.compile("sehr guter zustand", Pattern.CASE_INSENSITIVE),
            Pattern.compile("guter zustand", Pattern.CASE_INSENSITIVE),
            Pattern.compile("top zustand", Pattern.CASE_INSENSITIVE),
            Pattern.compile("keine reklamation", Pattern.CASE_INSENSITIVE),
            Pattern.compile("keine rückerstattung", Pattern.CASE_INSENSITIVE),
            Pattern.compile("keine garantie", Pattern.CASE_INSENSITIVE),
            Pattern.compile("zzgl\\.? versand", Pattern.CASE_INSENSITIVE),
            Pattern.compile("versand möglich", Pattern.CASE_INSENSITIVE),
            Pattern.compile("nur abholung", Pattern.CASE_INSENSITIVE),
            Pattern.compile("zu verkaufen", Pattern.CASE_INSENSITIVE),
            Pattern.compile("in originalverpackung", Pattern.CASE_INSENSITIVE),
            Pattern.compile("originalverpackung", Pattern.CASE_INSENSITIVE)
    );

    /** Einzelne Rausch-Wörter (word-boundary, damit Modellnummern nicht angerührt werden). */
    private static final List<Pattern> STOPWORDS = List.of(
            Pattern.compile("\\bvb\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bfestpreis\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\babzugeben\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bprivat(kauf|verkauf)?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bovp\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btop(angebot|preis|zustand)?\\b", Pattern.CASE_INSENSITIVE),
            // "super" ist bewusst KEIN Stopword: "RTX 4070 Super" ist ein Modell-Suffix.
            Pattern.compile("\\bpreis\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bbiete\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bangebot\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bgünstig\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\beinmalig\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bunfassbar\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bneuwertig\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bneuware\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bunbenutzt\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bgebraucht\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bdefekt\\w*\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bgebrauchsspuren\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\beinwandfrei\\b", Pattern.CASE_INSENSITIVE),
            // Zustands-Beschreibungen (inkl. Deklinationen: guter/gutem/gutes) – C2C-typisch.
            Pattern.compile("\\bzustand\\w*\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bgut\\w*\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bsehr\\b", Pattern.CASE_INSENSITIVE),
            // Funktionswörter, die in C2C-Titeln vorkommen, aber nie Teil einer Modellnummer sind.
            Pattern.compile("\\bin\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bkauf\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bverkauf\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bversandkostenfrei\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bversand\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bportofrei\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bkostenlos\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bneu\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bschnell\\b", Pattern.CASE_INSENSITIVE)
    );

    private static final Pattern PARENS = Pattern.compile("\\([^)]*\\)");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Bereinigt einen rohen C2C-Titel. Ergebnis ist klein geschrieben, von Rauschbegriffen
     * befreit und auf alphanumerische Tokens + einzelne Leerzeichen normiert.
     */
    public static String clean(String title) {
        if (title == null) {
            return null;
        }
        String s = title.toLowerCase(Locale.ROOT);
        s = PARENS.matcher(s).replaceAll(" ");
        for (Pattern p : PHRASES) {
            s = p.matcher(s).replaceAll(" ");
        }
        for (Pattern p : STOPWORDS) {
            s = p.matcher(s).replaceAll(" ");
        }
        s = NON_ALNUM.matcher(s).replaceAll(" ");
        s = WHITESPACE.matcher(s).replaceAll(" ").trim();
        return s;
    }
}
