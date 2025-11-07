package de.verdox.openhardwareapi.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.verdox.openhardwareapi.model.HardwareTypes;
import de.verdox.openhardwareapi.model.dto.PricePointUploadDto;
import de.verdox.openhardwareapi.model.values.Currency;
import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Eine einzige Klasse für alle REST-Calls. Einfach mit Base-URL instanziieren.
 */
// ... imports bleiben wie gehabt

// ^ falls du keine Filter brauchst, kannst du den Import entfernen (wird hier nicht zwingend genutzt)

public final class HardwareSpecClient {
    private static final Logger LOGGER = Logger.getLogger(HardwareSpecClient.class.getName());
    private static final String API_V1 = "/api/v1";

    private final WebClient http;
    @Getter
    private final String urlApiV1;
    private final ObjectMapper om;

    public HardwareSpecClient(String baseUrl) {
        this(baseUrl, defaultMapper());
    }

    public HardwareSpecClient(String baseUrl, ObjectMapper mapper) {
        this.urlApiV1 = baseUrl + "/api/v1";
        LOGGER.info("HardwareSpecClient created for base url " + baseUrl + " and api v1 endpoint " + trimTrailingSlash(this.urlApiV1));
        this.om = mapper;

        // >>> Neu: Pool mit Grenzen + Idle-/LifeTime
        ConnectionProvider provider = ConnectionProvider.builder("ohw-api-pool")
                .maxConnections(50)                 // Poolgröße (bei Bedarf kleiner, z.B. 20)
                .pendingAcquireMaxCount(200)        // Warteschlangenlimit (Backpressure)
                .maxIdleTime(Duration.ofSeconds(30))// Idle-Verbindung entsorgen
                .maxLifeTime(Duration.ofMinutes(2)) // Verbindungslebenszeit begrenzen
                .lifo()                             // bessere Cache-Treffer
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .followRedirect(true)
                .responseTimeout(Duration.ofSeconds(15))
                .keepAlive(true)
                .compress(true);

        this.http = WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize((int) DataSize.ofMegabytes(64).toBytes()))
                        .build()
                )
                .baseUrl(trimTrailingSlash(this.urlApiV1))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/json")
                .codecs(c -> c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper)))
                .build();
    }

    /* =========================================================
       ========    READ: LIST, GET ONE, TYPES            =======
       ========================================================= */

    public <T> PageResponse<T> list(String type, int page, int size, String sort, Class<T> entityClass) {
        String uri = uriBuilder("/specs/{type}", b -> {
            b.queryParam("page", page);
            b.queryParam("size", size);
            if (sort != null && !sort.isBlank()) b.queryParam("sort", sort);
        }, type);

        PagesMeta meta = pages(type, size);

        List<T> content = http.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSON)
                .exchangeToMono(resp -> {
                    if (!resp.statusCode().is2xxSuccessful()) return toProblem(resp).flatMap(Mono::error);
                    MediaType ct = resp.headers().contentType().orElse(MediaType.APPLICATION_JSON);

                    if (MediaType.APPLICATION_NDJSON.isCompatibleWith(ct)) {
                        return resp.bodyToFlux(entityClass).collectList();
                    }

                    // JSON: Array oder Page-Objekt unterscheiden
                    return resp.bodyToMono(byte[].class).flatMap(bytes -> {
                        try {
                            JsonNode n = om.readTree(bytes);
                            List<T> list = new ArrayList<>();
                            if (n.isArray()) {
                                for (JsonNode item : n) list.add(om.treeToValue(item, entityClass));
                            } else if (n.has("content") && n.get("content").isArray()) {
                                for (JsonNode item : n.get("content")) list.add(om.treeToValue(item, entityClass));
                            } else {
                                return Mono.error(new IllegalStateException(
                                        "Unexpected JSON shape for list: " + truncate(new String(bytes, StandardCharsets.UTF_8), 1000)));
                            }
                            return Mono.just(list);
                        } catch (IOException e) {
                            return Mono.error(new IllegalStateException("Cannot parse list response", e));
                        }
                    });
                })
                .blockOptional()
                .orElseGet(List::of);

        int totalPages = meta != null ? meta.totalPages() : -1;
        long totalElements = meta != null ? meta.totalElements() : -1;
        return new PageResponse<>(content, page, size, totalPages, totalElements);
    }

    public <T> T getOne(String ean, String type, Class<T> entityClass) {
        return http.get()
                .uri("/specs/byEan/{ean}/{type}", ean, type)
                .exchangeToMono(resp -> readJsonOrError(resp, entityClass))
                .block();
    }

    public List<String> listTypes() {
        return http.get()
                .uri("/specs/types")
                .exchangeToMono(resp -> readJsonOrError(resp, List.class))
                .blockOptional()
                .orElseGet(List::of);
    }

    /* =========================================================
       ========           WRITE: UPLOADS                 =======
       ========================================================= */

    public BulkResult priceItemUpload(Collection<PricePointUploadDto> toUpload) {
        byte[] bytes = http.post()
                .uri(uriBuilder("/prices/points", uriBuilder -> {
                }))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toUpload)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .block();
        return parseBulkResult(bytes);
    }

    public <T> T uploadHardwareOne(String type, T entity, Class<T> entityClass) {
        return http.post()
                .uri("/specs/{type}", type)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(entity)
                .exchangeToMono(resp -> readJsonOrError(resp, entityClass))
                .block();
    }

    public String uploadHardwareOneRaw(String type, String json) {
        return http.post()
                .uri("/specs/{type}", type)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchangeToMono(resp -> readJsonOrError(resp, String.class))
                .block();
    }

    public BulkResult bulkHardwareUpload(String type, List<?> entities, boolean allOrNothing) {
        byte[] bytes = http.post()
                .uri(uriBuilder("/specs/{type}/bulk", b -> b.queryParam("allOrNothing", allOrNothing), type))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(entities)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .block();
        return parseBulkResult(bytes);
    }

    public BulkResult bulkHardwareUploadNdjson(String type, List<?> entities, boolean allOrNothing) {
        String ndjson = entities.stream()
                .map(e -> {
                    try {
                        return om.writeValueAsString(e);
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .collect(Collectors.joining("\n"));

        byte[] bytes = http.post()
                .uri(uriBuilder("/specs/{type}/bulk", b -> b.queryParam("allOrNothing", allOrNothing), type))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ndjson)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .block();
        return parseBulkResult(bytes);
    }

    public BulkResult bulkHardwareUploadFile(String type, Path file, boolean allOrNothing) {
        byte[] data = readAllBytes(file);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResourceWithFilename(data, file.getFileName().toString()));

        byte[] bytes = http.post()
                .uri(uriBuilder("/specs/{type}/bulkFile", b -> b.queryParam("allOrNothing", allOrNothing), type))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form))
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .block();
        return parseBulkResult(bytes);
    }

    public PagesMeta pages(String type, int size) {
        String headUri = uriBuilder("/specs/{type}", b -> b.queryParam("size", size), type);

        return http.method(org.springframework.http.HttpMethod.HEAD)
                .uri(headUri)
                .exchangeToMono(resp -> {
                    HttpStatus status = HttpStatus.resolve(resp.statusCode().value());
                    if (status.is2xxSuccessful() || status == HttpStatus.NO_CONTENT) {
                        HttpHeaders h = resp.headers().asHttpHeaders();
                        long totalElements = parseLongHeader(h, "X-Total-Elements", -1L);
                        int totalPages = parseIntHeader(h, "X-Total-Pages", -1);
                        int pageSize = parseIntHeader(h, "X-Page-Size", size);

                        if (totalElements >= 0 && totalPages >= 0) {
                            return Mono.just(new PagesMeta(totalElements, pageSize, totalPages));
                        }
                        return fetchPagesMetaViaGet(type, size);
                    }
                    if (status == HttpStatus.NOT_FOUND || status == HttpStatus.METHOD_NOT_ALLOWED) {
                        return fetchPagesMetaViaGet(type, size);
                    }
                    return toProblem(resp).flatMap(Mono::error);
                })
                .block();
    }

