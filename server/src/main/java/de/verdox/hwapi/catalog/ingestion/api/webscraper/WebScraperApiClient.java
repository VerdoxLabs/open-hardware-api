package de.verdox.hwapi.catalog.ingestion.api.webscraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
            boolean pcPartPickerCatalog = isPcPartPickerCatalog(url);
            boolean akamaiProtectedSite = isEbay(url);
            ObjectNode body = objectMapper.createObjectNode()
                    .put("url", url)
                    // PCPartPicker's catalog rows are populated after the initial
                    // document response; a static/auto shell has an empty tbody.
                    .put("engine", pcPartPickerCatalog ? "js" : akamaiProtectedSite ? "akamai" : engine);
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
                throw new WebScraperApiException("Webscraper API returned HTTP "
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

    private static boolean isPcPartPickerCatalog(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() != null && uri.getHost().endsWith("pcpartpicker.com")
                    && uri.getPath().startsWith("/products/");
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
        public WebScraperApiException(String message) {
            super(message);
        }

        public WebScraperApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
