package de.verdox.hwapi.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.verdox.hwapi.benchmarkapi.entity.CPUBenchmarkResults;
import de.verdox.hwapi.benchmarkapi.entity.GPUBenchmarkResults;
import de.verdox.hwapi.model.HardwareTypes;
import de.verdox.hwapi.model.dto.PricePointUploadDto;
import de.verdox.hwapi.model.values.Currency;
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

public final class HardwareSpecClient extends HWApiClient {
    private static final Logger LOGGER = Logger.getLogger(HardwareSpecClient.class.getName());

    public HardwareSpecClient(String baseUrl) {
        super(baseUrl);
    }

    public HardwareSpecClient(String baseUrl, ObjectMapper mapper) {
        super(baseUrl, mapper);
    }

    public Optional<CPUBenchmarkResults> getCpuBenchmark(String cpuModelName) {
        String uri = uriBuilder("/benchmark/cpu", b -> {
            if (cpuModelName != null && !cpuModelName.isBlank()) {
                b.queryParam("cpuModelName", cpuModelName);
            }
        });

        byte[] bytes = http.get()
                .uri(uri)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
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
            return Optional.of(om.treeToValue(node, CPUBenchmarkResults.class));
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse CPU benchmark response: " +
                    new String(bytes, StandardCharsets.UTF_8), e);
        }
    }

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

    public Optional<GPUBenchmarkResults> getGpuBenchmark(String gpuCanonicalName) {
        String uri = uriBuilder("/benchmark/gpu", b -> {
            if (gpuCanonicalName != null && !gpuCanonicalName.isBlank()) {
                b.queryParam("gpuCanonicalName", gpuCanonicalName);
            }
        });

        byte[] bytes = http.get()
                .uri(uri)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
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
            return Optional.of(om.treeToValue(node, GPUBenchmarkResults.class));
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse GPU benchmark response: " +
                    new String(bytes, StandardCharsets.UTF_8), e);
        }
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
}

