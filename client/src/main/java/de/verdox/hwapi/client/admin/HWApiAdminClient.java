package de.verdox.hwapi.client.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.hwapi.client.HWApiClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Client für /api/v1/admin/*
 * Nutzt die robuste Fehlerbehandlung aus HWApiClient (readJsonOrError / toProblem).
 */
public class HWApiAdminClient extends HWApiClient {

    public HWApiAdminClient(String baseUrl) {
        super(baseUrl);
    }

    public HWApiAdminClient(String baseUrl, ObjectMapper mapper) {
        super(baseUrl, mapper);
    }

    // ---------------------------------------------------------------------
    // Endpoints
    // ---------------------------------------------------------------------

    /** GET /api/v1/admin/status */
    public Mono<HardwareAdminDtos.BackendStatus> getStatus() {
        return http.get()
                .uri("/admin/status")
                .exchangeToMono(resp -> readJsonOrError(resp, HardwareAdminDtos.BackendStatus.class));
    }

    public Mono<HardwareAdminDtos.AwinFetchStatus> getAwinStatus() {
        return http.get()
                .uri("/admin/awin/status")
                .exchangeToMono(resp -> readJsonOrError(resp, HardwareAdminDtos.AwinFetchStatus.class));
    }

    /** GET /api/v1/admin/stats */
    public Mono<HardwareAdminDtos.BackendStats> getStats() {
        return http.get()
                .uri("/admin/stats")
                .exchangeToMono(resp -> readJsonOrError(resp, HardwareAdminDtos.BackendStats.class));
    }

    /**
     * POST /api/v1/admin/scraping/restart
     * - 202 Accepted => started
     * - 409 Conflict => already running
     */
    public Mono<HardwareAdminDtos.ActionResult> restartScraping() {
        return http.post()
                .uri("/admin/scraping/restart")
                .exchangeToMono(resp -> readActionResultOrProblem(resp, "restartScraping"));
    }

    /**
     * DELETE /api/v1/admin/hardware
     * - 200 OK => deleted
     * - 409 Conflict => blocked (e.g. scraping running)
     */
    public Mono<HardwareAdminDtos.ActionResult> deleteAllHardwareData() {
        return http.delete()
                .uri("/admin/hardware")
                .exchangeToMono(resp -> readActionResultOrProblem(resp, "deleteAllHardwareData"));
    }

    // ---------------------------------------------------------------------
    // Convenience blocking wrappers (für Vaadin ok; bitte in CompletableFuture.runAsync nutzen)
    // ---------------------------------------------------------------------

    public HardwareAdminDtos.BackendStatus getStatusBlocking() {
        return getStatus().block();
    }

    public HardwareAdminDtos.BackendStats getStatsBlocking() {
        return getStats().block();
    }

    public HardwareAdminDtos.ActionResult restartScrapingBlocking() {
        return restartScraping().block();
    }

    public HardwareAdminDtos.ActionResult deleteAllHardwareDataBlocking() {
        return deleteAllHardwareData().block();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Für Action-Endpunkte wollen wir bei 409 (oder generell 4xx/5xx) die Problem-Details
     * sauber als Exception (readJsonOrError/toProblem) – aber bei 409 zusätzlich gern
     * eine “freundliche” Meldung, wenn der Server ActionResult liefert.
     *
     * -> Wenn Server bei 409 JSON ActionResult liefert, lesen wir ihn.
     * -> Wenn Server RFC7807 problem+json liefert, landet es in toProblem().
     */
    private Mono<HardwareAdminDtos.ActionResult> readActionResultOrProblem(ClientResponse resp, String opName) {
        if (resp.statusCode().is2xxSuccessful()) {
            return readJsonOrError(resp, HardwareAdminDtos.ActionResult.class);
        }

        // Speziell 409: falls der Server HardwareAdminDtos.ActionResult zurückgibt, wollen wir ihn lesen,
        // sonst fallback auf RFC7807 via toProblem().
        if (resp.statusCode().value() == HttpStatus.CONFLICT.value()) {
            return resp.headers().contentType()
                    .map(ct -> isJson(ct))
                    .orElse(false)
                    ? resp.bodyToMono(HardwareAdminDtos.ActionResult.class)
                    .switchIfEmpty(Mono.just(new HardwareAdminDtos.ActionResult(false, "Conflict")))
                    : toProblem(resp).flatMap(Mono::error);
        }

        // Default: RFC7807/Body -> Exception
        return toProblem(resp).flatMap(Mono::error);
    }
}
