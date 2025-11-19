package de.verdox.hwapi.priceapi.component.controller;

import de.verdox.hwapi.model.dto.PricePointUploadDto;
import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.priceapi.component.service.EbayCompletedListingsService;
import de.verdox.hwapi.priceapi.model.RemoteSoldItem;
import de.verdox.hwapi.priceapi.repository.RemoteSoldItemRepository;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/v1/prices/sold")
public class APIPricesController {
    private static final Logger LOGGER = Logger.getLogger(APIPricesController.class.getName());
    private static final int MAX_BULK_SIZE = 250;
    private final EbayCompletedListingsService service;

    public APIPricesController(EbayCompletedListingsService service) {
        this.service = service;
    }

    /**
     * Bulk-Upload: mehrere Price Points
     * POST /api/v1/prices/{ean}/points
     */
    @PostMapping("/points")
    public ResponseEntity<BulkResult> uploadPricePoints(
            @RequestBody List<PricePointUploadDto> body
    ) {
        if (body == null || body.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var saved = service.createAll(body);
        var ids = saved.stream().map(RemoteSoldItem::getUuid).toList();
        var result = new BulkResult(body.size(), saved.size(), ids);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // --- AVG CURRENT ---
    // Default 3 Monate, optional ?currency=EUR
    @GetMapping("/{ean}/avg-current")
    public ResponseEntity<BigDecimal> getAvgCurrentDefault(
            @PathVariable String ean,
            @RequestParam(value = "currency", required = false) String currency) {
        return service.getCurrentAveragePriceForEan(ean, currency != null ? Currency.findCurrency(currency) : Currency.US_DOLLAR, 3)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // Mit monthsSince, optional ?currency=EUR
    @GetMapping("/{ean}/avg-current/{monthsSince}")
    public ResponseEntity<BigDecimal> getAvgCurrent(
            @PathVariable String ean,
            @PathVariable @Min(0) int monthsSince,
            @RequestParam(value = "currency", required = false) String currency) {
        return service.getCurrentAveragePriceForEan(ean, currency != null ? Currency.findCurrency(currency) : Currency.US_DOLLAR, monthsSince)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // --- SERIES (alle Daten) ---
    @GetMapping("/{ean}/series")
    public List<RemoteSoldItemRepository.PricePoint> getSeries(@PathVariable String ean) {
        return service.getAllPricesForEan(ean);
    }

    // --- SERIES RECENT ---

    // Ohne monthsSince → Default 3
    @GetMapping("/{ean}/series/recent")
    public List<RemoteSoldItemRepository.PricePoint> getSeriesRecentDefault(@PathVariable String ean) {
        return service.getRecentPricesForEan(ean, 3);
    }

    // Mit monthsSince
    @GetMapping("/{ean}/series/recent/{monthsSince}")
    public List<RemoteSoldItemRepository.PricePoint> getSeriesRecent(
            @PathVariable String ean,
            @PathVariable int monthsSince) {

        return service.getRecentPricesForEan(ean, monthsSince);
    }


    /**
     * BULK: avg-current für viele EANs
     * POST /api/v1/prices/avg-current/bulk
     * <p>
     * Beispiele:
     * 1) Nur EAN-Liste (defaults: USD, monthsSince=3):
     * Body: ["4012345678901", "4023456789012"]
     * <p>
     * 2) Mit Parametern:
     * Body: { "eans": ["401...", "402..."], "monthsSince": 6, "currency": "EUR" }
     */
    @PostMapping("/avg-current/bulk")
    public ResponseEntity<BulkAvgCurrentResponse> getAvgCurrentBulk(
            @RequestBody Object body
    ) {
        BulkAvgCurrentRequest req = coerceToBulkRequest(body);

        if (req.eans() == null || req.eans().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (req.eans().size() > MAX_BULK_SIZE) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }

        final int months = req.monthsSince() != null ? Math.max(0, req.monthsSince()) : 3;
        final Currency currency = req.currency() != null
                ? Currency.findCurrency(req.currency())
                : Currency.US_DOLLAR;

        // stabile Reihenfolge wie Request
        List<AvgEntry> results = new ArrayList<>(req.eans().size());
        for (String ean : req.eans()) {
            if (ean == null || ean.isBlank()) {
                results.add(new AvgEntry(null, null, false));
                continue;
            }
            var opt = service.getCurrentAveragePriceForEan(ean, currency, months);
            results.add(new AvgEntry(ean, opt.orElse(null), opt.isPresent()));
        }

        return ResponseEntity.ok(new BulkAvgCurrentResponse(currency.name(), months, results));
    }

    @PostMapping("/lookup")
    public ResponseEntity<PriceLookupResponse> lookupPrice(@RequestBody PriceLookupRequest body) {
        if (body == null || body.ean() == null || body.ean().isBlank()) {
            return ResponseEntity.badRequest().build();
        }


        Currency currency = body.currency() != null
                ? body.currency()
                : Currency.US_DOLLAR;

        var result = service.lookupPriceNow(body.ean(), currency);


        PriceLookupResponse response = new PriceLookupResponse(
                result.ean(),
                currency.name(),
                result.status().name(),
                result.value()
        );

        if (result.status() == EbayCompletedListingsService.PriceLookupStatus.FOUND) {
            return ResponseEntity.ok(response);
        }
        if (result.status() == EbayCompletedListingsService.PriceLookupStatus.NOT_FOUND_CACHED_24H) {
            // 429 oder 204 ist Geschmackssache – hier 429 als "zu viele Versuche / blockiert"
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
        // NOT_FOUND (neue Suche gemacht, aber immer noch nichts)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(response);
    }

    /**
     * Erlaubt sowohl eine pure String-Liste als Body als auch ein Objekt mit Feldern:
     * { "eans": [...], "monthsSince": 3, "currency": "EUR" }
     */
    @SuppressWarnings("unchecked")
    private BulkAvgCurrentRequest coerceToBulkRequest(Object body) {
        if (body instanceof List<?> list) {
            // Liste von EANs
            List<String> eans = list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
            return new BulkAvgCurrentRequest(eans, null, null);
        }
        if (body instanceof Map<?, ?> map) {
            Object eansObj = map.get("eans");
            List<String> eans;
            if (eansObj instanceof List<?> l) {
                eans = l.stream().filter(Objects::nonNull).map(Object::toString).toList();
            } else {
                eans = List.of();
            }
            Integer months = null;
            Object m = map.get("monthsSince");
            if (m != null) {
                try {
                    months = Integer.parseInt(m.toString());
                } catch (NumberFormatException ignored) {
                }
            }
            String currency = null;
            Object c = map.get("currency");
            if (c != null) currency = c.toString();
            return new BulkAvgCurrentRequest(eans, months, currency);
        }
        // Falsches Format
        return new BulkAvgCurrentRequest(List.of(), null, null);
    }

    public record BulkAvgCurrentRequest(
            List<String> eans,
            Integer monthsSince,
            String currency
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

    public record BulkResult(
            int total,
            int created,
            List<UUID> ids
    ) {
    }

    public record PriceLookupRequest(
            String ean,
            Currency currency
    ) {
    }

    public record PriceLookupResponse(
            String ean,
            String currency,
            String status,   // FOUND / NOT_FOUND / NOT_FOUND_CACHED_24H
            BigDecimal value // null falls kein Preis
    ) {
    }
}
