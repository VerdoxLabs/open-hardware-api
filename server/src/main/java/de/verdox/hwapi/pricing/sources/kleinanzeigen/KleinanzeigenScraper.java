package de.verdox.hwapi.pricing.sources.kleinanzeigen;

import de.verdox.hwapi.catalog.ingestion.api.selenium.CookieJar;
import de.verdox.hwapi.catalog.ingestion.api.selenium.FScrapingCache;
import de.verdox.hwapi.catalog.ingestion.api.selenium.FetchOptions;
import de.verdox.hwapi.catalog.ingestion.api.selenium.SeleniumBasedWebScraper;
import de.verdox.hwapi.catalog.domain.values.Currency;
import de.verdox.hwapi.infrastructure.storage.DataStorage;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Selenium-Scraper für Kleinanzeigen (C2C-Kleinanzeigen).
 *
 * <p>Konstruktionsmuster 1:1 zu {@code EbayScraper}: ein {@link SeleniumBasedWebScraper}
 * mit File-Cache, Cookie-Jar und einem Challenge-Predicate (Kleinanzeigen hat
 * Bot-Checks). Das Geparste ist bewusst nur {@link KleinanzeigenAd} (Rohtatsachen) –
 * das Produkt-Matching macht die {@code KleinanzeigenPriceService}.
 *
 * <p><b>Selector-Basis:</b> Die CSS-Selektoren unten spiegeln das aktuelle
 * Kleinanzeigen-Listings-DOM (aktuell {@code article[data-adid]}, zuvor
 * {@code div.aditem}). Kleinanzeigen ändert das DOM regelmäßig – falls
 * ein Run leere Resultate liefert, zuerst hier prüfen. Das ist die eine
 * Stellschraube für das Scraping.
 */
public class KleinanzeigenScraper {

    private static final Logger LOGGER = Logger.getLogger(KleinanzeigenScraper.class.getSimpleName());

    public static final String DOMAIN = "www.kleinanzeigen.de";

    /** Europäischer Preis: "1.234,56" / "1.234" / "123". */
    private static final Pattern PRICE_NUMBER =
            Pattern.compile("(\\d{1,3}(?:\\.\\d{3})*(?:,\\d{1,2})?|\\d+(?:,\\d{1,2})?)");

    private final SeleniumBasedWebScraper scraper;

    public KleinanzeigenScraper(String id) {
        this.scraper = new SeleniumBasedWebScraper(
                id, new FScrapingCache(), new CookieJar(DataStorage.resolve("scraping")));
        scraper.setIsChallengePage((url, doc) -> {
            String t = Optional.ofNullable(doc.title()).orElse("").toLowerCase(Locale.ROOT);
            return t.contains("captcha") || t.contains("störung") || t.contains("geprüft")
                    || doc.selectFirst("div.challenge") != null;
        });
    }

    private static final String CATEGORY_PATH = "/s-pc-zubehoer-software/";
    private static final String CATEGORY_SUFFIX = "/k0c225";

