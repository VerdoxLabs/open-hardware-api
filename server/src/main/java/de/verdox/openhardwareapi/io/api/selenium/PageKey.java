package de.verdox.openhardwareapi.io.api.selenium;

public record PageKey(String domain, String id, String url) {
    public PageKey {
        if (domain == null || domain.isBlank())
            throw new IllegalArgumentException("domain must not be null/blank");
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id must not be null/blank");
        if (url == null || url.isBlank())
            throw new IllegalArgumentException("url must not be null/blank");
        if (domain.contains("/") || domain.contains(".."))
            throw new IllegalArgumentException("invalid domain: " + domain);
    }
}
