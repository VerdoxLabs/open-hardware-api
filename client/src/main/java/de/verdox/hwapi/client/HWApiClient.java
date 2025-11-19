package de.verdox.hwapi.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Getter;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class HWApiClient {
    protected static final Logger LOGGER = Logger.getLogger(HardwareSpecClient.class.getName());

    protected final WebClient http;
    @Getter
    protected final String urlApiV1;
    protected final ObjectMapper om;

    public HWApiClient(String baseUrl) {
        this(baseUrl, defaultMapper());
    }

    public HWApiClient(String baseUrl, ObjectMapper mapper) {
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
                .responseTimeout(Duration.ofSeconds(60))
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

    private static ObjectMapper defaultMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .configure(SerializationFeature.WRITE_ENUMS_USING_INDEX, false)
                .configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true)
                .configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true)
                .configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    protected String trimTrailingSlash(String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    protected byte[] readAllBytes(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read file: " + p, e);
        }
    }

    protected Mono<? extends Throwable> toProblem(ClientResponse resp) {
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

    protected String optText(com.fasterxml.jackson.databind.JsonNode n, String field) {
        com.fasterxml.jackson.databind.JsonNode x = n.get(field);
        return (x != null && !x.isNull()) ? x.asText() : null;
    }

    protected boolean isJson(MediaType ct) {
        if (ct == null) return false;
        if (MediaType.APPLICATION_JSON.isCompatibleWith(ct)) return true;
        // deckt z.B. application/problem+json oder application/vnd.api+json ab
        return ct.getSubtype() != null && ct.getSubtype().toLowerCase().contains("json");
    }

    protected <T> Mono<T> readJsonOrError(ClientResponse resp, Class<T> cls) {
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

    protected static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }

    protected String uriBuilder(String path, java.util.function.Consumer<UriBuilder> c, Object... vars) {
        var factory = new DefaultUriBuilderFactory(urlApiV1);
        var builder = factory.builder().path(path);
        c.accept(builder);
        String abs = builder.build(vars).toString();
        return abs.replaceFirst("^" + java.util.regex.Pattern.quote(urlApiV1), "");
    }

    protected String uriBuilder(String path, java.util.function.Consumer<UriBuilder> c) {
        var factory = new DefaultUriBuilderFactory(urlApiV1);
        var builder = factory.builder().path(path);
        c.accept(builder);
        String abs = builder.build().toString();
        return abs.replaceFirst("^" + java.util.regex.Pattern.quote(urlApiV1), "");
    }

    protected HardwareSpecClient.BulkResult parseBulkResult(byte[] bytes) {
        try {
            JsonNode n = om.readTree(bytes);
            int saved = n.path("savedCount").asInt(0);
            int failed = n.has("failedCount") ? n.path("failedCount").asInt(0) : 0;
            List<HardwareSpecClient.BulkError> errs = new ArrayList<>();
            if (n.has("errors") && n.get("errors").isArray()) {
                for (JsonNode e : n.get("errors")) {
                    errs.add(new HardwareSpecClient.BulkError(
                            e.path("index").asInt(-1),
                            e.path("error").asText("")
                    ));
                }
            }
            return new HardwareSpecClient.BulkResult(saved, failed, errs);
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse bulk upload response: " + new String(bytes, StandardCharsets.UTF_8), e);
        }
    }
}