    /**
     * Baut die kategorisierte Kleinanzeigen-Such-URL für PC-Zubehör/Software.
     * Das /s-{query}/k0.html-Routing liefert für diese Suche inzwischen HTTP 500;
     * die Kategorie-Route entspricht dem aktuellen Kleinanzeigen-Frontend.
     */
    public static String buildUrl(String query, int page) {
        String q = query == null ? "" : query.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{Alnum}]+", "-")
                .replaceAll("^-|-$", "");
        if (q.isBlank()) {
            q = "pc";
        }
        int p = Math.max(1, page);
        String pagination = p == 1 ? "" : "?page=" + p;
        return "https://" + DOMAIN + CATEGORY_PATH + q + CATEGORY_SUFFIX + pagination;
    }

    /**
     * Holt 1..max(10) Suchergebnisseiten für eine Query und sammelt alle Anzeigen.
     * Beendet die Schleife früh, wenn eine Seite nicht (mehr) parsbar ist.
     */
    public List<KleinanzeigenAd> fetch(String query, int pages) {
        List<KleinanzeigenAd> out = new ArrayList<>();
        int max = Math.max(1, Math.min(10, pages));

        for (int p = 1; p <= max; p++) {
            String url = buildUrl(query, p);
            Document doc;
            try {
                doc = scraper.fetch(DOMAIN, query, url,
                        new FetchOptions()
                                .setTryHeadlessFirst(true)
                                .setTtl(Duration.ofMinutes(10))
                                .setSkipIfNotCache(false));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Kleinanzeigen fetch fehlgeschlagen für " + url, e);
                break; // Challenge/Offline → nicht weiterdrehen
            }

            if (doc == null || doc.selectFirst("article[data-adid], div.aditem") == null) {
                continue; // Challenge-/leere Seite
            }

            List<KleinanzeigenAd> pageAds = parseAds(doc);
            out.addAll(pageAds);
            if (pageAds.isEmpty()) {
                break; // keine Karten mehr → Ende der Suchergebnisse
            }
        }
        return out;
    }

    /** Parst alle Anzeigen-Karten eines Listings-Dokuments. (Package-privat zum Testen.) */
    List<KleinanzeigenAd> parseAds(Document doc) {
        List<KleinanzeigenAd> out = new ArrayList<>();
        for (Element ad : doc.select("article[data-adid], div.aditem")) {
            try {
                KleinanzeigenAd parsed = parseOne(ad);
                if (parsed != null) {
                    out.add(parsed);
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Kleinanzeigen-Ad konnte nicht gepart werden", e);
            }
        }
        return out;
    }

    private KleinanzeigenAd parseOne(Element ad) {
        // The legacy server-rendered page used div.aditem/a.aditem-main. The current
        // Astro result page uses article[data-adid] and keeps the item URL in data-href.
        Element main = ad.selectFirst("a.aditem-main, h3 a[href], a[href]");

        String adId = ad.attr("data-adid");
        if (adId.isBlank() && main != null) {
            adId = main.attr("data-adid");
        }
        if (adId.isBlank()) {
            return null;
        }

        Element titleEl = ad.selectFirst("h3, h2");
        String title = (titleEl != null) ? titleEl.text() : "";
        if (title.isBlank() && main != null) {
            title = main.text();
        }
        if (title.isBlank()) {
            return null;
        }

        String url = !ad.absUrl("data-href").isBlank() ? ad.absUrl("data-href")
                : (main != null && !main.absUrl("href").isBlank())
                ? main.absUrl("href") : main != null ? main.attr("href") : "";

        Element img = (main != null) ? main.selectFirst("img") : ad.selectFirst("img");
        String imageUrl = (img != null && !img.absUrl("src").isBlank()) ? img.absUrl("src") : null;

        PriceHolder price = parsePrice(ad);

        return new KleinanzeigenAd(
                adId,
                title.trim(),
                price.price(),
                Currency.EURO,
                price.negotiable(),
                url,
                imageUrl
        );
    }

    /**
     * Kleinanzeigen zeigt den Preis in {@code span.price--basic} (bzw.
     * {@code span.price--emphasized}). "VB" = Verhandlungsbasis → kein Richtpreis.
     * Ein Preis mit "VB" dahinter (z. B. "123 € VB") wird als verhandelbar markiert.
     */
    private PriceHolder parsePrice(Element ad) {
        Element el = ad.selectFirst("span.price--basic");
        if (el == null) {
            el = ad.selectFirst("span.price--emphasized");
        }
        if (el == null) {
            // Current Astro cards render the price as a regular <p>, without a stable
            // semantic price class. The Euro marker keeps this fallback scoped to a price.
            el = ad.select("p").stream()
                    .filter(candidate -> candidate.text().contains("€"))
                    .findFirst()
                    .orElse(null);
        }
        String text = (el != null) ? el.text() : "";
        if (text.isBlank()) {
            return new PriceHolder(null, false);
        }

        boolean negotiable = text.toUpperCase(Locale.ROOT).contains("VB");

        Matcher m = PRICE_NUMBER.matcher(text);
        if (m.find()) {
            String num = m.group(1).replace(".", "").replace(",", ".");
            try {
                return new PriceHolder(new BigDecimal(num), negotiable);
            } catch (NumberFormatException ignore) {
                // unten auf null mappen
            }
        }
        return new PriceHolder(null, negotiable);
    }

    private record PriceHolder(BigDecimal price, boolean negotiable) {
    }
}
