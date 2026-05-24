package de.verdox.hwapi.priceapi.component.controller;

import de.verdox.hwapi.client.PriceSeriesResponseDTO;
import de.verdox.hwapi.hardwareapi.component.service.HardwareSpecService;
import de.verdox.hwapi.model.HardwareSpec;
import de.verdox.hwapi.model.dto.PricePointUploadDto;
import de.verdox.hwapi.model.values.ItemCondition;
import de.verdox.hwapi.priceapi.component.service.ebay.EbayCompletedListingsService;
import de.verdox.hwapi.priceapi.component.service.ItemPriceService;
import de.verdox.hwapi.priceapi.model.RemoteSoldItem;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    private ResponseEntity<BulkSeriesResponseV2> handleBulkSeriesByIds(SeriesKind kind, BulkSeriesRequestV2 req) {
        List<String> mpns = Optional.ofNullable(req.mpns()).orElse(List.of());
        List<String> eans = Optional.ofNullable(req.eans()).orElse(List.of());

        // 1) Requested in deterministischer Reihenfolge (mpns dann eans)
        List<String> requested = new ArrayList<>(mpns.size() + eans.size());
        for (String s : mpns) addIfValid(requested, s);
        for (String s : eans) addIfValid(requested, s);

        if (requested.isEmpty()) return ResponseEntity.badRequest().build();
        if (requested.size() > MAX_BULK_SIZE) return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();

        int monthSince = Optional.ofNullable(req.monthSince()).orElse(12);
        Set<ItemCondition> conditions = Optional.ofNullable(req.conditions())
                .filter(c -> !c.isEmpty())
                .orElse(EnumSet.allOf(ItemCondition.class));
        boolean fetchIfNoData = Optional.ofNullable(req.fetchIfNoData()).orElse(false);

        // Normalisierte Keys für Lookup (trim)
        List<String> requestedNorm = new ArrayList<>(requested.size());
        for (String id : requested) requestedNorm.add(normalize(id));

        // 2) Specs in einem Rutsch holen (unique identifiers -> kleinerer IN-Block)
        Set<String> uniqueIdentifiers = new LinkedHashSet<>(requestedNorm);
        List<HardwareSpec<?>> specs = hardwareSpecService.findAllByEANOrMPN(new ArrayList<>(uniqueIdentifiers));

        // 3) Index identifier -> spec (best effort)
        Map<String, HardwareSpec<?>> byIdentifier = new HashMap<>(uniqueIdentifiers.size() * 2);
        for (HardwareSpec<?> s : specs) {
            if (s == null) continue;

            if (s.getMPNs() != null) {
                for (String m : s.getMPNs()) {
                    String key = normalize(m);
                    if (!key.isEmpty()) byIdentifier.putIfAbsent(key, s);
                }
            }
            if (s.getEANs() != null) {
                for (String e : s.getEANs()) {
                    String key = normalize(e);
                    if (!key.isEmpty()) byIdentifier.putIfAbsent(key, s);
                }
            }
        }

        // 4) Performance-Hebel: pro Spec nur 1x DB-Query (Cache)
        //    Key: specId (falls null -> identityHashCode fallback)
        Map<Long, PriceSeriesResponseDTO> seriesCache = new HashMap<>();

        List<SeriesEntryV2> results = new ArrayList<>(requested.size());

        for (int i = 0; i < requested.size(); i++) {
            String original = requested.get(i);
            String identifier = requestedNorm.get(i);

            HardwareSpec<?> spec = byIdentifier.get(identifier);
            if (spec == null) {
                results.add(empty(original));
                continue;
            }

            PriceSeriesResponseDTO dbResult = seriesCache.computeIfAbsent(spec.getId(), k -> {
                if (kind == SeriesKind.ACTIVE) {
                    return itemPriceService.fetchActiveSeriesDataFromDB(spec, conditions, monthSince);
                }
                return itemPriceService.fetchCompletedSeriesDataFromDB(spec, conditions, monthSince);
            });

            if (hasSeries(dbResult)) {
                results.add(new SeriesEntryV2(original, dbResult));
                continue;
            }

            if (fetchIfNoData) {
                PriceSeriesResponseDTO remoteJobDto = itemPriceService.fetchSeriesDataFromRemote(spec, false);
                results.add(new SeriesEntryV2(original, remoteJobDto != null ? remoteJobDto : emptyDto()));
            } else {
                itemPriceService.addToBackgroundJob(spec);
                results.add(empty(original));
            }
        }

        return ResponseEntity.ok(new BulkSeriesResponseV2(results));
    }


    private static void addIfValid(List<String> out, String raw) {
        if (raw == null || raw.isBlank()) return;
        // decode + trim
        String decoded = urlDecode(raw);
        if (!decoded.isBlank()) out.add(decoded.trim());
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Fallback: lieber best-effort als 400
            return s;
        }
    }

    private static String normalize(String s) {
        return (s == null) ? "" : s.trim();
    }

    private static boolean hasSeries(PriceSeriesResponseDTO dto) {
        return dto != null && dto.series() != null && !dto.series().isEmpty();
    }

    private static final PriceSeriesResponseDTO emptyResponse = new PriceSeriesResponseDTO(false, List.of());

    private static PriceSeriesResponseDTO emptyDto() {
        return emptyResponse;
    }

    private static SeriesEntryV2 empty(String identifier) {
        return new SeriesEntryV2(identifier, emptyDto());
    }


    private enum SeriesKind { ACTIVE, COMPLETED }

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
