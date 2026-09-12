package de.verdox.hwapi.priceapi.io.kleinanzeigen;

import de.verdox.hwapi.hardwareapi.scraping.api.selenium.CookieJar;
import de.verdox.hwapi.hardwareapi.scraping.api.selenium.FScrapingCache;
import de.verdox.hwapi.hardwareapi.scraping.api.selenium.FetchOptions;
import de.verdox.hwapi.hardwareapi.scraping.api.selenium.SeleniumBasedWebScraper;
import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.util.DataStorage;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
 * Kleinanzeigen-Listings-DOM ({@code div.aditem}, {@code a.aditem-main},
 * {@code span.price--basic} …). Kleinanzeigen ändert das DOM regelmäßig – falls
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

    /**
     * Baut die Kleinanzeigen-Such-URL für eine Freitext-Query (Kategorie "alle", k0).
     * {@code px} = Seiten-Parameter (1-basiert), {@code an=on} = nur aktive Anzeigen.
     */
    public static String buildUrl(String query, int page) {
        String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
        int p = Math.max(1, page);
        return "https://" + DOMAIN + "/s-" + q + "/k0.html?an=on&px=" + p;
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

            if (doc == null || doc.selectFirst("div.aditem") == null) {
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
        for (Element ad : doc.select("div.aditem")) {
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
        Element main = ad.selectFirst("a.aditem-main");

        String adId = ad.attr("data-adid");
        if (adId.isBlank() && main != null) {
            adId = main.attr("data-adid");
        }
        if (adId.isBlank()) {
            return null;
        }

        Element titleEl = (main != null) ? main.selectFirst("h2") : ad.selectFirst("h2");
        String title = (titleEl != null) ? titleEl.text() : "";
        if (title.isBlank()) {
            return null;
        }

        String url = (main != null && !main.absUrl("href").isBlank())
                ? main.absUrl("href")
                : main != null ? main.attr("href") : "";

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
