package de.verdox.openhardwareapi.io.ebay;

import de.verdox.openhardwareapi.util.SeleniumUtil;
import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EbayScraper {

    @Getter
    public enum EbayMarketplace {
        GERMANY("ebay.de", "EBAY-DE", "EBAY_DE", "EUR", NumberFormat.getCurrencyInstance(Locale.GERMANY)),
        AUSTRIA("ebay.at", "EBAY-AT", "EBAY_AT", "EUR", NumberFormat.getCurrencyInstance(Locale.GERMANY)),
        SWITZERLAND("ebay.ch", "EBAY-CH", "EBAY_CH", "CHF", NumberFormat.getCurrencyInstance(Locale.GERMANY)),

        USA("ebay.com", "EBAY-US", "EBAY_US", "USD", NumberFormat.getCurrencyInstance(Locale.US)),
        CANADA_EN("ebay.ca", "EBAY-ENCA", "EBAY_CA", "CAD", NumberFormat.getCurrencyInstance(Locale.CANADA)),
        CANADA_FR("c.ebay.ca", "EBAY-FRCA", "EBAY_CA", "CAD", NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)),

        UK("ebay.co.uk", "EBAY-GB", "EBAY_GB", "GBP", NumberFormat.getCurrencyInstance(Locale.US)),
        IRELAND("ebay.ie", "EBAY-IE", "EBAY_IE", "EUR", NumberFormat.getCurrencyInstance(Locale.US)),

        FRANCE("ebay.fr", "EBAY-FR", "EBAY_FR", "EUR", NumberFormat.getCurrencyInstance(Locale.GERMANY)),
        ITALY("ebay.it", "EBAY-IT", "EBAY_IT", "EUR", NumberFormat.getCurrencyInstance(Locale.GERMANY)),
        SPAIN("ebay.es", "EBAY-ES", "EBAY_ES", "EUR", NumberFormat.getCurrencyInstance(Locale.GERMANY)),

        BELGIUM_FR("befr.ebay.be", "EBAY-FRBE", "EBAY_BE", "EUR", NumberFormat.getCurrencyInstance(Locale.FRENCH)),
        BELGIUM_NL("benl.ebay.be", "EBAY-NLBE", "EBAY_BE", "EUR", NumberFormat.getCurrencyInstance(Locale.GERMANY)),
        NETHERLANDS("ebay.nl", "EBAY-NL", "EBAY_NL", "EUR", NumberFormat.getCurrencyInstance(Locale.US)),
        POLAND("ebay.pl", "EBAY-PL", "EBAY_PL", "PLN", NumberFormat.getCurrencyInstance(Locale.GERMANY)),

        AUSTRALIA("ebay.com.au", "EBAY-AU", "EBAY_AU", "AUD", NumberFormat.getCurrencyInstance(Locale.UK)),
        HONGKONG("ebay.com.hk", "EBAY-HK", "EBAY_HK", "HKD", NumberFormat.getCurrencyInstance(Locale.US)),
        SINGAPORE("ebay.com.sg", "EBAY-SG", "EBAY_SG", "SGD", NumberFormat.getCurrencyInstance(Locale.US));

        private final String domain;
        private final String findingGlobalId;
        private final String browseMarketplaceId;
        private final String currency;
        private final NumberFormat numberFormat;

        EbayMarketplace(String domain, String findingGlobalId, String browseMarketplaceId, String currency, NumberFormat numberFormat) {
            this.domain = domain;
            this.findingGlobalId = findingGlobalId;
            this.browseMarketplaceId = browseMarketplaceId;
            this.currency = currency;
            this.numberFormat = numberFormat;
        }

        /**
         * Suche enum anhand Domain
         */
        public static Optional<EbayMarketplace> fromDomain(String domain) {
            return Arrays.stream(values())
                    .filter(m -> m.domain.equalsIgnoreCase(domain))
                    .findFirst();
        }

        /**
         * Suche enum anhand Finding Global-ID
         */
        public static Optional<EbayMarketplace> fromFindingId(String globalId) {
            return Arrays.stream(values())
                    .filter(m -> m.findingGlobalId.equalsIgnoreCase(globalId))
                    .findFirst();
        }

        /**
         * Suche enum anhand Browse Marketplace-ID
         */
        public static Optional<EbayMarketplace> fromBrowseId(String browseId) {
            return Arrays.stream(values())
                    .filter(m -> m.browseMarketplaceId.equalsIgnoreCase(browseId))
                    .findFirst();
        }
    }


    public record SoldItem(
            String itemId,
            String title,
            List<String> condition,          // "Gebraucht", ...
            Price price,
            Integer bids,              // 1 (optional, null bei Sofort-Kauf)
            LocalDate soldDate         // 2025-10-03 (aus "Verkauft  3. Okt 2025")
    ) {
    }

    /**
     * Baut die eBay-Such-URL für verkaufte/beendete Angebote zu einer EAN (inkl. mkcid=2).
     */
    public static String buildUrl(EbayMarketplace marketplace, String ean, int page, int perPage) {
        String q = URLEncoder.encode(ean, StandardCharsets.UTF_8);
        int ipg = Math.max(10, Math.min(240, perPage));

        int pgn = Math.max(1, page);
        return "https://www." + marketplace.domain + "/sch/i.html"
                + "?_nkw=" + q
                + "&_sacat=0"
                + "&LH_Complete=1"
                + "&LH_Sold=1"
                + "LH_TitleDesc=1"
                + "LH_SellerType=1"
                + "_fslt=1"
                + "&_ipg=" + ipg
                + "&_pgn=" + pgn
                + "&mkcid=2";
    }

    /**
     * Holt N Seiten (1..pages) und gibt die gesammelten Items zurück.
     */
    public static List<SoldItem> fetchByEan(EbayMarketplace marketplace, String ean, int pages) throws Exception {
        List<SoldItem> out = new ArrayList<>();
        int maxPages = Math.max(1, Math.min(10, pages)); // Safety

        // --- Selenium Headless Chrome ---
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                /*"--headless=new",*/
                "--disable-gpu",
                "--no-sandbox",
                "--window-size=1400,1000",
                "--lang=de-DE",
                "--disable-blink-features=AutomationControlled"
        );
        // etwas weniger „botty“
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        WebDriver driver = SeleniumUtil.create(options);
        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(45));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // kleine Helpers lokal
        java.util.function.Predicate<WebDriver> isChallenge = d -> {
            String t = Optional.ofNullable(d.getTitle()).orElse("").toLowerCase(Locale.ROOT);
            return t.contains("störung") || t.contains("geprüft") || t.contains("bitte warten") || t.contains("captcha");
        };
        ExpectedCondition<Boolean> docReady = d ->
                "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState"));

        Runnable politePause = () -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {
            }
        };

        java.util.function.Consumer<WebDriver> dismissConsents = d -> {
            try {
                List<By> candidates = List.of(
                        By.cssSelector("button#gdpr-banner-accept"),
                        By.cssSelector("button[aria-label*='Alle akzeptieren']"),
                        By.xpath("//button[contains(.,'Alle akzeptieren')]"),
                        By.cssSelector("button[class*='accept'], button[class*='consent']")
                );
                for (By by : candidates) {
                    var els = d.findElements(by);
                    if (!els.isEmpty() && els.get(0).isDisplayed()) {
                        els.get(0).click();
                        break;
                    }
                }
            } catch (Exception ignore) {
            }
        };

        try {
            for (int p = 1; p <= maxPages; p++) {
                String url = buildUrl(marketplace, ean, p, 240);
                driver.get(url);

                // Dokument fertig laden
                wait.until(docReady);

                // Challenge abwarten (max 30s)
                long until = System.currentTimeMillis() + 30000;
                while (System.currentTimeMillis() < until && isChallenge.test(driver)) {
                    Thread.sleep(1000);
                }

                // Cookie/Consent wegklicken (falls sichtbar)
                dismissConsents.accept(driver);

                // Auf Ergebnis-Container warten (robuster als nur li.s-item)
                By[] resultRoots = new By[]{
                        By.cssSelector("#srp-river-results"),
                        By.cssSelector("ul.srp-results"),
                        By.cssSelector("div.s-item__wrapper"),
                        By.cssSelector("li.s-item")
                };
                boolean found = false;
                for (By by : resultRoots) {
                    try {
                        wait.until(ExpectedConditions.presenceOfElementLocated(by));
                        found = true;
                        break;
                    } catch (TimeoutException ignore) {
                    }
                }

                // Falls noch nichts: scroll + kurzer Retry + Refresh-Fallback
                if (!found) {
                    try {
                        js.executeScript("window.scrollTo(0, document.body.scrollHeight/2)");
                    } catch (Exception ignore) {
                    }
                    politePause.run();
                    try {
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("li.s-item")));
                        found = true;
                    } catch (TimeoutException te) {
                        driver.navigate().refresh();
                        wait.until(docReady);
                        dismissConsents.accept(driver);
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("li.s-item")));
                        found = true;
                    }
                }

                // HTML holen und mit Jsoup parsen
                String html = driver.getPageSource();
                Document doc = Jsoup.parse(html, driver.getCurrentUrl());

                // Safety: Challenge nochmal prüfen
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
                        //TODO: Extract bid amount from bidText
                        amountBids = extractBidCount(bidText);
                    }

                    LocalDate soldOnDate = parseSoldDate(soldOn, marketplace); // TODO: Text like "Sold 3 Oct 2025". But on different languages! Try to parse it!

                    SoldItem soldItem = new SoldItem(listingId, headerTitle, subtitle, parsePrice(marketplace, priceText), amountBids, soldOnDate);
                    out.add(soldItem);
                }
                // höfliches Crawling
                politePause.run();
            }
            return out;
        } finally {
            try {
                //driver.quit();
            } catch (Exception ignore) {
            }
        }
    }


    private static class Price {
        BigDecimal value;
        String currency;

        @Override
        public String toString() {
            return "Price{" +
                    "value=" + value +
                    ", currency='" + currency + '\'' +
                    '}';
        }
    }

    /**
     * Robust genug für "12,34 €", "EUR 12,34", "£12.34", "US $12.34" etc.
     */
    private static Price parsePrice(EbayMarketplace mkt, String raw) {
        if (raw == null) return null;
        String s = raw.replace("\u00A0", " ").trim(); // NBSP fix
        if (s.isBlank()) return null;

        // Währung bestimmen
        String currency = "EUR"; // Default
        if (s.contains("€") || s.toUpperCase(Locale.ROOT).contains("EUR")) {
            currency = "EUR";
        } else if (s.contains("$") || s.toUpperCase(Locale.ROOT).contains("USD")) {
            currency = "USD";
        } else if (s.contains("£") || s.toUpperCase(Locale.ROOT).contains("GBP")) {
            currency = "GBP";
        } else if (s.toUpperCase(Locale.ROOT).contains("AUD")) {
            currency = "AUD";
        } else if (s.toUpperCase(Locale.ROOT).contains("CAD")) {
            currency = "CAD";
        } else if (s.toUpperCase(Locale.ROOT).contains("CHF")) {
            currency = "CHF";
        } else if (s.toUpperCase(Locale.ROOT).contains("PLN")) {
            currency = "PLN";
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
            System.out.println("Could not parse " + numPart+" with "+mkt.getNumberFormat().getCurrency());
            e.printStackTrace();
        }

        Price p = new Price();
        p.value = value;
        p.currency = currency;
        return p;
    }



    /**
     * "Verkauft  3. Okt 2025" / "Sold 3 Oct 2025" / "Vendu 3 oct. 2025" → LocalDate
     */
    private static LocalDate parseSoldDate(String caption, EbayMarketplace mkt) {
        if (caption == null || caption.isBlank()) return null;
        String c = caption.trim();

        // Sprache grob nach Marktplatz
        Locale loc = switch (mkt) {
            case USA, CANADA_EN -> Locale.US;
            case UK -> Locale.UK;
            case FRANCE, BELGIUM_FR -> Locale.FRANCE;
            case ITALY -> Locale.ITALY;
            case SPAIN -> new Locale("es", "ES");
            case NETHERLANDS, BELGIUM_NL -> new Locale("nl", "NL");
            case POLAND -> new Locale("pl", "PL");
            default -> Locale.GERMANY;
        };

        // String normalisieren: nur Datumsanteil rausziehen
        // DE: "Verkauft  3. Okt 2025"
        // EN: "Sold 3 Oct 2025"
        Matcher m = Pattern.compile("(\\d{1,2}[^0-9]{1,3}[A-Za-zÄÖÜäöü.]{2,}\\s\\d{4})").matcher(c);
        String datePart = m.find() ? m.group(1) : c;

        System.out.println(datePart);

        // Kandidaten-Formate (mit und ohne Punkt nach Monat)
        String[] patterns = {
                "d. MMM yyyy", "d MMM yyyy", "d. MMMM yyyy", "d MMMM yyyy"
        };
        for (String p : patterns) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(p, loc);
                return LocalDate.parse(datePart.replace("  ", " ").trim(), fmt);
            } catch (Exception ignore) {
            }
        }
        return null; // not fatal
    }

    /**
     * "1 Gebot", "12 Gebote", "0 bids" → int
     */
    private static int extractBidCount(String bidsText) {
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

    /* ----------------- Demo ----------------- */

    public static void main(String[] args) throws Exception {
        String ean = (args.length > 0) ? args[0] : "0730143309202";
        List<SoldItem> items = fetchByEan(EbayMarketplace.UK, ean, 1); // zwei Seiten á 240 Ergebnisse

        for (SoldItem item : items) {
            System.out.println(item);
        }
        System.out.println("Total: " + items.size());
    }
}
