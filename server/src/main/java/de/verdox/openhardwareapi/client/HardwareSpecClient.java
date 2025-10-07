package de.verdox.openhardwareapi.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.openhardwareapi.model.CPU;
import de.verdox.openhardwareapi.model.GPU;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
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
import java.util.stream.Collectors;

/**
 * Eine einzige Klasse für alle REST-Calls. Einfach mit Base-URL instanziieren.
 */
public final class HardwareSpecClient {

    private final WebClient http;
    private final String baseUrl;
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
        this.baseUrl = baseUrl;
        this.om = mapper;
        var httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(15));
        this.http = WebClient.builder()
                .baseUrl(trimTrailingSlash(baseUrl))
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
        var factory = new DefaultUriBuilderFactory(baseUrl); // <---- baseUrl direkt verwenden
        var builder = factory.builder().path(path);
        c.accept(builder);
        // Wir geben nur den relativen Teil zurück, weil WebClient die baseUrl schon kennt
        String abs = builder.build(vars).toString();
        return abs.replaceFirst("^" + java.util.regex.Pattern.quote(baseUrl), "");
    }

    private String uriBuilder(String path, java.util.function.Consumer<UriBuilder> c) {
        var factory = new DefaultUriBuilderFactory(baseUrl);
        var builder = factory.builder().path(path);
        c.accept(builder);
        String abs = builder.build().toString();
        return abs.replaceFirst("^" + java.util.regex.Pattern.quote(baseUrl), "");
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
        var client = new HardwareSpecClient("http://localhost:5050/api/v1");

        List<String> types = client.listTypes();
        System.out.println(types);

        var page = client.list("cpu", 0, 100, "id,asc", CPU.class);
        System.out.println(page);

        CPU cpu = client.getOne("0730143314442","cpu", CPU.class);
        System.out.println(cpu);

       // GPU saved = client.uploadOne("gpu", gpu, GPU.class);

    }
}
