package de.verdox.hwapi.priceapi.component.service.ebay;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Holt OAuth Access Tokens für die eBay Buy Feed API
 * via Client Credentials Flow.
 *
 * WICHTIG: Für die Feed API (Client Credentials) werden KEINE Scopes benötigt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EbayOAuthService {

    private final EbayFeedProperties ebayFeedProperties;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cached Token + Ablaufzeitpunkt
    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    /**
     * Liefert ein gültiges App-Access-Token für die eBay-APIs.
     * Verwendet einen einfachen in-memory Cache.
     */
    public synchronized String getAccessToken() {

        // Wenn Token vorhanden und noch nicht kurz vor Ablauf → wiederverwenden
        if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return cachedAccessToken;
        }

        try {
            String clientId = ebayFeedProperties.getClientId();
            String clientSecret = ebayFeedProperties.getClientSecret();

            if (clientId == null || clientSecret == null) {
                throw new IllegalStateException(
                        "EBAY_FEED_CLIENT_ID / EBAY_FEED_CLIENT_SECRET not configured."
                );
            }

            String credentials = clientId + ":" + clientSecret;
            String encodedCredentials = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            URI uri = URI.create("https://api.ebay.com/identity/v1/oauth2/token");

            String body = "grant_type=client_credentials";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", "Basic " + encodedCredentials)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Failed to obtain eBay OAuth token. Status=" +
                                response.statusCode() + ", Body=" + response.body()
                );
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> json = objectMapper.readValue(response.body(), Map.class);

            String accessToken = (String) json.get("access_token");
            Number expiresIn = (Number) json.getOrDefault("expires_in", 7200);

            if (accessToken == null) {
                throw new IllegalStateException("eBay OAuth response missing access_token");
            }

            cachedAccessToken = accessToken;
            tokenExpiresAt = Instant.now().plusSeconds(expiresIn.longValue());

            log.info("Fetched new eBay OAuth token, valid for {} seconds", expiresIn);

            return cachedAccessToken;

        } catch (IOException e) {
            throw new RuntimeException("Error obtaining eBay OAuth token", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while obtaining eBay OAuth token", e);
        }
    }
}
