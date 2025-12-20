package de.verdox.hwapi.priceapi.component.controller;

import de.verdox.hwapi.client.PriceSeriesResponseDTO;
import de.verdox.hwapi.hardwareapi.component.service.HardwareSpecService;
import de.verdox.hwapi.model.HardwareSpec;
import de.verdox.hwapi.model.dto.PricePointUploadDto;
import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.model.values.ItemCondition;
import de.verdox.hwapi.priceapi.component.service.ebay.EbayCompletedListingsService;
import de.verdox.hwapi.priceapi.component.service.ItemPriceService;
import de.verdox.hwapi.priceapi.model.RemoteSoldItem;
import de.verdox.hwapi.priceapi.repository.RemoteSoldItemRepository;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/v1/prices/sold")
public class APIPricesController {
    private static final Logger LOGGER = Logger.getLogger(APIPricesController.class.getName());
    private static final int MAX_BULK_SIZE = 250;

    private final EbayCompletedListingsService service;
    private final ItemPriceService itemPriceService;
    private final HardwareSpecService hardwareSpecService;

    public APIPricesController(EbayCompletedListingsService service,
                               ItemPriceService itemPriceService,
                               HardwareSpecService hardwareSpecService) {
        this.service = service;
        this.itemPriceService = itemPriceService;
        this.hardwareSpecService = hardwareSpecService;
    }

    // ------------------------------------------------------------
    // Upload
    // ------------------------------------------------------------

