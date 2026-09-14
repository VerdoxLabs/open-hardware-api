package de.verdox.hwapi.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.hwapi.pricing.api.PricePointUploadDto;
import de.verdox.hwapi.catalog.domain.values.Currency;
import de.verdox.hwapi.catalog.domain.values.ItemCondition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    // ✅ NEW bulk endpoints (by exact identifiers)
    public static final String SERIES_ACTIVE_BULK_BY_IDS_PATH = "/prices/sold/series/fetchActive/bulkByIds";
    public static final String SERIES_COMPLETED_BULK_BY_IDS_PATH = "/prices/sold/series/fetchCompleted/bulkByIds";

    // (optional legacy)
    public static final String SERIES_ACTIVE_BULK_PATH = "/prices/sold/series/fetchActive/bulk";
    public static final String SERIES_COMPLETED_BULK_PATH = "/prices/sold/series/fetchCompleted/bulk";

    public HWApiPricesClient(String baseUrl) {
        super(baseUrl);
    }

    public HWApiPricesClient(String baseUrl, ObjectMapper mapper) {
        super(baseUrl, mapper);
    }

    // -------------------------------------------------------------------------
    // Upload verkaufte Price-Points
    // -------------------------------------------------------------------------

    public HardwareSpecClient.BulkResult priceItemUpload(Collection<PricePointUploadDto> toUpload) {
        byte[] bytes = http.post()
                .uri(uriBuilder("/prices/sold/points", uriBuilder -> { }))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toUpload)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .block();

        return parseBulkResult(bytes);
    }

    // -------------------------------------------------------------------------
    // Avg Current Bulk
    // -------------------------------------------------------------------------

    public HardwareSpecClient.BulkAvgCurrentResponse getAvgCurrentBulk(List<String> eans) {
        return getAvgCurrentBulk(eans, Currency.US_DOLLAR, 3);
    }

    public HardwareSpecClient.BulkAvgCurrentResponse getAvgCurrentBulk(List<String> eans, Currency currency, int monthsSince) {
        if (eans == null || eans.isEmpty()) {
            return new HardwareSpecClient.BulkAvgCurrentResponse(currency.name(), Math.max(0, monthsSince), List.of());
        }

        HardwareSpecClient.BulkAvgCurrentRequest req =
                new HardwareSpecClient.BulkAvgCurrentRequest(
                        eans,
                        Math.max(0, monthsSince),
                        currency != null ? currency.name() : null
                );

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

    public Map<String, BigDecimal> getAvgCurrentBulkMap(List<String> eans, Currency currency, int monthsSince) {
        HardwareSpecClient.BulkAvgCurrentResponse resp = getAvgCurrentBulk(eans, currency, monthsSince);
        if (resp.results() == null) return Map.of();
        return resp.results().stream()
                .filter(HardwareSpecClient.AvgEntry::found)
                .collect(Collectors.toMap(
                        HardwareSpecClient.AvgEntry::ean,
                        HardwareSpecClient.AvgEntry::value,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    // -------------------------------------------------------------------------
    // Single series (unchanged)
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

    // -------------------------------------------------------------------------
    // ✅ NEW: Bulk series by exact MPN/EAN
    // -------------------------------------------------------------------------

    public Map<String, PriceSeriesResponseDTO> fetchActiveSeriesBulkByIdsOnce(
            List<String> mpns,
            List<String> eans,
            Set<ItemCondition> conditions,
            int monthSince,
            boolean fetchIfNoData
    ) {
        return fetchSeriesBulkByIdsOnce(
                SERIES_ACTIVE_BULK_BY_IDS_PATH,
                mpns,
                eans,
                conditions,
                monthSince,
                fetchIfNoData
        );
    }

    public Map<String, PriceSeriesResponseDTO> fetchCompletedSeriesBulkByIdsOnce(
            List<String> mpns,
            List<String> eans,
            Set<ItemCondition> conditions,
            int monthSince,
            boolean fetchIfNoData
    ) {
        return fetchSeriesBulkByIdsOnce(
                SERIES_COMPLETED_BULK_BY_IDS_PATH,
                mpns,
                eans,
                conditions,
                monthSince,
                fetchIfNoData
        );
    }

    private Map<String, PriceSeriesResponseDTO> fetchSeriesBulkByIdsOnce(
            String path,
            List<String> mpns,
            List<String> eans,
            Set<ItemCondition> conditions,
            int monthSince,
            boolean fetchIfNoData
    ) {
        List<String> mpnList = mpns != null ? mpns : List.of();
        List<String> eanList = eans != null ? eans : List.of();

        if (mpnList.isEmpty() && eanList.isEmpty()) return Map.of();

        BulkSeriesRequestV2 req = new BulkSeriesRequestV2(
                mpnList,
                eanList,
                conditions != null ? conditions : Set.of(),
                monthSince,
                fetchIfNoData
        );

        byte[] bytes = this.http.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .block();

        try {
            BulkSeriesResponseV2 resp = this.om.readValue(bytes, BulkSeriesResponseV2.class);
            if (resp.results() == null) return Map.of();

            // identifier -> series (in response order)
            return resp.results().stream()
                    .filter(e -> e.identifier() != null)
                    .collect(Collectors.toMap(
                            SeriesEntryV2::identifier,
                            SeriesEntryV2::series,
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse bulkByIds series response: " + new String(bytes, StandardCharsets.UTF_8), e);
        }
    }

    // -------------------------------------------------------------------------
    // (Optional) Legacy bulk by keys (kannst du später löschen)
    // -------------------------------------------------------------------------

    public Map<String, PriceSeriesResponseDTO> fetchActiveSeriesBulkOnce(
            List<String> keys,
            Set<ItemCondition> conditions,
            int monthSince,
            boolean fetchIfNoData
    ) {
        if (keys == null || keys.isEmpty()) return Map.of();

        BulkSeriesRequest req = new BulkSeriesRequest(
                keys,
                conditions != null ? conditions : Set.of(),
                monthSince,
                fetchIfNoData
        );

        byte[] bytes = this.http.post()
                .uri(SERIES_ACTIVE_BULK_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .block();

        try {
            BulkSeriesResponse resp = this.om.readValue(bytes, BulkSeriesResponse.class);
            if (resp.results() == null) return Map.of();

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
        if (keys == null || keys.isEmpty()) return Map.of();

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
            if (resp.results() == null) return Map.of();

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

    // -------------------------------------------------------------------------
    // Shared helpers (existing)
    // -------------------------------------------------------------------------

    private PriceSeriesResponseDTO fetchSeriesOnce(
            String path,
            Set<String> mpns,
            Set<String> eans,
            Set<ItemCondition> conditions,
            int monthSince,
            boolean fetchIfNoData
    ) {
        String uri = uriBuilder(path, b -> {
            if (mpns != null && !mpns.isEmpty()) mpns.forEach(mpn -> b.queryParam("MPNs", mpn));
            if (eans != null && !eans.isEmpty()) eans.forEach(ean -> b.queryParam("EANs", ean));
            if (conditions != null && !conditions.isEmpty()) conditions.forEach(c -> b.queryParam("conditions", c.name()));
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
        if (timeout == null || timeout.isZero() || timeout.isNegative()) timeout = Duration.ofSeconds(30);
        if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) pollInterval = Duration.ofSeconds(2);

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
    // DTOs
    // -------------------------------------------------------------------------

    // ✅ NEW byIds request/response
    public record BulkSeriesRequestV2(
            List<String> mpns,
            List<String> eans,
            Set<ItemCondition> conditions,
            Integer monthSince,
            Boolean fetchIfNoData
    ) {}

    public record SeriesEntryV2(
            String identifier,
            PriceSeriesResponseDTO series
    ) {}

    public record BulkSeriesResponseV2(
            List<SeriesEntryV2> results
    ) {}

    // legacy
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