// ---------- NEW: Cluster-Preis-Endpunkte ----------

    public Optional<BigDecimal> getMedianPerGbRam(HardwareTypes.RamType type, Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/ram/median-per-gb", b -> {
            b.queryParam("type", type.name());
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        byte[] bytes = http.get().uri(uri)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .blockOptional().orElse(null);
        if (bytes == null || bytes.length == 0) return Optional.empty();
        try {
            var node = om.readTree(bytes);
            if (node.isNull()) return Optional.empty();
            return Optional.of(om.treeToValue(node, BigDecimal.class));
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse median-per-gb RAM response", e);
        }
    }

    public Optional<BigDecimal> getEstimatedRamStickPrice(
            HardwareTypes.RamType type,
            long speedMtps,
            int capacityGb,
            boolean isKit,
            boolean ecc,
            Currency currency,
            int monthsSince
    ) {
        String uri = uriBuilder("/cluster/prices/ram/estimate", b -> {
            b.queryParam("type", type.name());
            b.queryParam("speed", speedMtps);
            b.queryParam("capacityGb", capacityGb);
            b.queryParam("isKit", isKit);
            b.queryParam("ecc", ecc);
            if (currency != null)
                b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    public Optional<BigDecimal> getMedianPerGbRamBySpeed(HardwareTypes.RamType type, long speedMtps, Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/ram/median-per-gb/by-speed", b -> {
            b.queryParam("type", type.name());
            b.queryParam("speed", speedMtps);
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    public Optional<BigDecimal> getMedianMainboard(HardwareTypes.CpuSocket socket, HardwareTypes.Chipset chipset,
                                                   HardwareTypes.MotherboardFormFactor formFactor,
                                                   Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/mainboard/median", b -> {
            b.queryParam("socket", socket.name());
            b.queryParam("chipset", chipset.name());
            b.queryParam("formFactor", formFactor.name());
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    /**
     * Defaults: USD, monthsSince=3
     */
    public BulkAvgCurrentResponse getAvgCurrentBulk(List<String> eans) {
        return getAvgCurrentBulk(eans, Currency.US_DOLLAR, 3);
    }

    public BulkAvgCurrentResponse getAvgCurrentBulk(List<String> eans, Currency currency, int monthsSince) {
        if (eans == null || eans.isEmpty()) {
            return new BulkAvgCurrentResponse(currency.name(), Math.max(0, monthsSince), List.of());
        }
        BulkAvgCurrentRequest req = new BulkAvgCurrentRequest(eans, Math.max(0, monthsSince), currency != null ? currency.name() : null);

        byte[] bytes = this.http.post()
                .uri("/prices/avg-current/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .block();

        try {
            return this.om.readValue(bytes, BulkAvgCurrentResponse.class);
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse bulk avg-current response: " + new String(bytes, StandardCharsets.UTF_8), e);
        }
    }

    /**
     * Bequeme Map-Variante: nur gefundene Werte (found==true) werden aufgenommen.
     */
    public Map<String, BigDecimal> getAvgCurrentBulkMap(List<String> eans, Currency currency, int monthsSince) {
        BulkAvgCurrentResponse resp = getAvgCurrentBulk(eans, currency, monthsSince);
        if (resp.results() == null) return Map.of();
        return resp.results().stream()
                .filter(AvgEntry::found)
                .collect(Collectors.toMap(AvgEntry::ean, AvgEntry::value, (a, b) -> a, LinkedHashMap::new)); // Request-Reihenfolge bewahren
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

    // --- AVG per-GB RAM ---
    public Optional<BigDecimal> getAveragePerGbRam(HardwareTypes.RamType type, Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/ram/avg-per-gb", b -> {
            b.queryParam("type", type.name());
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    public Optional<BigDecimal> getAveragePerGbRamBySpeed(HardwareTypes.RamType type, long speedMtps, Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/ram/avg-per-gb/by-speed", b -> {
            b.queryParam("type", type.name());
            b.queryParam("speed", speedMtps);
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    // --- AVG Mainboard ---
    public Optional<BigDecimal> getAverageMainboard(HardwareTypes.CpuSocket socket, HardwareTypes.Chipset chipset,
                                                    HardwareTypes.MotherboardFormFactor formFactor,
                                                    Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/mainboard/avg", b -> {
            b.queryParam("socket", socket.name());
            b.queryParam("chipset", chipset.name());
            b.queryParam("formFactor", formFactor.name());
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    // --- AVG PSU ---
    public Optional<BigDecimal> getAveragePsu(long wattage, HardwareTypes.PsuEfficiencyRating rating,
                                              HardwareTypes.PSU_MODULARITY modularity,
                                              Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/psu/avg", b -> {
            b.queryParam("wattage", wattage);
            b.queryParam("rating", rating.name());
            b.queryParam("modularity", modularity.name());
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    // --- AVG GPU ---
    public Optional<BigDecimal> getAverageGpu(String gpuName, Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/gpu/avg", b -> {
            b.queryParam("gpuName", gpuName);
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }


    public Optional<BigDecimal> getMedianPsu(long wattage, HardwareTypes.PsuEfficiencyRating rating,
                                             HardwareTypes.PSU_MODULARITY modularity,
                                             Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/psu/median", b -> {
            b.queryParam("wattage", wattage);
            b.queryParam("rating", rating.name());
            b.queryParam("modularity", modularity.name());
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    public Optional<BigDecimal> getMedianGpu(String gpuName,
                                             Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/gpu/median", b -> {
            b.queryParam("gpuName", gpuName);
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    // shared helper
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

    // Bestehend …
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
        String base = monthsSince > 0 ? "/prices/{ean}/avg-current/{monthsSince}" : "/prices/{ean}/avg-current";

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

    public record PricePoint(java.time.LocalDate sellPrice, java.math.BigDecimal price, String currency) {
    }

    public java.util.List<PricePoint> getPriceSeries(String ean) {
        return http.get()
                .uri("/prices/{ean}/series", ean)
                .exchangeToMono(resp -> readJsonOrError(resp, PricePoint[].class))
                .map(arr -> java.util.Arrays.asList(arr))
                .blockOptional()
                .orElseGet(java.util.List::of);
    }

    public java.util.List<PricePoint> getRecentPriceSeries(String ean) {
        return getRecentPriceSeries(ean, 3);
    }

    public java.util.List<PricePoint> getRecentPriceSeries(String ean, int monthsSince) {
        String path = monthsSince > 0 ? "/prices/{ean}/series/recent/{monthsSince}" : "/prices/{ean}/series/recent";
        return http.get()
                .uri(path, ean, monthsSince)
                .exchangeToMono(resp -> readJsonOrError(resp, PricePoint[].class))
                .map(arr -> java.util.Arrays.asList(arr))
                .blockOptional()
                .orElseGet(java.util.List::of);
    }

    /* =========================================================
       ========              Helpers                     =======
       ========================================================= */

    private static ObjectMapper defaultMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .configure(SerializationFeature.WRITE_ENUMS_USING_INDEX, false)
                .configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true)
                .configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true)
                .configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static String trimTrailingSlash(String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static byte[] readAllBytes(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read file: " + p, e);
        }
    }

    private Mono<? extends Throwable> toProblem(ClientResponse resp) {
        return resp.bodyToMono(byte[].class)
                .defaultIfEmpty(new byte[0])
                .map(bytes -> {
                    String raw = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

                    // Reason/Status-Text (Spring 6-kompatibel)
                    String reason = (resp.statusCode() instanceof org.springframework.http.HttpStatus hs)
                            ? hs.getReasonPhrase()
                            : resp.statusCode().toString();

                    // Versuche RFC7807-Felder rauszuziehen
                    String title = null, detail = null, type = null, instance = null, code = null;
                    try {
                        com.fasterxml.jackson.databind.JsonNode n = om.readTree(bytes);
                        if (n != null && !n.isMissingNode() && !n.isNull()) {
                            title = optText(n, "title");
                            detail = optText(n, "detail");
                            type = optText(n, "type");
                            instance = optText(n, "instance");
                            code = optText(n, "code");
                        }
                    } catch (Exception ignore) { /* Body war kein JSON, raw verwenden */ }

                    // Baue eine aussagekräftige Fehlermeldung
                    String msg = "HTTP " + resp.statusCode().value() + " " + reason
                            + (title != null ? " | title=" + title : "")
                            + (code != null ? " | code=" + code : "")
                            + (instance != null ? " | instance=" + instance : "")
                            + (detail != null ? " | detail=" + detail : " | body=" + raw);

                    // Optional: Loggen
                    LOGGER.log(Level.SEVERE, "GET failed: " + msg + "\n" + raw);

                    // Exception mit Body zurückgeben
                    return org.springframework.web.reactive.function.client.WebClientResponseException.create(
                            resp.statusCode().value(),
                            reason,
                            resp.headers().asHttpHeaders(),
                            raw.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            java.nio.charset.StandardCharsets.UTF_8
                    );
                });
    }

    private static String optText(com.fasterxml.jackson.databind.JsonNode n, String field) {
        com.fasterxml.jackson.databind.JsonNode x = n.get(field);
        return (x != null && !x.isNull()) ? x.asText() : null;
    }


    // ---------- Neues, einheitliches 2xx/Error-Handling ----------
    private static boolean isJson(MediaType ct) {
        if (ct == null) return false;
        if (MediaType.APPLICATION_JSON.isCompatibleWith(ct)) return true;
        // deckt z.B. application/problem+json oder application/vnd.api+json ab
        return ct.getSubtype() != null && ct.getSubtype().toLowerCase().contains("json");
    }

    private <T> Mono<T> readJsonOrError(ClientResponse resp, Class<T> cls) {
        if (!resp.statusCode().is2xxSuccessful()) {
            return toProblem(resp).flatMap(Mono::error);
        }
        MediaType ct = resp.headers().contentType().orElse(null);
        if (isJson(ct)) {
            return resp.bodyToMono(cls)
                    .onErrorResume(e -> resp.bodyToMono(String.class).defaultIfEmpty("<empty body>").flatMap(body -> {
                        LOGGER.warning(() -> "JSON decode error (" + e.getClass().getSimpleName() + "): " + e.getMessage()
                                + " | contentType=" + ct + " | body=" + truncate(body, 4000));
                        return Mono.error(new IllegalStateException("JSON decode error, contentType=" + ct
                                + ", body=" + truncate(body, 4000), e));
                    }));
        }
        // 2xx aber kein JSON -> Body als Text lesen, loggen und klare Exception werfen
        return resp.bodyToMono(String.class).defaultIfEmpty("<empty body>").flatMap(body -> {
            String msg = "Unexpected successful response with non-JSON contentType="
                    + ct + ", status=" + resp.statusCode()
                    + ", body=" + truncate(body, 4000);
            LOGGER.warning(msg);
            return Mono.error(new IllegalStateException(msg));
        });
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }
    // ---------- Ende: neues Handling ----------

    private String uriBuilder(String path, java.util.function.Consumer<UriBuilder> c, Object... vars) {
        var factory = new DefaultUriBuilderFactory(urlApiV1);
        var builder = factory.builder().path(path);
        c.accept(builder);
        String abs = builder.build(vars).toString();
        return abs.replaceFirst("^" + java.util.regex.Pattern.quote(urlApiV1), "");
    }

    private String uriBuilder(String path, java.util.function.Consumer<UriBuilder> c) {
        var factory = new DefaultUriBuilderFactory(urlApiV1);
        var builder = factory.builder().path(path);
        c.accept(builder);
        String abs = builder.build().toString();
        return abs.replaceFirst("^" + java.util.regex.Pattern.quote(urlApiV1), "");
    }

    private <T> PageResponse<T> deserializePage(byte[] bytes, Class<T> cls) {
        try {
            JsonNode n = om.readTree(bytes);
            List<T> content = new ArrayList<>();
            for (JsonNode item : n.withArray("content")) {
                content.add(om.treeToValue(item, cls));
            }
            int number = n.path("number").asInt();
            int size = n.path("size").asInt();
            int totalPages = n.path("totalPages").asInt();
            long totalElements = n.path("totalElements").asLong();
            return new PageResponse<>(content, number, size, totalPages, totalElements);
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse page response: " + new String(bytes, StandardCharsets.UTF_8), e);
        }
    }

    private BulkResult parseBulkResult(byte[] bytes) {
        try {
            JsonNode n = om.readTree(bytes);
            int saved = n.path("savedCount").asInt(0);
            int failed = n.has("failedCount") ? n.path("failedCount").asInt(0) : 0;
            List<BulkError> errs = new ArrayList<>();
            if (n.has("errors") && n.get("errors").isArray()) {
                for (JsonNode e : n.get("errors")) {
                    errs.add(new BulkError(
                            e.path("index").asInt(-1),
                            e.path("error").asText("")
                    ));
                }
            }
            return new BulkResult(saved, failed, errs);
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse bulk upload response: " + new String(bytes, StandardCharsets.UTF_8), e);
        }
    }

    public record PagesMeta(long totalElements, int pageSize, int totalPages) {
    }

    private Mono<PagesMeta> fetchPagesMetaViaGet(String type, int size) {
        String getUri = uriBuilder("/specs/{type}/pages", b -> b.queryParam("size", size), type);
        return http.get()
                .uri(getUri)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
                .map(n -> new PagesMeta(
                        n.path("totalElements").asLong(-1),
                        n.path("pageSize").asInt(size),
                        n.path("totalPages").asInt(-1)
                ));
    }

    private static long parseLongHeader(HttpHeaders h, String name, long def) {
        try {
            return Long.parseLong(h.getFirst(name));
        } catch (Exception e) {
            return def;
        }
    }

    private static int parseIntHeader(HttpHeaders h, String name, int def) {
        try {
            return Integer.parseInt(h.getFirst(name));
        } catch (Exception e) {
            return def;
        }
    }

    public record PageResponse<T>(List<T> content, int number, int size, int totalPages, long totalElements) {
    }

    public record TypeMeta(String type, String entityClass, long count, Instant lastUpdated) {
    }

    public record BulkResult(int savedCount, int failedCount, List<BulkError> errors) {
    }

    public record BulkError(int index, String error) {
    }

    public record BulkAvgCurrentRequest(
            List<String> eans,
            Integer monthsSince,
            String currency // z.B. "EUR", "USD"
    ) {
    }

    public record BulkAvgCurrentResponse(
            String currency,
            int monthsSince,
            List<AvgEntry> results
    ) {
    }

    public record AvgEntry(
            String ean,
            BigDecimal value,
            boolean found
    ) {
    }

    public static final class ApiProblemException extends RuntimeException {
        private final ProblemDetail problem;

        public ApiProblemException(ProblemDetail p) {
            super(p.getDetail() != null ? p.getDetail() : String.valueOf(p.getTitle()));
            this.problem = p;
        }

        public ProblemDetail getProblem() {
            return problem;
        }
    }

    static final class ByteArrayResourceWithFilename extends org.springframework.core.io.ByteArrayResource {
        private final String filename;

        ByteArrayResourceWithFilename(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    public static void main(String[] args) {
        var client = new HardwareSpecClient("http://localhost:5050");

        client.priceItemUpload(Set.of(
                new PricePointUploadDto(
                        "ebay.de",

                        "1234",
                        "123456789",
                        BigDecimal.ONE,
                        Currency.EURO,
                        LocalDate.now()
                )
        ));
    }
}

