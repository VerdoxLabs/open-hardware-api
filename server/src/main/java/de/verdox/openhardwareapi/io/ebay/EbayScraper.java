package de.verdox.openhardwareapi.io.ebay;

import de.verdox.openhardwareapi.configuration.DataStorage;
import de.verdox.openhardwareapi.io.api.Price;
import de.verdox.openhardwareapi.io.api.selenium.CookieJar;
import de.verdox.openhardwareapi.io.api.selenium.FScrapingCache;
import de.verdox.openhardwareapi.io.api.selenium.SeleniumBasedWebScraper;
import de.verdox.openhardwareapi.model.values.Currency;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EbayScraper {

    private final SeleniumBasedWebScraper seleniumBasedWebScraper;

    public EbayScraper(String id) {
        this.seleniumBasedWebScraper  = new SeleniumBasedWebScraper(id, new FScrapingCache(), new CookieJar(DataStorage.resolve("scraping")));
        seleniumBasedWebScraper.setIsChallengePage((s, doc) -> {

            if (doc.selectFirst("div.pgHeading") != null) {
                return true;
            }

            String titleTag = Optional.ofNullable(doc.title()).orElse("").toLowerCase(Locale.ROOT);
            return titleTag.contains("störung") || titleTag.contains("geprüft") || titleTag.contains("captcha");
        });
    }

    /**
     * Baut die eBay-Such-URL für verkaufte/beendete Angebote zu einer EAN (inkl. mkcid=2).
     */
    public static String buildUrl(EbayMarketplace marketplace, String ean, int page, int perPage) {
        String q = URLEncoder.encode(ean, StandardCharsets.UTF_8);
        int ipg = Math.max(10, Math.min(240, perPage));

        int pgn = Math.max(1, page);

        if (marketplace.equals(EbayMarketplace.GERMANY)) {
            return "https://www." + marketplace.getDomain() + "/sch/i.html"
                    + "?_nkw=" + q
                    + "&_sacat=0"
                    + "&LH_Complete=1"
                    + "&LH_Sold=1"
                    + "&LH_TitleDesc=1"
                    + "&LH_SellerType=1"
                    + "&_fslt=1"
                    + "&_ipg=" + ipg
                    + "&_pgn=" + pgn
                    + "&LH_PrefLoc=3"
                    + "&mkcid=2";
        } else {
            return "https://www." + marketplace.getDomain() + "/sch/i.html"
                    + "?_nkw=" + q
                    + "&_sacat=0"
                    + "&LH_Complete=1"
                    + "&LH_Sold=1"
                    + "&LH_TitleDesc=1"
                    + "&_fslt=1"
                    + "&_ipg=" + ipg
                    + "&_pgn=" + pgn
                    + "&mkcid=2";
        }
    }

    /**
     * Holt N Seiten (1..pages) und gibt die gesammelten Items zurück.
     */
    public List<EbaySoldItem> fetchByEan(EbayMarketplace marketplace, String ean, int pages) throws Exception {
        List<EbaySoldItem> out = new ArrayList<>();
        int maxPages = Math.max(1, Math.min(10, pages));

        for (int p = 1; p <= maxPages; p++) {
            String url = buildUrl(marketplace, ean, p, 240);

            Document doc = seleniumBasedWebScraper.fetch(marketplace.getDomain(), ean, url, Duration.ofDays(7));

            String titleTag = Optional.ofNullable(doc.title()).orElse("").toLowerCase(Locale.ROOT);
            if (titleTag.contains("störung") || titleTag.contains("geprüft") || titleTag.contains("captcha")) {
                throw new IllegalStateException("Bot-Challenge nicht passiert – kein Ergebnis-HTML.");
            }

            for (Element element : doc.select("li")) {
                String listingId = element.attr("data-listingid");
                var containerContent = element.select("div.su-card-container__content");
                var header = containerContent.select("div.su-card-container__header");
                var headerText = header.select("div.s-card__caption").select("span");
                var headerTitle = header.select("div.s-card__title").text();
                if (headerText.isEmpty()) {
                    continue;
                }
                String soldOn = headerText.getFirst().text();
                var titleText = header.select("div.s-card__title").select("span");

                if (!headerText.getFirst().hasClass("positive")) {
                    continue;
                }

                if (titleText.isEmpty()) {
                    continue;
                }
                var condition = header.select("div.s-card__subtitle-row").select("div.s-card__subtitle").select("span");
                List<String> subtitle = condition.stream().map(Element::text).toList();

                var body = containerContent.select("div.su-card-container__attributes");
                var attributeRows = body.select("div.su-card-container__attributes__primary").select("div.s-card__attribute-row");

                if (attributeRows.size() < 3) {
                    continue;
                }

                String priceText = attributeRows.get(0).text();
                String bidText = attributeRows.get(1).text();
                boolean bidOrBuyNow = bidText.contains("Gebot"); // TODO: Check if the string contains any number -> Bid Otherwise there would only be text

                int amountBids = 0;
                if (bidOrBuyNow) {
                    amountBids = extractBidCount(bidText);
                }

                LocalDate soldOnDate = parseSoldDate(soldOn, marketplace);

                EbaySoldItem ebaySoldItem = new EbaySoldItem(listingId, headerTitle, subtitle, parsePrice(marketplace, priceText), amountBids, soldOnDate);
                out.add(ebaySoldItem);
            }
        }
        return out;
    }


    /**
     * Robust genug für "12,34 €", "EUR 12,34", "£12.34", "US $12.34" etc.
     */
    private Price parsePrice(EbayMarketplace mkt, String raw) {
        if (raw == null) return null;
        String s = raw.replace("\u00A0", " ").trim(); // NBSP fix
        if (s.isBlank()) return null;

        // Währung bestimmen
        Currency currency = Currency.EURO; // Default
        if (s.contains("€") || s.toUpperCase(Locale.ROOT).contains("EUR")) {
            currency = Currency.EURO;
        } else if (s.contains("$") || s.toUpperCase(Locale.ROOT).contains("USD")) {
            currency = Currency.US_DOLLAR;
        } else if (s.contains("£") || s.toUpperCase(Locale.ROOT).contains("GBP")) {
            currency = Currency.UK_POUND;
        } else if (s.toUpperCase(Locale.ROOT).contains("AUD")) {
            currency = Currency.AUSTRALIAN_DOLLAR;
        } else if (s.toUpperCase(Locale.ROOT).contains("CAD")) {
            currency = Currency.CANADIAN_DOLLAR;
        } else if (s.toUpperCase(Locale.ROOT).contains("CHF")) {
            currency = Currency.SWISS_FRANKEN;
        } else if (s.toUpperCase(Locale.ROOT).contains("PLN")) {
            currency = Currency.POLAND_ZLOTY;
        }

        // Nur Zahlen, Komma, Punkt behalten
        String numPart = s.replaceAll("[^0-9,\\.]", "");
        if (numPart.isBlank()) return null;

        // Locale je Marktplatz


        BigDecimal value = null;
        try {
            Number n = mkt.getNumberFormat().parse(numPart);
            value = new BigDecimal(n.toString());
        } catch (ParseException e) {
            System.out.println("Could not parse " + numPart + " with " + mkt.getNumberFormat().getCurrency());
        }

        return new Price(value, currency);
    }


    /**
     * "Verkauft  3. Okt 2025" / "Sold 3 Oct 2025" / "Vendu 3 oct. 2025" → LocalDate
     */
    private LocalDate parseSoldDate(String caption, EbayMarketplace mkt) {
        if (caption == null || caption.isBlank()) return null;
        String c = caption.trim();

        // String normalisieren: nur Datumsanteil rausziehen
        // DE: "Verkauft  3. Okt 2025"
        // EN: "Sold 3 Oct 2025"
        Matcher m = Pattern.compile("(\\d{1,2}[^0-9]{1,3}[A-Za-zÄÖÜäöü.]{2,}\\s\\d{4})").matcher(c);
        String datePart = m.find() ? m.group(1) : c;

        return mkt.getEbayDateParser().parse(datePart);
    }

    /**
     * "1 Gebot", "12 Gebote", "0 bids" → int
     */
    private int extractBidCount(String bidsText) {
        if (bidsText == null) return 0;
        Matcher m = Pattern.compile("(\\d+)\\s*(Gebot|Gebote|bid|bids)", Pattern.CASE_INSENSITIVE).matcher(bidsText);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignore) {
            }
        }
        return 0;
    }
}
