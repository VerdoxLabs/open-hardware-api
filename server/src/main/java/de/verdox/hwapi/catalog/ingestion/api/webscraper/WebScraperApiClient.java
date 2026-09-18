package de.verdox.hwapi.catalog.ingestion.api.webscraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** HTTP client for the Webscraper API raw-HTML integration endpoint. */
public final class WebScraperApiClient {

    private final URI rawEndpoint;
    private final String apiKey;
    private final String engine;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WebScraperApiClient(String baseUrl, String apiKey, String engine, Duration timeout,
                               ObjectMapper objectMapper) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.rawEndpoint = URI.create(normalizedBaseUrl).resolve("v1/raw");
        this.apiKey = apiKey;
        this.engine = engine;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public String fetchHtml(String url) {
        try {
            String requestUrl = normalizeUrl(url);
            boolean pcPartPickerCatalog = isPcPartPickerCatalog(requestUrl);
            boolean akamaiProtectedSite = isEbay(requestUrl);
            boolean kleinanzeigenSite = isKleinanzeigen(requestUrl);
            ObjectNode body = objectMapper.createObjectNode()
                    .put("url", requestUrl)
                    // PCPartPicker's catalog rows are populated after the initial
                    // document response; a static/auto shell has an empty tbody.
                    // Kleinanzeigen returns HTTP 500 to the static client. Force the real
                    // browser path so the configured Camoufox websocket is used directly.
                    .put("engine", pcPartPickerCatalog ? "js" : akamaiProtectedSite ? "akamai" :
                    kleinanzeigenSite ? "cloudflare" : engine);
            if (pcPartPickerCatalog) {
                body.put("waitForSelector", "#category_content tr.tr__product");
            }
            HttpRequest.Builder request = HttpRequest.newBuilder(rawEndpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    // Successful responses are raw HTML, but the API returns structured JSON
                    // for scrape failures. Accept both so Spring can negotiate the error body
                    // instead of falling through to its Whitelabel 500 page.
                    .header("Accept", "text/html, application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body),
                            StandardCharsets.UTF_8));
            if (apiKey != null && !apiKey.isBlank()) {
                request.header("X-API-Key", apiKey);
            }

            HttpResponse<String> response = httpClient.send(request.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String responseBody = response.body() == null ? "" : response.body();
                if (responseBody.length() > 500) {
                    responseBody = responseBody.substring(0, 500);
                }
                throw new WebScraperApiException(response.statusCode(), "Webscraper API returned HTTP "
                        + response.statusCode() + ": " + responseBody);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebScraperApiException("Webscraper API request was interrupted", e);
        } catch (WebScraperApiException e) {
            throw e;
        } catch (Exception e) {
            throw new WebScraperApiException("Could not fetch " + url + " via Webscraper API", e);
        }
    }

    /**
     * Catalog scrapers can return product names as part of the href. Those hrefs
     * occasionally contain spaces or Unicode characters, while the downstream
     * scraper expects a valid URL. Encode the URL once at the API boundary.
     */
    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        try {
            // UriComponentsBuilder knows URL component boundaries, unlike
            // URLEncoder (which is only for form/query values). It preserves an
            // existing scheme, path delimiters and percent escapes while encoding
            // unsafe product-title characters such as spaces, pipes and Unicode.
            return UriComponentsBuilder.fromUriString(url).build().encode().toUriString();
        } catch (IllegalArgumentException ignored) {
            // Preserve the original value so the API error still contains the
            // source URL when a scraper returns a malformed href.
            return url;
        }
    }

    private static boolean isPcPartPickerCatalog(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() != null && uri.getHost().endsWith("pcpartpicker.com")
                    && uri.getPath().startsWith("/products/");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isKleinanzeigen(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null && (host.equalsIgnoreCase("kleinanzeigen.de")
                    || host.endsWith(".kleinanzeigen.de"));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isEbay(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null && (host.equalsIgnoreCase("ebay.de")
                    || host.endsWith(".ebay.de")
                    || host.equalsIgnoreCase("ebay.com")
                    || host.endsWith(".ebay.com")
                    || host.equalsIgnoreCase("ebay.at")
                    || host.endsWith(".ebay.at")
                    || host.equalsIgnoreCase("ebay.ch")
                    || host.endsWith(".ebay.ch")
                    || host.equalsIgnoreCase("ebay.co.uk")
                    || host.endsWith(".ebay.co.uk")
                    || host.equalsIgnoreCase("ebay.ie")
                    || host.endsWith(".ebay.ie")
                    || host.equalsIgnoreCase("ebay.fr")
                    || host.endsWith(".ebay.fr")
                    || host.equalsIgnoreCase("ebay.it")
                    || host.endsWith(".ebay.it")
                    || host.equalsIgnoreCase("ebay.es")
                    || host.endsWith(".ebay.es")
                    || host.equalsIgnoreCase("ebay.be")
                    || host.endsWith(".ebay.be")
                    || host.equalsIgnoreCase("ebay.nl")
                    || host.endsWith(".ebay.nl")
                    || host.equalsIgnoreCase("ebay.pl")
                    || host.endsWith(".ebay.pl")
                    || host.equalsIgnoreCase("ebay.com.au")
                    || host.endsWith(".ebay.com.au")
                    || host.equalsIgnoreCase("ebay.ca")
                    || host.endsWith(".ebay.ca"));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static class WebScraperApiException extends RuntimeException {
        private final Integer statusCode;

        public WebScraperApiException(String message) {
            super(message);
            this.statusCode = null;
        }

        public WebScraperApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public WebScraperApiException(String message, Throwable cause) {
            super(message, cause);
            this.statusCode = null;
        }

        /** HTTP status returned by the scraper service, if a response was received. */
        public Integer statusCode() {
            return statusCode;
        }
    }
}
