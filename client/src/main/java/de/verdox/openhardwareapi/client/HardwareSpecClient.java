package de.verdox.openhardwareapi.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.openhardwareapi.model.CPU;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Eine einzige Klasse für alle REST-Calls. Einfach mit Base-URL instanziieren.
 */
public final class HardwareSpecClient {
    private static final Logger LOGGER = Logger.getLogger(HardwareSpecClient.class.getName());
    private static final String API_V1 = "/api/v1";

    private final WebClient http;
    @Getter
    private final String urlApiV1;
    private final ObjectMapper om;

    /**
     * Baut einen Client mit sinnvollen Defaults (Timeout 15s, JSON, Accept: application/json).
     */
    public HardwareSpecClient(String baseUrl) {
        this(baseUrl, defaultMapper());
    }

    /**
     * Variante mit eigenem ObjectMapper (falls ihr z.B. Module/Features custom setzen wollt).
     */
    public HardwareSpecClient(String baseUrl, ObjectMapper mapper) {
        this.urlApiV1 = baseUrl + API_V1;
        LOGGER.info("HardwareSpecClient created for base url " + baseUrl + " and api v1 endpoint " + trimTrailingSlash(this.urlApiV1));
        this.om = mapper;
        var httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(15));
        this.http = WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder().codecs(clientCodecConfigurer -> clientCodecConfigurer.defaultCodecs().maxInMemorySize((int) DataSize.ofMegabytes(16).toBytes())).build())
                .baseUrl(trimTrailingSlash(this.urlApiV1))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper)))
                .build();
    }

    /* =========================================================
       ========    READ: LIST, GET ONE, TYPES            =======
       ========================================================= */

    /**
     * Listet eine Seite von Entities eines Typs (ohne Filter).
     */
    public <T> PageResponse<T> list(String type, int page, int size, String sort, Class<T> entityClass) {
        var uri = uriBuilder("/specs/{type}", b -> {
            b.queryParam("page", page);
            b.queryParam("size", size);
            if (sort != null && !sort.isBlank()) b.queryParam("sort", sort);
        }, type);

        var bytes = http.get().uri(uri)
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), this::toProblem)
                .bodyToMono(byte[].class)
                .block();

        return deserializePage(bytes, entityClass);
    }

    /**
     * Holt ein einzelnes Objekt.
     */
    public <T> T getOne(String ean, String type, Class<T> entityClass) {
        return http.get()
                .uri("/specs/byEan/{ean}/{type}", ean, type)
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), this::toProblem)
                .bodyToMono(entityClass)
                .block();
    }

    /**
     * Liefert nur die registrierten Type-Namen.
     */
    public List<String> listTypes() {
        return http.get()
                .uri("/specs/types")
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), this::toProblem)
                .bodyToMono(List.class)
                .blockOptional()
                .orElseGet(List::of);
    }

    /**
     * Liefert Typs + Stats. Entspricht GET /specs/types?stats=true
     */
    public List<TypeMeta> listTypesWithStats() {
        return http.get()
                .uri(uriBuilder("/specs/types", b -> b.queryParam("stats", true)))
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), this::toProblem)
                .bodyToMono(List.class)
                .blockOptional()
                .orElseGet(List::of);
    }

    /* =========================================================
       ========           WRITE: UPLOADS                 =======
       ========================================================= */

    /**
     * Single-Upload: POST /specs/{type} mit Entity als JSON.
     */
    public <T> T uploadOne(String type, T entity, Class<T> entityClass) {
        return http.post()
                .uri("/specs/{type}", type)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(entity)
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), this::toProblem)
                .bodyToMono(entityClass)
                .block();
    }

    /**
     * Bulk-Upload mit JSON-Array. allOrNothing=true ⇒ jede Invalidität bricht alles ab.
     */
    public BulkResult bulkUpload(String type, List<?> entities, boolean allOrNothing) {
        return http.post()
                .uri(uriBuilder("/specs/{type}/bulk", b -> b.queryParam("allOrNothing", allOrNothing), type))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(entities)
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), this::toProblem)
                .bodyToMono(BulkResult.class)
                .block();
    }

    /**
     * Bulk-Upload als NDJSON (eine Entity pro Zeile).
     */
    public BulkResult bulkUploadNdjson(String type, List<?> entities, boolean allOrNothing) {
        String ndjson = entities.stream()
                .map(e -> {
                    try {
                        return om.writeValueAsString(e);
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .collect(Collectors.joining("\n"));

        return http.post()
                .uri(uriBuilder("/specs/{type}/bulk", b -> b.queryParam("allOrNothing", allOrNothing), type))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ndjson) // Server akzeptiert JSON-Array ODER NDJSON
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), this::toProblem)
                .bodyToMono(BulkResult.class)
                .block();
    }

    /**
     * Bulk-Upload via Datei (multipart/form-data). Inhalt darf JSON-Array oder NDJSON enthalten.
     */
    public BulkResult bulkUploadFile(String type, Path file, boolean allOrNothing) {
        byte[] data = readAllBytes(file);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResourceWithFilename(data, file.getFileName().toString()));

        return http.post()
                .uri(uriBuilder("/specs/{type}/bulkFile", b -> b.queryParam("allOrNothing", allOrNothing), type))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form))
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), this::toProblem)
                .bodyToMono(BulkResult.class)
                .block();
    }

    public PagesMeta pages(String type, int size) {
        // 1) HEAD /specs/{type}?size=...
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
                        // Server liefert HEAD ohne Header? -> Fallback auf GET
                        return fetchPagesMetaViaGet(type, size);
                    }
                    // Fallback auf GET bei 404/405 etc.
                    if (status == HttpStatus.NOT_FOUND || status == HttpStatus.METHOD_NOT_ALLOWED) {
                        return fetchPagesMetaViaGet(type, size);
                    }
                    return toProblem(resp).flatMap(Mono::error);
                })
                .block();
    }

    /* =========================================================
       ========              Helpers                     =======
       ========================================================= */

    private static ObjectMapper defaultMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
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
        return resp.bodyToMono(ProblemDetail.class)
                .defaultIfEmpty(ProblemDetail.forStatus(resp.statusCode()))
                .map(ApiProblemException::new);
    }

    /**
     * Kleine Convenience: URI zusammenbauen mit QueryParams.
     */
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

    /**
     * Deserialisiert die Spring-Page-Struktur zu einem schlanken PageResponse<T>.
     */
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

    // ===== Transport-Typ =====
    public record PagesMeta(long totalElements, int pageSize, int totalPages) {
    }


    // ===== Fallback GET /specs/{type}/pages?size=... =====
    private Mono<PagesMeta> fetchPagesMetaViaGet(String type, int size) {
        String getUri = uriBuilder("/specs/{type}/pages", b -> b.queryParam("size", size), type);
        return http.get()
                .uri(getUri)
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), this::toProblem)
                .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
                .map(n -> new PagesMeta(
                        n.path("totalElements").asLong(-1),
                        n.path("pageSize").asInt(size),
                        n.path("totalPages").asInt(-1)
                ));
    }

    // ===== kleine Header-Parser =====
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


    /* ======== Transport-Datentypen (leichtgewichtig) ======== */

    /**
     * Schlanke Page-Repräsentation kompatibel zu Spring Page JSON.
     */
    public record PageResponse<T>(List<T> content, int number, int size, int totalPages, long totalElements) {
    }

    /**
     * /specs/types?stats=true Antwort-Zeile.
     */
    public record TypeMeta(String type, String entityClass, long count, Instant lastUpdated) {
    }

    /**
     * Ergebnis vom Bulk-Upload (Server gibt savedCount/failedCount/errors aus).
     */
    public record BulkResult(int savedCount, int failedCount, List<BulkError> errors) {
    }

    public record BulkError(int index, String error) {
    }

    /**
     * Exception, wenn Server ein ProblemDetail liefert.
     */
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

    /**
     * Multipart Resource mit Dateiname.
     */
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

        List<CPU> cpu = client.list("cpu", 0, 100, "id,asc", CPU.class).content();
        System.out.println("Received: "+cpu.size());

        if(!cpu.isEmpty()) {
            System.out.println("Uploading: "+cpu);
            client.uploadOne("cpu", cpu.getFirst(), CPU.class);
        }

    }
}
