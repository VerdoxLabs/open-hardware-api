package de.verdox.hwapi.priceapi.io.ebay.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EbayDeveloperAPIClient {
    private static final Logger LOGGER = Logger.getLogger(EbayDeveloperAPIClient.class.getName());

    private final WebClient webClient;
    private final WebClient authClient;
    private final String rootUri;
    private final String clientId;
    private final String clientSecret;

    // Token cache
    private volatile String bearerToken;
    private volatile long tokenExpiryEpochSec = 0L;

    public EbayDeveloperAPIClient(String rootUri, String clientId, String clientSecret) {
        LOGGER.log(Level.INFO,"Initializing EbayDeveloperAPIClient for " + rootUri);
        this.rootUri = rootUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.webClient = WebClient.builder()
                .baseUrl(rootUri)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.authClient = WebClient.builder()
                .baseUrl(rootUri)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public boolean isSandbox() {
        return rootUri.contains("sandbox");
    }

    private synchronized void ensureValidToken() {
        long now = Instant.now().getEpochSecond();
        if (bearerToken == null || now + 10 >= tokenExpiryEpochSec) {
            refreshTokenBlocking();
        }
    }

    @SuppressWarnings("unchecked")
    private void refreshTokenBlocking() {
        if (clientId == null || clientSecret == null) {
            throw new IllegalStateException("EBAY_CLIENT_ID and EBAY_CLIENT_SECRET must be set for token refresh");
        }
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> resp = authClient.post()
                .uri("/identity/v1/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("grant_type=client_credentials&scope="+rootUri+"/oauth/api_scope")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (resp == null || !resp.containsKey("access_token")) {
            throw new RuntimeException("Failed to refresh eBay token: empty response");
        }
        this.bearerToken = (String) resp.get("access_token");
        Number expiresIn = (Number) resp.getOrDefault("expires_in", 3600);
        this.tokenExpiryEpochSec = Instant.now().getEpochSecond() + expiresIn.longValue();
        LOGGER.log(Level.INFO, "Got a new eBay token, expires in " + expiresIn + " seconds");
    }

    /**
     * Einzelne Suche - liefert zusätzlichen Meta-Infos (Status, Rate-Limit)
     */
    public Mono<EbaySearchResult> search(EbayBrowseSearchRequest request) {
        ensureValidToken();
        String pathAndQuery = request.buildUrl().replace(rootUri, "");
        Map<String, String> headers = request.buildHeaders(bearerToken);

        return webClient.get()
                .uri(pathAndQuery)
                .headers(h -> headers.forEach(h::add))
                .exchangeToMono(resp -> resp.bodyToMono(String.class).map(body -> {
                    int status = resp.statusCode().value();
                    String rem = resp.headers().asHttpHeaders().getFirst("x-ratelimit-remaining");
                    String reset = resp.headers().asHttpHeaders().getFirst("x-ratelimit-reset");
                    int remaining = -1;
                    long resetEpoch = 0L;
                    try { if (rem != null) remaining = Integer.parseInt(rem); } catch (Exception ignored) {}
                    try { if (reset != null) resetEpoch = Long.parseLong(reset); } catch (Exception ignored) {}
                    return new EbaySearchResult(status, body, remaining, resetEpoch);
                }));
    }

    /**
     * Bulk: ein Request pro GTIN parallel ausführen
     */
    public Mono<List<EbaySearchResult>> searchByGtins(List<String> gtins, EbayBrowseSearchRequest.Builder template) {
        var requests = EbayBrowseSearchRequest.bulkForGtins(gtins, template);
        return Mono.zip(
                requests.stream().map(this::search).toList(),
                arr -> Arrays.stream(arr).map(o -> (EbaySearchResult) o).toList()
        );
    }

    /**
     * Holt die Rate-Limit-Informationen vom Analytics-Endpoint und parsed die Rate-Info
     * für das angegebene resourceName (z. B. "buy.browse").
     */
    public Mono<EbayRateInfo> getRateLimitForResource(String resourceName) {
        ensureValidToken();
        return webClient.get()
                .uri("/developer/analytics/v1_beta/rate_limit")
                .headers(h -> {
                    h.add(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
                    h.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                })
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> parseRateLimit(body, resourceName))
                .onErrorResume(ex -> {
                    LOGGER.log(Level.WARNING, "Could not fetch rate limits", ex);
                    return Mono.just(new EbayRateInfo(-1, -1, 0L));
                });
    }

    private EbayRateInfo parseRateLimit(String body, String resourceName) {
        try {
            JsonElement rootEl = JsonParser.parseString(body);
            if (!rootEl.isJsonObject()) return new EbayRateInfo(-1, -1, 0L);
            JsonObject root = rootEl.getAsJsonObject();
            if (!root.has("rateLimits")) return new EbayRateInfo(-1, -1, 0L);
            JsonArray rateLimits = root.getAsJsonArray("rateLimits");
            for (JsonElement rlEl : rateLimits) {
                if (!rlEl.isJsonObject()) continue;
                JsonObject rlObj = rlEl.getAsJsonObject();
                if (!rlObj.has("resources")) continue;
                JsonArray resources = rlObj.getAsJsonArray("resources");
                for (JsonElement rEl : resources) {
                    if (!rEl.isJsonObject()) continue;
                    JsonObject rObj = rEl.getAsJsonObject();
                    if (!rObj.has("name")) continue;
                    String name = rObj.get("name").getAsString();
                    if (!resourceName.equalsIgnoreCase(name)) continue;
                    if (!rObj.has("rates") || !rObj.get("rates").isJsonArray()) continue;
                    JsonArray rates = rObj.getAsJsonArray("rates");
                    if (rates.size() == 0) continue;
                    JsonObject rate = rates.get(0).getAsJsonObject(); // take first entry
                    int remaining = rate.has("remaining") && !rate.get("remaining").isJsonNull() ? rate.get("remaining").getAsInt() : -1;
                    int limit = rate.has("limit") && !rate.get("limit").isJsonNull() ? rate.get("limit").getAsInt() : -1;
                    long resetEpoch = 0L;
                    if (rate.has("reset") && !rate.get("reset").isJsonNull()) {
                        try {
                            resetEpoch = java.time.Instant.parse(rate.get("reset").getAsString()).getEpochSecond();
                        } catch (Exception ignore) { /* ignore parse problems */ }
                    }
                    return new EbayRateInfo(remaining, limit, resetEpoch);
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to parse rate limit JSON", ex);
        }
        return new EbayRateInfo(-1, -1, 0L);
    }

    public static final class EbaySearchResult {
        public final int statusCode;
        public final String body;
        public final int rateLimitRemaining;
        public final long rateLimitResetEpoch;

        public EbaySearchResult(int statusCode, String body, int rateLimitRemaining, long rateLimitResetEpoch) {
            this.statusCode = statusCode;
            this.body = body;
            this.rateLimitRemaining = rateLimitRemaining;
            this.rateLimitResetEpoch = rateLimitResetEpoch;
        }

        @Override
        public String toString() {
            return "EbaySearchResult{" +
                    "statusCode=" + statusCode +
                    ", body='" + body + '\'' +
                    ", rateLimitRemaining=" + rateLimitRemaining +
                    ", rateLimitResetEpoch=" + rateLimitResetEpoch +
                    '}';
        }
    }

    public static final class EbayRateInfo {
        public final int remaining;
        public final int limit;
        public final long resetEpochSec;

        public EbayRateInfo(int remaining, int limit, long resetEpochSec) {
            this.remaining = remaining;
            this.limit = limit;
            this.resetEpochSec = resetEpochSec;
        }

        @Override
        public String toString() {
            return "EbayRateInfo{" +
                    "remaining=" + remaining +
                    ", limit=" + limit +
                    ", resetEpochSec=" + resetEpochSec +
                    '}';
        }
    }
}
