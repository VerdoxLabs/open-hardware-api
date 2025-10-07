package de.verdox.openhardwareapi.io.barcode;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BarcodeLookupScraper {

    // Bei Bedarf anpassen / rotieren
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final int TIMEOUT_MS = (int) Duration.ofSeconds(20).toMillis();

    // Einfaches Check: nur 8–18 Ziffern; EAN/UPC liegen i. d. R. in diesem Bereich
    private static final Pattern BARCODE_ALLOWED = Pattern.compile("^\\d{8,18}$");

    // „Barcode Formats: UPC-A 818253371989, EAN-13 0818253371989“
    private static final Pattern UPC_EXTRACT = Pattern.compile("(?:UPC|UPC-A)\\s*(\\d+)");
    private static final Pattern EAN_EXTRACT = Pattern.compile("(?:EAN|EAN-13)\\s*(\\d+)");

    public static Optional<ProductInfo> fetch(String barcode) throws IOException {
        String cleaned = sanitizeBarcode(barcode);
        if (cleaned == null) {
            throw new IllegalArgumentException("Ungültiger Barcode: " + barcode);
        }

        String url = "https://www.barcodelookup.com/" + cleaned;

        Connection conn = Jsoup
                .connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .referrer("https://www.google.com")
                .ignoreHttpErrors(true);

        Connection.Response resp = conn.execute();
        int status = resp.statusCode();
        if (status == 404) {
            return Optional.empty();
        }

        // Manche 200-Seiten sind „not found“-Seiten im Content. Also DOM prüfen.
        Document doc = resp.parse();

        Element details = doc.selectFirst("div.col-50.product-details");
        if (details == null) {
            // Fallback: erkennbarer „no results“-Text?
            if (doc.text().toLowerCase().contains("no product results")
                    || doc.title().toLowerCase().contains("not found")
                    || doc.selectFirst("#edit-product-btn") == null) {
                return Optional.empty();
            }
            // Wenn die Struktur anders ist, versuchen wir’s dennoch nicht weiter.
            return Optional.empty();
        }

        ProductInfo info = new ProductInfo();
        info.url = url;
        info.inputBarcode = cleaned;

        // <h1> enthält oft „UPC 818253371989“
        Element h1 = details.selectFirst("h1");
        if (h1 != null) {
            info.h1 = h1.text().trim();
        }

        // <h4> = Produktname
        Element h4 = details.selectFirst("h4");
        if (h4 != null) {
            info.title = h4.text().trim();
        }

        // Key/Value-Blöcke: <div class="product-text-label">Label: <span class="product-text">Value</span></div>
        Elements metaRows = details.select("div.product-text-label");
        for (Element row : metaRows) {
            String label = row.ownText().replace(":", "").trim();
            String value = row.selectFirst(".product-text") != null
                    ? row.selectFirst(".product-text").text().trim()
                    : "";

            switch (label.toLowerCase()) {
                case "barcode formats" -> {
                    info.barcodeFormatsRaw = value;
                    // UPC/EAN aus dem Rohtext parsen
                    Matcher m1 = UPC_EXTRACT.matcher(value);
                    if (m1.find()) info.upc = m1.group(1);
                    Matcher m2 = EAN_EXTRACT.matcher(value);
                    if (m2.find()) info.ean13 = m2.group(1);
                }
                case "category" -> info.category = value;
                case "manufacturer" -> info.manufacturer = value;
                default -> {
                    // Alles weitere mitnehmen (z. B. gelegentlich andere Felder)
                    info.extraMeta.append(label).append("=").append(value).append("\n");
                }
            }
        }

        // Falls UPC/EAN nicht aus Barcode Formats extrahiert werden konnten, noch heuristisch aus <h1>
        if (isBlank(info.upc) && info.h1 != null) {
            Matcher m = UPC_EXTRACT.matcher(info.h1);
            if (m.find()) info.upc = m.group(1);
        }

        // „Gefunden“ definieren wir als: Es gibt einen Titel oder irgendein Meta
        boolean hasSomething = !isBlank(info.title) || !isBlank(info.manufacturer)
                || !isBlank(info.category) || !isBlank(info.upc) || !isBlank(info.ean13);
        return hasSomething ? Optional.of(info) : Optional.empty();
    }

    private static String sanitizeBarcode(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D+", "");
        if (!BARCODE_ALLOWED.matcher(digits).matches()) return null;
        return digits;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // --- Simple DTO ---
    public static class ProductInfo {
        public String url;
        public String inputBarcode;

        public String h1;                 // Rohtext aus <h1> (z. B. "UPC 818253371989")
        public String title;              // <h4> Produktname
        public String upc;                // aus "Barcode Formats" oder H1 extrahiert
        public String ean13;              // aus "Barcode Formats" extrahiert
        public String category;           // "Electronics Accessories > …"
        public String manufacturer;       // "Amd"
        public String barcodeFormatsRaw;  // kompletter Rohtext des Felds
        public StringBuilder extraMeta = new StringBuilder();

        @Override
        public String toString() {
            return "ProductInfo{" +
                    "url='" + url + '\'' +
                    ", inputBarcode='" + inputBarcode + '\'' +
                    ", h1='" + h1 + '\'' +
                    ", title='" + title + '\'' +
                    ", upc='" + upc + '\'' +
                    ", ean13='" + ean13 + '\'' +
                    ", category='" + category + '\'' +
                    ", manufacturer='" + manufacturer + '\'' +
                    ", barcodeFormatsRaw='" + barcodeFormatsRaw + '\'' +
                    ", extraMeta=\n" + extraMeta +
                    '}';
        }
    }

    // --- Mini-Demo ---
    public static void main(String[] args) {
        try {
            Optional<ProductInfo> res = fetch("818253371989");
            System.out.println(res.isPresent() ? res.get() : "Kein Eintrag gefunden");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

