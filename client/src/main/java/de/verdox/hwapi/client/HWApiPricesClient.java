package de.verdox.hwapi.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.hwapi.model.dto.PricePointUploadDto;
import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.model.values.ItemCondition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class HWApiPricesClient extends HWApiClient {

    private static final String SERIES_ACTIVE_PATH = "/prices/sold/series/fetchActive";
    private static final String SERIES_COMPLETED_PATH = "/prices/sold/series/fetchCompleted";

    public HWApiPricesClient(String baseUrl) {
        super(baseUrl);
    }

    public HWApiPricesClient(String baseUrl, ObjectMapper mapper) {
        super(baseUrl, mapper);
    }

    /**
     * Upload verkaufte Price-Points.
     * Neuer Endpoint: POST /prices/sold/points
     */
    public HardwareSpecClient.BulkResult priceItemUpload(Collection<PricePointUploadDto> toUpload) {
        byte[] bytes = http.post()
                .uri(uriBuilder("/prices/sold/points", uriBuilder -> {
                }))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toUpload)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .block();
        return parseBulkResult(bytes);
    }

    /**
     * Defaults: USD, monthsSince=3
     * Nutzt jetzt POST /prices/sold/avg-current/bulk
     */
    public HardwareSpecClient.BulkAvgCurrentResponse getAvgCurrentBulk(List<String> eans) {
        return getAvgCurrentBulk(eans, Currency.US_DOLLAR, 3);
    }

    public HardwareSpecClient.BulkAvgCurrentResponse getAvgCurrentBulk(List<String> eans, Currency currency, int monthsSince) {
        if (eans == null || eans.isEmpty()) {
            return new HardwareSpecClient.BulkAvgCurrentResponse(currency.name(), Math.max(0, monthsSince), List.of());
        }
        HardwareSpecClient.BulkAvgCurrentRequest req =
                new HardwareSpecClient.BulkAvgCurrentRequest(eans, Math.max(0, monthsSince), currency != null ? currency.name() : null);

        byte[] bytes = this.http.post()
                .uri("/prices/sold/avg-current/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .block();

        try {
            return this.om.readValue(bytes, HardwareSpecClient.BulkAvgCurrentResponse.class);
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse bulk avg-current response: " + new String(bytes, StandardCharsets.UTF_8), e);
        }
    }

    /**
     * Bequeme Map-Variante: nur gefundene Werte (found==true) werden aufgenommen.
     */
    public Map<String, BigDecimal> getAvgCurrentBulkMap(List<String> eans, Currency currency, int monthsSince) {
        HardwareSpecClient.BulkAvgCurrentResponse resp = getAvgCurrentBulk(eans, currency, monthsSince);
        if (resp.results() == null) return Map.of();
        return resp.results().stream()
                .filter(HardwareSpecClient.AvgEntry::found)
                .collect(Collectors.toMap(HardwareSpecClient.AvgEntry::ean, HardwareSpecClient.AvgEntry::value,
                        (a, b) -> a, LinkedHashMap::new)); // Request-Reihenfolge bewahren
    }

    /**
     * Batch-Helfer: ruft den Bulk-Endpoint mehrfach auf, wenn die EAN-Liste groß ist.
     *
     * @param batchSize z.B. 400 (sollte <= Server-Limit sein)
     */
    public Map<String, BigDecimal> getAvgCurrentBulkMapBatched(List<String> eans, Currency currency, int monthsSince, int batchSize) {
        if (eans == null || eans.isEmpty()) return Map.of();
        int size = eans.size();
        LinkedHashMap<String, BigDecimal> out = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i += batchSize) {
            List<String> slice = eans.subList(i, Math.min(i + batchSize, size));
            Map<String, BigDecimal> part = getAvgCurrentBulkMap(slice, currency, monthsSince);
            out.putAll(part);
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Einzel-Avg-Price (verkaufte Artikel)
    // nutzt jetzt /prices/sold/...
    // -------------------------------------------------------------------------

    public Optional<BigDecimal> getAverageCurrentPrice(String ean) {
        return getAverageCurrentPrice(ean, 3);
    }

    public Optional<BigDecimal> getAverageCurrentPrice(String ean, int monthsSince) {
        return getAverageCurrentPrice(ean, Currency.US_DOLLAR, monthsSince);
    }

    public Optional<BigDecimal> getAverageCurrentPrice(String ean, Currency currency) {
        return getAverageCurrentPrice(ean, currency, 3);
    }

    public Optional<BigDecimal> getAverageCurrentPrice(String ean, Currency currency, int monthsSince) {
        // neuer Basis-Pfad: /prices/sold/{ean}/avg-current[/monthsSince]
        String base = monthsSince > 0 ? "/prices/sold/{ean}/avg-current/{monthsSince}" : "/prices/sold/{ean}/avg-current";

        String uri = (currency != null)
                ? (monthsSince > 0
                ? uriBuilder(base, b -> b.queryParam("currency", currency.name()), ean, monthsSince)
                : uriBuilder(base, b -> b.queryParam("currency", currency.name()), ean))
                : (monthsSince > 0
                ? uriBuilder(base, b -> {
        }, ean, monthsSince)
                : uriBuilder(base, b -> {
        }, ean));

        byte[] bytes = http.get()
                .uri(uri)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .blockOptional()
                .orElse(null);

        if (bytes == null) return Optional.empty();
        try {
            var node = om.readTree(bytes);
            if (node.isMissingNode() || node.isNull()) return Optional.empty();
            return Optional.of(om.treeToValue(node, BigDecimal.class));
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse avg-current response: " + new String(bytes, StandardCharsets.UTF_8), e);
        }
    }

    // -------------------------------------------------------------------------
    // Serien (verkaufte Artikel)
    // nutzen jetzt /prices/sold/...
    // -------------------------------------------------------------------------

    public record PricePoint(java.time.LocalDate sellPrice, java.math.BigDecimal price, String currency) {
    }

    public java.util.List<PricePoint> getPriceSeries(String ean) {
        return http.get()
                .uri("/prices/sold/{ean}/series", ean)
                .exchangeToMono(resp -> readJsonOrError(resp, PricePoint[].class))
                .map(arr -> java.util.Arrays.asList(arr))
                .blockOptional()
                .orElseGet(java.util.List::of);
    }

    public java.util.List<PricePoint> getRecentPriceSeries(String ean) {
        return getRecentPriceSeries(ean, 3);
    }

    public java.util.List<PricePoint> getRecentPriceSeries(String ean, int monthsSince) {
        String path = monthsSince > 0
                ? "/prices/sold/{ean}/series/recent/{monthsSince}"
                : "/prices/sold/{ean}/series/recent";
        return http.get()
                .uri(path, ean, monthsSince)
                .exchangeToMono(resp -> readJsonOrError(resp, PricePoint[].class))
                .map(arr -> java.util.Arrays.asList(arr))
                .blockOptional()
                .orElseGet(java.util.List::of);
    }

    // -------------------------------------------------------------------------
    // Optional: On-Demand Lookup (nutzt /prices/sold/lookup)
    // -------------------------------------------------------------------------

    /**
     * On-Demand Lookup: triggert Scraper+API im Backend (mit 24h Negativ-Cache).
     * Gibt nur dann einen Wert zurück, wenn status == FOUND.
     */
    public Optional<BigDecimal> lookupPriceNow(String ean, Currency currency) {
        if (ean == null || ean.isBlank()) return Optional.empty();
        if (currency == null) currency = Currency.EURO;

        Map<String, Object> body = new HashMap<>();
        body.put("ean", ean);
        body.put("currency", currency.name());

        byte[] bytes = http.post()
                .uri("/prices/sold/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> {
                    HttpStatus status = HttpStatus.resolve(response.statusCode().value());

                    // OK -> Body normal lesen
                    if (status.is2xxSuccessful()) {
                        return response.bodyToMono(byte[].class);
                    }

                    // 429 -> Body lesen, aber NICHT als Fehler behandeln
                    if (status == HttpStatus.TOO_MANY_REQUESTS) {
                        return response.bodyToMono(byte[].class);
                    }

                    // alles andere -> Exception wie bisher
                    return response.createException().flatMap(Mono::error);
                })
                .blockOptional()
                .orElse(null);

        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }

        try {
            JsonNode node = om.readTree(bytes);
            if (node == null || node.isNull() || node.isMissingNode()) {
                return Optional.empty();
            }

            String status = node.path("status").asText(null);

            // Wenn der Server sagt: nicht gefunden / nur gecached-info, dann einfach leer zurück
            if (!"FOUND".equals(status)) {
                if ("NOT_FOUND_CACHED_24H".equals(status)) {
                    LOGGER.info("Price for " + ean + " not cached yet (status NOT_FOUND_CACHED_24H).");
                } else {
                    LOGGER.info("Price for EAN " + ean + " not found, status=" + status);
                }
                return Optional.empty();
            }

            JsonNode valueNode = node.get("value");
            if (valueNode == null || valueNode.isNull()) {
                return Optional.empty();
            }

            BigDecimal value = om.treeToValue(valueNode, BigDecimal.class);
            return Optional.ofNullable(value);

        } catch (IOException e) {
            throw new RuntimeException("Cannot parse lookup price response: " +
                    new String(bytes, StandardCharsets.UTF_8), e);
        }
    }

    // -------------------------------------------------------------------------
    // Neue Methoden: Serien (ACTIVE / COMPLETED) mit Polling & Timeout
    // Endpoints: GET /prices/series/fetchActive, /prices/series/fetchCompleted
    // -------------------------------------------------------------------------

    public PriceSeriesResponseDTO fetchActiveSeriesOnce(
            Set<String> mpns,
            Set<String> eans,
            Set<ItemCondition> conditions,
            int monthSince,
            boolean fetchIfNoData
    ) {
        return fetchSeriesOnce(SERIES_ACTIVE_PATH, mpns, eans, conditions, monthSince, fetchIfNoData);
    }

    public PriceSeriesResponseDTO fetchCompletedSeriesOnce(
            Set<String> mpns,
            Set<String> eans,
            Set<ItemCondition> conditions,
            int monthSince,
            boolean fetchIfNoData
    ) {
        return fetchSeriesOnce(SERIES_COMPLETED_PATH, mpns, eans, conditions, monthSince, fetchIfNoData);
    }

    public PriceSeriesResponseDTO fetchActiveSeriesWithPolling(
            Set<String> mpns,
            Set<String> eans,
            Set<ItemCondition> conditions,
            int monthSince
    ) {
        return fetchSeriesWithPolling(SERIES_ACTIVE_PATH, mpns, eans, conditions, monthSince,
                Duration.ofSeconds(30), Duration.ofSeconds(2));
    }

    public PriceSeriesResponseDTO fetchCompletedSeriesWithPolling(
            Set<String> mpns,
            Set<String> eans,
            Set<ItemCondition> conditions,
            int monthSince
    ) {
        return fetchSeriesWithPolling(SERIES_COMPLETED_PATH, mpns, eans, conditions, monthSince,
                Duration.ofSeconds(30), Duration.ofSeconds(2));
    }

    public PriceSeriesResponseDTO fetchActiveSeriesWithPolling(
            Set<String> mpns,
            Set<String> eans,
            Set<ItemCondition> conditions,
            int monthSince,
            Duration timeout,
            Duration pollInterval
    ) {
        return fetchSeriesWithPolling(SERIES_ACTIVE_PATH, mpns, eans, conditions, monthSince, timeout, pollInterval);
    }

    public PriceSeriesResponseDTO fetchCompletedSeriesWithPolling(
            Set<String> mpns,
            Set<String> eans,
            Set<ItemCondition> conditions,
            int monthSince,
            Duration timeout,
            Duration pollInterval
    ) {
        return fetchSeriesWithPolling(SERIES_COMPLETED_PATH, mpns, eans, conditions, monthSince, timeout, pollInterval);
    }

    // -------------------------------------------------------------------------
    // Helper: Serienfetch & Polling (vermeidet duplizierten Code)
    // -------------------------------------------------------------------------

    public Map<String, PriceSeriesResponseDTO> fetchActiveSeriesBulkOnce(
            List<String> keys,
            Set<ItemCondition> conditions,
            int monthSince,
            boolean fetchIfNoData
    ) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }

        BulkSeriesRequest req = new BulkSeriesRequest(
                keys,
                conditions != null ? conditions : Set.of(),
                monthSince,
                fetchIfNoData
        );

        byte[] bytes = (byte[]) ((WebClient.RequestBodySpec) this.http.post()
                .uri(SERIES_ACTIVE_BULK_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req))
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .block();

        try {
            BulkSeriesResponse resp = this.om.readValue(bytes, BulkSeriesResponse.class);
            if (resp.results() == null) {
                return Map.of();
            }

            return resp.results().stream()
                    .filter(e -> e.key() != null)
                    .collect(Collectors.toMap(
                            SeriesEntry::key,
                            SeriesEntry::series,
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse bulk series active response: " + new String(bytes, StandardCharsets.UTF_8), e);
        }
    }


    public Map<String, PriceSeriesResponseDTO> fetchCompletedSeriesBulkOnce(
            List<String> keys,
            Set<ItemCondition> conditions,
            int monthSince,
            boolean fetchIfNoData
    ) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }

        BulkSeriesRequest req = new BulkSeriesRequest(
                keys,
                conditions != null ? conditions : Set.of(),
                monthSince,
                fetchIfNoData
        );

        byte[] bytes = this.http.post()
                .uri(SERIES_COMPLETED_BULK_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .block();

        try {
            BulkSeriesResponse resp = this.om.readValue(bytes, BulkSeriesResponse.class);
            if (resp.results() == null) {
                return Map.of();
            }

            return resp.results().stream()
                    .filter(e -> e.key() != null)
                    .collect(Collectors.toMap(
                            SeriesEntry::key,
                            SeriesEntry::series,
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse bulk series completed response: " + new String(bytes, StandardCharsets.UTF_8), e);
        }
    }



    private PriceSeriesResponseDTO fetchSeriesOnce(
            String path,
            Set<String> mpns,
            Set<String> eans,
            Set<ItemCondition> conditions,
            int monthSince,
            boolean fetchIfNoData
    ) {
        String uri = uriBuilder(path, b -> {
            if (mpns != null && !mpns.isEmpty()) {
                mpns.forEach(mpn -> b.queryParam("MPNs", mpn));
            }
            if (eans != null && !eans.isEmpty()) {
                eans.forEach(ean -> b.queryParam("EANs", ean));
            }
            if (conditions != null && !conditions.isEmpty()) {
                conditions.forEach(c -> b.queryParam("conditions", c.name()));
            }
            b.queryParam("monthSince", monthSince);
            b.queryParam("fetchIfNoData", fetchIfNoData);
        });

        return http.get()
                .uri(uri)
                .exchangeToMono(resp -> readJsonOrError(resp, PriceSeriesResponseDTO.class))
                .block();
    }

    private PriceSeriesResponseDTO fetchSeriesWithPolling(
            String path,
            Set<String> mpns,
            Set<String> eans,
            Set<ItemCondition> conditions,
            int monthSince,
            Duration timeout,
            Duration pollInterval
    ) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofSeconds(30);
        }
        if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
            pollInterval = Duration.ofSeconds(2);
        }

        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        PriceSeriesResponseDTO current = fetchSeriesOnce(path, mpns, eans, conditions, monthSince, true);

        while (current != null
                && current.refreshStarted()
                && (current.series() == null || current.series().isEmpty())
                && System.nanoTime() < deadlineNanos) {

            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            current = fetchSeriesOnce(path, mpns, eans, conditions, monthSince, false);
        }

        return current;
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Optional<BigDecimal> getPriceDto(String uri) {
        byte[] bytes = http.get().uri(uri)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .blockOptional().orElse(null);
        if (bytes == null || bytes.length == 0) return Optional.empty();
        try {
            var node = om.readTree(bytes);
            if (node.isMissingNode() || node.isNull()) return Optional.empty();
            return Optional.of(om.treeToValue(node, BigDecimal.class));
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse price dto: " + new String(bytes, java.nio.charset.StandardCharsets.UTF_8), e);
        }
    }

    public static final String SERIES_ACTIVE_BULK_PATH = "/prices/sold/series/fetchActive/bulk";
    public static final String SERIES_COMPLETED_BULK_PATH = "/prices/sold/series/fetchCompleted/bulk";

    public record BulkSeriesRequest(
            List<String> keys,
            Set<ItemCondition> conditions,
            Integer monthSince,
            Boolean fetchIfNoData
    ) {}

    public record SeriesEntry(
            String key,
            PriceSeriesResponseDTO series
    ) {}

    public record BulkSeriesResponse(
            List<SeriesEntry> results
    ) {}


}

