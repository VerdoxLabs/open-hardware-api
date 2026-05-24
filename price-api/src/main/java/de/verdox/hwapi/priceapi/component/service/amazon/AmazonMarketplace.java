package de.verdox.hwapi.priceapi.component.service.amazon;

import de.verdox.hwapi.model.values.Currency;

public enum AmazonMarketplace {

    DE("webservices.amazon.de", "eu-west-1", "amazon.de", Currency.EURO),
    US("webservices.amazon.com", "us-east-1", "amazon.com", Currency.US_DOLLAR),
    UK("webservices.amazon.co.uk", "eu-west-1", "amazon.co.uk", Currency.UK_POUND),
    FR("webservices.amazon.fr", "eu-west-1", "amazon.fr", Currency.EURO),
    IT("webservices.amazon.it", "eu-west-1", "amazon.it", Currency.EURO),
    ES("webservices.amazon.es", "eu-west-1", "amazon.es", Currency.EURO);

    private final String host;
    private final String region;
    private final String domain;
    private final Currency defaultCurrency;

    AmazonMarketplace(String host, String region, String domain, Currency defaultCurrency) {
        this.host = host;
        this.region = region;
        this.domain = domain;
        this.defaultCurrency = defaultCurrency;
    }

    public String getHost() {
        return host;
    }

    public String getRegion() {
        return region;
    }

    public String getDomain() {
        return domain;
    }

    public Currency getDefaultCurrency() {
        return defaultCurrency;
    }
}
