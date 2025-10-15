package de.verdox.openhardwareapi.io.ebay;

import lombok.Getter;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

@Getter
public enum EbayMarketplace {
    GERMANY("ebay.de", "EBAY-DE", "EBAY_DE", "EUR", NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.GERMANY),
    AUSTRIA("ebay.at", "EBAY-AT", "EBAY_AT", "EUR", NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.AUSTRIA),
    SWITZERLAND("ebay.ch", "EBAY-CH", "EBAY_CH", "CHF", NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.SWITZERLAND),

    USA("ebay.com", "EBAY-US", "EBAY_US", "USD", NumberFormat.getNumberInstance(Locale.US), EbayDateParser.USA),
    CANADA_EN("ebay.ca", "EBAY-ENCA", "EBAY_CA", "CAD", NumberFormat.getNumberInstance(Locale.CANADA), EbayDateParser.USA),
    UK("ebay.co.uk", "EBAY-GB", "EBAY_GB", "GBP", NumberFormat.getNumberInstance(Locale.US), EbayDateParser.UK),
    IRELAND("ebay.ie", "EBAY-IE", "EBAY_IE", "EUR", NumberFormat.getNumberInstance(Locale.US), EbayDateParser.IRELAND),

    FRANCE("ebay.fr", "EBAY-FR", "EBAY_FR", "EUR", NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.FRANCE),
    ITALY("ebay.it", "EBAY-IT", "EBAY_IT", "EUR", NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.ITALY),
    SPAIN("ebay.es", "EBAY-ES", "EBAY_ES", "EUR", NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.SPAIN),

    BELGIUM_FR("befr.ebay.be", "EBAY-FRBE", "EBAY_BE", "EUR", NumberFormat.getNumberInstance(Locale.FRENCH), EbayDateParser.BELGIUM_FR),
    BELGIUM_NL("benl.ebay.be", "EBAY-NLBE", "EBAY_BE", "EUR", NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.BELGIUM_NL),
    NETHERLANDS("ebay.nl", "EBAY-NL", "EBAY_NL", "EUR", NumberFormat.getNumberInstance(Locale.US), EbayDateParser.NETHERLANDS),
    POLAND("ebay.pl", "EBAY-PL", "EBAY_PL", "PLN", NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.POLAND),

    AUSTRALIA("ebay.com.au", "EBAY-AU", "EBAY_AU", "AUD", NumberFormat.getNumberInstance(Locale.UK), EbayDateParser.AUSTRALIA),
    HONGKONG("ebay.com.hk", "EBAY-HK", "EBAY_HK", "HKD", NumberFormat.getNumberInstance(Locale.US), EbayDateParser.HONGKONG),
    SINGAPORE("ebay.com.sg", "EBAY-SG", "EBAY_SG", "SGD", NumberFormat.getNumberInstance(Locale.US), EbayDateParser.SINGAPORE),
    ;

    private final String domain;
    private final String findingGlobalId;
    private final String browseMarketplaceId;
    private final String currency;
    private final NumberFormat numberFormat;
    private final EbayDateParser ebayDateParser;

    EbayMarketplace(String domain, String findingGlobalId, String browseMarketplaceId, String currency, NumberFormat numberFormat, EbayDateParser ebayDateParser) {
        this.domain = domain;
        this.findingGlobalId = findingGlobalId;
        this.browseMarketplaceId = browseMarketplaceId;
        this.currency = currency;
        this.numberFormat = numberFormat;
        this.ebayDateParser = ebayDateParser;
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
