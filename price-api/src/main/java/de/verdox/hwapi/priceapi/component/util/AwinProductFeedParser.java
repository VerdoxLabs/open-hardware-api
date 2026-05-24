package de.verdox.hwapi.priceapi.component.util;

import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.priceapi.component.dto.AwinProductRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

public final class AwinProductFeedParser {

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()                // erste Zeile ist Header
            .setAutoFlush(true)
            .setSkipHeaderRecord(true)  // Header beim Iterieren überspringen
            .build();

    private AwinProductFeedParser() {
    }

    /**
     * Parst einen Awin-Produktfeed.
     * Der InputStream darf entweder:
     *  - GZIP-komprimiert (z.B. .csv.gz) oder
     *  - unkomprimierte CSV
     * sein. Das wird automatisch erkannt.
     */
    public static void parse(InputStream in, Consumer<AwinProductRecord> consumer) throws IOException {
        Objects.requireNonNull(in, "in must not be null");

        // mark/reset sicherstellen
        if (!in.markSupported()) {
            in = new BufferedInputStream(in);
        }

        // GZIP-Magic-Header (0x1f, 0x8b) prüfen
        in.mark(2);
        int b1 = in.read();
        int b2 = in.read();
        in.reset();

        boolean isGzip = (b1 == 0x1f && b2 == 0x8b);

        InputStream effectiveStream = isGzip ? new GZIPInputStream(in) : in;

        try (Reader reader = new InputStreamReader(effectiveStream, StandardCharsets.UTF_8)) {
            CSVParser records = CSV_FORMAT.parse(reader);
            try {
                for (CSVRecord record : records) {
                    if (record.size() == 0) {
                        continue;
                    }

                    AwinProductRecord product = mapRecord(record);
                    if (product != null) {
                        consumer.accept(product);
                    }
                }
            }
            finally {
                records.close();
            }
        }
    }

    /**
     * Mappt einen CSVRecord auf AwinProductRecord.
     * Spaltennamen basieren auf deiner AWIN-Feed-URL (columns=...).
     */
    private static AwinProductRecord mapRecord(CSVRecord record) {
        // Strings
        String awDeepLink        = getString(record, "aw_deep_link");
        String productName       = getString(record, "product_name");
        String brandName       = getString(record, "brand_name");
        String awProductId       = getString(record, "aw_product_id");
        String merchantProductId = getString(record, "merchant_product_id");
        String merchantImageUrl  = getString(record, "merchant_image_url");
        String description       = getString(record, "description");
        String merchantCategory  = getString(record, "merchant_category");
        String merchantName      = getString(record, "merchant_name");
        String categoryName      = getString(record, "category_name");
        String awImageUrl        = getString(record, "aw_image_url");
        String merchantDeepLink  = getString(record, "merchant_deep_link");
        String language          = getString(record, "language");
        String lastUpdated       = getString(record, "last_updated");
        String ean               = getString(record, "ean");
        String isbn              = getString(record, "isbn");
        String upc               = getString(record, "upc");
        String mpn               = getString(record, "mpn");
        String parentProductId   = getString(record, "parent_product_id");
        String productGtin       = getString(record, "product_GTIN");

        // Numerische Felder
        BigDecimal searchPrice   = getBigDecimal(record, "search_price");
        BigDecimal storePrice    = getBigDecimal(record, "store_price");
        BigDecimal deliveryCost  = getBigDecimal(record, "delivery_cost");
        BigDecimal averageRating = getBigDecimal(record, "average_rating");

        long merchantId          = getLong(record, "merchant_id");
        long categoryId          = getLong(record, "category_id");
        long dataFeedId          = getLong(record, "data_feed_id");

        // Currency
        Currency currency        = getCurrency(record, "currency");


        if(currency == null && merchantName != null && (merchantName.contains("DE") || merchantName.contains("AT"))) {
            currency = Currency.EURO;
        }

        // DisplayPrice (nutzt store_price + currency; display_price-String ignorieren wir erstmal)
        AwinProductRecord.DisplayPrice displayPrice = buildDisplayPrice(storePrice, currency);

        // Minimal-Check: komplett leere Produkte rausfiltern
        if (awDeepLink == null && productName == null && awProductId == null && merchantProductId == null) {
            return null;
        }

        return AwinProductRecord.builder()
                .awDeepLink(awDeepLink)
                .productName(productName)
                .awProductId(awProductId)
                .manufacturerName(brandName)
                .merchantProductId(merchantProductId)
                .merchantImageUrl(merchantImageUrl)
                .description(description)
                .merchantCategory(merchantCategory)
                .searchPrice(searchPrice)
                .merchantName(merchantName)
                .merchantId(merchantId)
                .categoryName(categoryName)
                .categoryId(categoryId)
                .awImageUrl(awImageUrl)
                .currency(currency)
                .storePrice(storePrice)
                .deliveryCost(deliveryCost)
                .merchantDeepLink(merchantDeepLink)
                .language(language)
                .lastUpdated(lastUpdated)
                .ean(ean)
                .isbn(isbn)
                .upc(upc)
                .mpn(mpn)
                .parentProductId(parentProductId)
                .displayPrice(displayPrice)
                .dataFeedId(dataFeedId)
                .productGtin(productGtin)
                .averageRating(averageRating)
                .build();
    }

    // ===== Helper-Methoden =====

    private static String getString(CSVRecord record, String column) {
        try {
            String value = record.get(column);
            if (value == null) {
                return null;
            }
            value = value.trim();
            return value.isEmpty() ? null : value;
        } catch (IllegalArgumentException e) {
            // Spalte nicht vorhanden
            return null;
        }
    }

    private static BigDecimal getBigDecimal(CSVRecord record, String column) {
        String value = getString(record, column);
        if (value == null) {
            return null;
        }
        try {
            // AWIN benutzt normalerweise "." als Dezimaltrennzeichen,
            // falls doch mal "," auftaucht, ersetzen wir es.
            return new BigDecimal(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long getLong(CSVRecord record, String column) {
        String value = getString(record, column);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static Currency getCurrency(CSVRecord record, String column) {
        String code = getString(record, column);
        if (code == null) {
            return null;
        }
        try {
            return Currency.findCurrency(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static AwinProductRecord.DisplayPrice buildDisplayPrice(BigDecimal storePrice, Currency currency) {
        if (storePrice == null || currency == null) {
            return null;
        }
        return new AwinProductRecord.DisplayPrice(storePrice, currency);
    }
}
