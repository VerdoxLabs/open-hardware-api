package de.verdox.hwapi.integration.client.ebay;

import de.verdox.hwapi.catalog.domain.values.Currency;
import de.verdox.hwapi.pricing.model.ListingEnums;
import lombok.Getter;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

@Getter
public enum EbayMarketplace {
    GERMANY("ebay.de", "EBAY-DE", ListingEnums.Country.DE, "EBAY_DE", Currency.EURO, NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.GERMANY),
    AUSTRIA("ebay.at", "EBAY-AT", ListingEnums.Country.AT, "EBAY_AT", Currency.EURO, NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.AUSTRIA),
    SWITZERLAND("ebay.ch", "EBAY-CH", ListingEnums.Country.CH, "EBAY_CH", Currency.SWISS_FRANKEN, NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.SWITZERLAND),

    USA("ebay.com", "EBAY-US", ListingEnums.Country.US, "EBAY_US", Currency.US_DOLLAR, NumberFormat.getNumberInstance(Locale.US), EbayDateParser.USA),
    CANADA_EN("ebay.ca", "EBAY-ENCA", ListingEnums.Country.CA, "EBAY_CA", Currency.CANADIAN_DOLLAR, NumberFormat.getNumberInstance(Locale.CANADA), EbayDateParser.USA),
    UK("ebay.co.uk", "EBAY-GB", ListingEnums.Country.GB, "EBAY_GB", Currency.UK_POUND, NumberFormat.getNumberInstance(Locale.US), EbayDateParser.UK),
    IRELAND("ebay.ie", "EBAY-IE", ListingEnums.Country.IE, "EBAY_IE", Currency.EURO, NumberFormat.getNumberInstance(Locale.US), EbayDateParser.IRELAND),

    FRANCE("ebay.fr", "EBAY-FR", ListingEnums.Country.FR, "EBAY_FR", Currency.EURO, NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.FRANCE),
    ITALY("ebay.it", "EBAY-IT", ListingEnums.Country.IT, "EBAY_IT", Currency.EURO, NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.ITALY),
    SPAIN("ebay.es", "EBAY-ES", ListingEnums.Country.ES, "EBAY_ES", Currency.EURO, NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.SPAIN),

    BELGIUM_FR("befr.ebay.be", "EBAY-FRBE", ListingEnums.Country.BE, "EBAY_BE", Currency.EURO, NumberFormat.getNumberInstance(Locale.FRENCH), EbayDateParser.BELGIUM_FR),
    BELGIUM_NL("benl.ebay.be", "EBAY-NLBE", ListingEnums.Country.BE, "EBAY_BE", Currency.EURO, NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.BELGIUM_NL),
    NETHERLANDS("ebay.nl", "EBAY-NL", ListingEnums.Country.NL, "EBAY_NL", Currency.EURO, NumberFormat.getNumberInstance(Locale.US), EbayDateParser.NETHERLANDS),
    POLAND("ebay.pl", "EBAY-PL", ListingEnums.Country.PL, "EBAY_PL", Currency.POLAND_ZLOTY, NumberFormat.getNumberInstance(Locale.GERMANY), EbayDateParser.POLAND),

    AUSTRALIA("ebay.com.au", "EBAY-AU", ListingEnums.Country.AU, "EBAY_AU", Currency.AUSTRALIAN_DOLLAR, NumberFormat.getNumberInstance(Locale.UK), EbayDateParser.AUSTRALIA),
    ;

    private final String domain;
    private final String findingGlobalId;
    private final ListingEnums.Country country;
    private final String browseMarketplaceId;
    private final Currency currency;
    private final NumberFormat numberFormat;
    private final EbayDateParser ebayDateParser;

    EbayMarketplace(String domain, String findingGlobalId, ListingEnums.Country country, String browseMarketplaceId, Currency currency, NumberFormat numberFormat, EbayDateParser ebayDateParser) {
        this.domain = domain;
        this.findingGlobalId = findingGlobalId;
        this.country = country;
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
