package de.verdox.hwapi.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/** Copyright/source information retained for an image mirrored by the catalog. */
@Embeddable
public class ProductImageAttribution {

    @Column(name = "local_url", nullable = false, length = 1024)
    private String localUrl;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "source_page_url", nullable = false, length = 2048)
    private String sourcePageUrl;

    protected ProductImageAttribution() {
        // JPA
    }

    public ProductImageAttribution(String localUrl, String originalUrl, String sourcePageUrl) {
        this.localUrl = localUrl;
        this.originalUrl = originalUrl;
        this.sourcePageUrl = sourcePageUrl;
    }

    public String getLocalUrl() { return localUrl; }
    public String getOriginalUrl() { return originalUrl; }
    public String getSourcePageUrl() { return sourcePageUrl; }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProductImageAttribution that && Objects.equals(localUrl, that.localUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localUrl);
    }
}