    @PostMapping("/points")
    public ResponseEntity<BulkResult> uploadPricePoints(@RequestBody List<PricePointUploadDto> body) {
        if (body == null || body.isEmpty()) return ResponseEntity.badRequest().build();

        var saved = service.createAll(body);
        var ids = saved.stream().map(RemoteSoldItem::getUuid).toList();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new BulkResult(body.size(), saved.size(), ids));
    }

    // ------------------------------------------------------------
    // ✅ NEW: Bulk series by exact identifiers (MPN/EAN)
    // ------------------------------------------------------------

    @PostMapping("/series/fetchActive/bulkByIds")
    public ResponseEntity<BulkSeriesResponseV2> getSeriesForActiveBulkByIds(
            @RequestBody BulkSeriesRequestV2 req
    ) {
        return handleBulkSeriesByIds(SeriesKind.ACTIVE, req);
    }

    @PostMapping("/series/fetchCompleted/bulkByIds")
    public ResponseEntity<BulkSeriesResponseV2> getSeriesForCompletedBulkByIds(
            @RequestBody BulkSeriesRequestV2 req
    ) {
        return handleBulkSeriesByIds(SeriesKind.COMPLETED, req);
    }

    private ResponseEntity<BulkSeriesResponseV2> handleBulkSeriesByIds(
            SeriesKind kind,
            BulkSeriesRequestV2 req
    ) {
        List<String> mpns = Optional.ofNullable(req.mpns()).orElse(List.of());
        List<String> eans = Optional.ofNullable(req.eans()).orElse(List.of());

        // in-request Reihenfolge erhalten (mpns dann eans) – du kannst das auch anders machen,
        // aber: wichtig ist, dass Response deterministisch ist.
        List<String> requested = new ArrayList<>(mpns.size() + eans.size());
        for (String s : mpns) if (s != null && !s.isBlank()) requested.add(urlDecode(s));
        for (String s : eans) if (s != null && !s.isBlank()) requested.add(urlDecode(s));

        if (requested.isEmpty()) return ResponseEntity.badRequest().build();
        if (requested.size() > MAX_BULK_SIZE) return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();

        int monthSince = Optional.ofNullable(req.monthSince()).orElse(12);
        Set<ItemCondition> conditions = Optional.ofNullable(req.conditions())
                .filter(c -> !c.isEmpty())
                .orElse(EnumSet.allOf(ItemCondition.class));
        boolean fetchIfNoData = Optional.ofNullable(req.fetchIfNoData()).orElse(false);

        // 1) Specs in einem Rutsch holen
        //    findAllByEANOrMPN(List<String>) sollte alles matchen können.
        List<HardwareSpec<?>> specs = hardwareSpecService.findAllByEANOrMPN(requested);

        // 2) Index: identifier -> spec (best effort)
        //    Wichtig: ein Spec kann mehrere mpns/eans haben.
        Map<String, HardwareSpec<?>> byId = new HashMap<>(requested.size() * 2);
        for (HardwareSpec<?> s : specs) {
            if (s == null) continue;
            if (s.getMPNs() != null) {
                for (String m : s.getMPNs()) {
                    if (m != null && !m.isBlank()) byId.putIfAbsent(m.trim(), s);
                }
            }
            if (s.getEANs() != null) {
                for (String e : s.getEANs()) {
                    if (e != null && !e.isBlank()) byId.putIfAbsent(e.trim(), s);
                }
            }
        }

        // 3) Results: in exakt der Request-Reihenfolge
        List<SeriesEntryV2> results = new ArrayList<>(requested.size());

        for (String identifier : requested) {
            HardwareSpec<?> spec = byId.get(identifier);

            if (spec == null) {
                results.add(new SeriesEntryV2(identifier, new PriceSeriesResponseDTO(false, List.of())));
                continue;
            }

            PriceSeriesResponseDTO dbResult = (kind == SeriesKind.ACTIVE)
                    ? itemPriceService.fetchActiveSeriesDataFromDB(spec, conditions, monthSince)
                    : itemPriceService.fetchCompletedSeriesDataFromDB(spec, conditions, monthSince);

            if (dbResult != null && dbResult.series() != null && !dbResult.series().isEmpty()) {
                results.add(new SeriesEntryV2(identifier, dbResult));
                continue;
            }

            if (fetchIfNoData) {
                // NOTE: dein fetchSeriesDataFromRemote scheint “generisch” zu sein.
                //       Wenn du remote active/completed trennen willst, mach 2 Methoden draus.
                PriceSeriesResponseDTO remoteJobDto = itemPriceService.fetchSeriesDataFromRemote(spec, false);
                results.add(new SeriesEntryV2(identifier, remoteJobDto != null ? remoteJobDto : new PriceSeriesResponseDTO(false, List.of())));
            } else {
                itemPriceService.addToBackgroundJob(spec);
                results.add(new SeriesEntryV2(identifier, new PriceSeriesResponseDTO(false, List.of())));
            }
        }

        return ResponseEntity.ok(new BulkSeriesResponseV2(results));
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private enum SeriesKind { ACTIVE, COMPLETED }

    // ------------------------------------------------------------
    // Single endpoints (kannst du später auch “byIds” vereinheitlichen)
    // ------------------------------------------------------------

    @GetMapping("/series/fetchActive")
    public ResponseEntity<PriceSeriesResponseDTO> getSeriesForActive(
            @RequestParam(value = "MPNs", required = false) List<String> mpns,
            @RequestParam(value = "EANs", required = false) List<String> eans,
            @RequestParam(value = "conditions") Set<ItemCondition> conditions,
            @RequestParam(value = "monthSince") int monthSince,
            @RequestParam(value = "fetchIfNoData", defaultValue = "false") boolean fetchIfNoData
    ) {
        return getSeriesSingle(SeriesKind.ACTIVE, mpns, eans, conditions, monthSince, fetchIfNoData);
    }

    @GetMapping("/series/fetchCompleted")
    public ResponseEntity<PriceSeriesResponseDTO> getSeriesForCompleted(
            @RequestParam(value = "MPNs", required = false) List<String> mpns,
            @RequestParam(value = "EANs", required = false) List<String> eans,
            @RequestParam(value = "conditions") Set<ItemCondition> conditions,
            @RequestParam(value = "monthSince") int monthSince,
            @RequestParam(value = "fetchIfNoData", defaultValue = "false") boolean fetchIfNoData
    ) {
        return getSeriesSingle(SeriesKind.COMPLETED, mpns, eans, conditions, monthSince, fetchIfNoData);
    }

    private ResponseEntity<PriceSeriesResponseDTO> getSeriesSingle(
            SeriesKind kind,
            List<String> mpns,
            List<String> eans,
            Set<ItemCondition> conditions,
            int monthSince,
            boolean fetchIfNoData
    ) {
        if ((mpns == null || mpns.isEmpty()) && (eans == null || eans.isEmpty())) {
            return ResponseEntity.badRequest().build();
        }

        String first = null;
        if (mpns != null && !mpns.isEmpty()) first = urlDecode(mpns.getFirst());
        else if (eans != null && !eans.isEmpty()) first = urlDecode(eans.getFirst());

        if (first == null || first.isBlank()) return ResponseEntity.badRequest().build();

        HardwareSpec<?> hardwareSpec = hardwareSpecService.findByEANOrMPN(first);
        if (hardwareSpec == null) return ResponseEntity.notFound().build();

        PriceSeriesResponseDTO dbResult = (kind == SeriesKind.ACTIVE)
                ? itemPriceService.fetchActiveSeriesDataFromDB(hardwareSpec, conditions, monthSince)
                : itemPriceService.fetchCompletedSeriesDataFromDB(hardwareSpec, conditions, monthSince);

        if (dbResult != null && dbResult.series() != null && !dbResult.series().isEmpty()) {
            return ResponseEntity.ok(dbResult);
        }

        if (fetchIfNoData) {
            PriceSeriesResponseDTO remoteJobDto = itemPriceService.fetchSeriesDataFromRemote(hardwareSpec, false);
            return ResponseEntity.ok(remoteJobDto != null ? remoteJobDto : new PriceSeriesResponseDTO(false, List.of()));
        }

        itemPriceService.addToBackgroundJob(hardwareSpec);
        return ResponseEntity.ok(new PriceSeriesResponseDTO(false, List.of()));
    }

    // ------------------------------------------------------------
    // AVG / SERIES DB (unverändert)
    // ------------------------------------------------------------

    @GetMapping("/{ean}/avg-current")
    public ResponseEntity<BigDecimal> getAvgCurrentDefault(
            @PathVariable String ean,
            @RequestParam(value = "currency", required = false) String currency
    ) {
        return service.getCurrentAveragePriceForEan(
                        ean,
                        currency != null ? Currency.findCurrency(currency) : Currency.US_DOLLAR,
                        3
                )
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{ean}/avg-current/{monthsSince}")
    public ResponseEntity<BigDecimal> getAvgCurrent(
            @PathVariable String ean,
            @PathVariable @Min(0) int monthsSince,
            @RequestParam(value = "currency", required = false) String currency
    ) {
        return service.getCurrentAveragePriceForEan(
                        ean,
                        currency != null ? Currency.findCurrency(currency) : Currency.US_DOLLAR,
                        monthsSince
                )
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{ean}/series")
    public List<RemoteSoldItemRepository.PricePoint> getSeriesFromDB(@PathVariable String ean) {
        return service.getAllPricesForEan(ean);
    }

    @GetMapping("/{ean}/series/recent")
    public List<RemoteSoldItemRepository.PricePoint> getSeriesRecentDefault(@PathVariable String ean) {
        return service.getRecentPricesForEan(ean, 3);
    }

    @GetMapping("/{ean}/series/recent/{monthsSince}")
    public List<RemoteSoldItemRepository.PricePoint> getSeriesRecent(
            @PathVariable String ean,
            @PathVariable int monthsSince
    ) {
        return service.getRecentPricesForEan(ean, monthsSince);
    }

    // ------------------------------------------------------------
    // DTOs
    // ------------------------------------------------------------

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

    public record BulkResult(
            int total,
            int created,
            List<UUID> ids
    ) {}
}
