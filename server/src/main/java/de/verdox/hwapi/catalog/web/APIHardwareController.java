package de.verdox.hwapi.catalog.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import de.verdox.hwapi.catalog.application.HardwareSpecService;
import de.verdox.hwapi.catalog.domain.HardwareSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/specs")
public class APIHardwareController {
    private final HardwareSpecService hardwareSpecService;
    private final ObjectMapper objectMapper;

    @RequestMapping(path = "/{type}", method = RequestMethod.HEAD)
    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<?>> ResponseEntity<Void> head(
            @PathVariable String type,
            @RequestParam(defaultValue = "50") int size
    ) {
        if (!hardwareSpecService.isValidType(type)) {
            throw new IllegalArgumentException("Invalid type:" + type);
        }

        Class<HARDWARE> hardwareType = (Class<HARDWARE>) hardwareSpecService.getType(type);
        long totalElements = hardwareSpecService.countByType(hardwareType);

        int pageSize = Math.max(1, size);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);

        HttpHeaders h = new HttpHeaders();
        h.add("X-Total-Elements", String.valueOf(totalElements));
        h.add("X-Total-Pages", String.valueOf(totalPages));
        h.add("X-Page-Size", String.valueOf(pageSize));
        return new ResponseEntity<>(h, HttpStatus.NO_CONTENT);
    }

    @GetMapping("/{type}/count")
    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<HARDWARE>> ResponseEntity<Long> count(@PathVariable String type) {
        if (!hardwareSpecService.isValidType(type)) {
            throw new IllegalArgumentException("Invalid type:" + type);
        }
        Class<HARDWARE> hardwareType = (Class<HARDWARE>) hardwareSpecService.getType(type);
        return ResponseEntity.ok(hardwareSpecService.countByType(hardwareType));
    }


    @GetMapping("/{type}")
    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<HARDWARE>> ResponseEntity<Page<HARDWARE>> list(
            @PathVariable String type,
            @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            WebRequest request
    ) {
        if (!hardwareSpecService.isValidType(type)) {
            throw new IllegalArgumentException("Invalid type:" + type);
        }

        @SuppressWarnings("unchecked")
        Class<HARDWARE> hardwareType = (Class<HARDWARE>) hardwareSpecService.getType(type);

        Page<HARDWARE> page = hardwareSpecService.findPage(hardwareType, pageable);

        LocalDate lastLaunchDate = page.getContent().stream()
                .map(HardwareSpec::getLaunchDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(LocalDate.MIN);

        String etag = "\"" + page.getTotalElements() + ":" + lastLaunchDate + "\"";

        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                    .build();
        }

        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(page);
    }


    @GetMapping("/byEan/{ean}/{type}")
    public <HARDWARE extends HardwareSpec<HARDWARE>> ResponseEntity<HardwareSpec<?>> byEan(
            @PathVariable(required = true) String ean,
            @PathVariable(required = false) String type,
            WebRequest request
    ) {
        if (type == null) {
            return ResponseEntity.of(Optional.ofNullable(hardwareSpecService.findAnyByEAN(ean)));
        }
        if (!hardwareSpecService.isValidType(type)) {
            throw new IllegalArgumentException("Invalid type " + type);
        }
        Class<HARDWARE> hardwareType = (Class<HARDWARE>) hardwareSpecService.getType(type);
        return ResponseEntity.of(Optional.ofNullable(hardwareSpecService.findByEAN(hardwareType, ean)));
    }


    @GetMapping("/types")
    public ResponseEntity<Set<String>> getAllTypes() {
        return ResponseEntity.ok(hardwareSpecService.getAllValidTypes());
    }

    @GetMapping("/byMpn")
    @Transactional(readOnly = true)
    public ResponseEntity<HardwareSpec<?>> byMpn(@RequestParam String mpn,
                                                  @RequestParam(required = false) String type) {
        if (type != null && !hardwareSpecService.isValidType(type)) {
            throw new IllegalArgumentException("Invalid type " + type);
        }
        HardwareSpec<?> spec = type == null
                ? hardwareSpecService.findByMPN(mpn)
                : hardwareSpecService.findByMPN(hardwareSpecService.getType(type), mpn);
        if (spec == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(spec);
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> search(@RequestParam(defaultValue = "") String q,
                                        @RequestParam(required = false) String type,
                                        @RequestParam(defaultValue = "100") int limit) {
        String needle = q.trim().toLowerCase(Locale.ROOT);
        int safeLimit = Math.max(1, Math.min(limit, 250));
        return hardwareSpecService.findAll().stream()
                .filter(spec -> type == null || type.equalsIgnoreCase(spec.getClass().getSimpleName()))
                .filter(spec -> needle.isBlank()
                        || spec.displayName().toLowerCase(Locale.ROOT).contains(needle)
                        || (spec.getManufacturer() != null && spec.getManufacturer().toLowerCase(Locale.ROOT).contains(needle))
                        || spec.getMPNs().stream().anyMatch(mpn -> mpn.toLowerCase(Locale.ROOT).contains(needle))
                        || spec.getEANs().stream().anyMatch(ean -> ean.contains(needle)))
                .limit(safeLimit)
                .map(spec -> {
                    Map<String, Object> result = new LinkedHashMap<>(objectMapper.convertValue(spec, Map.class));
                    result.put("specType", spec.getClass().getSimpleName().toLowerCase(Locale.ROOT));
                    return result;
                })
                .toList();
    }

    @GetMapping("/search/page")
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> searchPage(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "") String manufacturer,
            @RequestParam(defaultValue = "") String identifier,
            @RequestParam(defaultValue = "false") boolean withImage,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam Map<String, String> parameters
    ) {
        String needle = q.trim().toLowerCase(Locale.ROOT);
        String maker = manufacturer.trim().toLowerCase(Locale.ROOT);
        String idNeedle = identifier.trim().toLowerCase(Locale.ROOT);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));

        Stream<HardwareSpec<?>> matches = hardwareSpecService.findAll().stream()
                .filter(spec -> type == null || type.isBlank() || type.equalsIgnoreCase(spec.getClass().getSimpleName()))
                .filter(spec -> maker.isBlank() || (spec.getManufacturer() != null && spec.getManufacturer().toLowerCase(Locale.ROOT).contains(maker)))
                .filter(spec -> idNeedle.isBlank() || spec.getMPNs().stream().anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(idNeedle))
                        || spec.getEANs().stream().anyMatch(value -> value.contains(identifier.trim())))
                .filter(spec -> !withImage || spec.getPictureUrls().stream().anyMatch(url -> url != null && !url.contains("noimage")))
                .filter(spec -> needle.isBlank()
                        || spec.displayName().toLowerCase(Locale.ROOT).contains(needle)
                        || spec.getMPNs().stream().anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(needle))
                        || spec.getEANs().stream().anyMatch(value -> value.contains(needle)))
                .filter(spec -> matchesSpecFilters(spec, parameters));

        Comparator<HardwareSpec<?>> comparator = Comparator.comparing(
                spec -> spec.displayName() == null ? "" : spec.displayName(), String.CASE_INSENSITIVE_ORDER);
        if ("id".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparingLong((HardwareSpec<?> spec) -> spec.getId()).reversed();
        } else if ("detectedAt".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparing((HardwareSpec<?> spec) -> spec.getDetectedAt(),
                    Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Comparator.comparingLong((HardwareSpec<?> spec) -> spec.getId()).reversed());
        }
        List<Map<String, Object>> all = matches.sorted(comparator)
                .map(spec -> {
                    Map<String, Object> result = new LinkedHashMap<>(objectMapper.convertValue(spec, Map.class));
                    result.put("specType", spec.getClass().getSimpleName().toLowerCase(Locale.ROOT));
                    return result;
                }).toList();

        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return new PageImpl<>(all.subList(from, to), org.springframework.data.domain.PageRequest.of(safePage, safeSize), all.size());
    }

    /**
     * Facets deliberately use the JSON representation of a spec.  This keeps the
     * catalog filter contract aligned with the entity model and also supports the
     * lossless {@code CatalogAccessory.specifications} map without a new endpoint
     * per accessory class.  Clients only request the fields they render.
     */
    @GetMapping("/filter-options")
    @Transactional(readOnly = true)
    public Map<String, List<String>> filterOptions(
            @RequestParam String type,
            @RequestParam List<String> fields
    ) {
        if (!hardwareSpecService.isValidType(type)) throw new IllegalArgumentException("Invalid type: " + type);
        return fields.stream().distinct().collect(java.util.stream.Collectors.toMap(
                field -> field,
                field -> hardwareSpecService.findAll().stream()
                        .filter(spec -> type.equalsIgnoreCase(spec.getClass().getSimpleName()))
                        .flatMap(spec -> valuesAt(spec, field).stream())
                        .filter(value -> !value.isBlank() && !"UNKNOWN".equalsIgnoreCase(value) && !"0".equals(value))
                        .distinct().sorted(String.CASE_INSENSITIVE_ORDER).limit(100).toList(),
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    /** `filter.<field>=value` is exact/set membership; `min.` and `max.` compare numbers. */
    private boolean matchesSpecFilters(HardwareSpec<?> spec, Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("filter.") || entry.getKey().startsWith("min.") || entry.getKey().startsWith("max."))
                .allMatch(entry -> {
                    String key = entry.getKey();
                    String field = key.substring(key.indexOf('.') + 1);
                    List<String> values = valuesAt(spec, field);
                    if (values.isEmpty()) return false;
                    if (key.startsWith("filter.")) {
                        return values.stream().anyMatch(value -> value.equalsIgnoreCase(entry.getValue()));
                    }
                    try {
                        double threshold = Double.parseDouble(entry.getValue());
                        return values.stream().mapToDouble(value -> {
                                    try { return Double.parseDouble(value); } catch (NumberFormatException ignored) { return Double.NaN; }
                                })
                                .anyMatch(value -> key.startsWith("min.") ? value >= threshold : value <= threshold);
                    } catch (NumberFormatException ignored) {
                        return false;
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private List<String> valuesAt(HardwareSpec<?> spec, String path) {
        Map<String, Object> document = objectMapper.convertValue(spec, new TypeReference<>() {});
        Object value = document;
        for (String segment : path.split("\\.")) {
            if (!(value instanceof Map<?, ?> map)) return List.of();
            value = ((Map<String, Object>) map).get(segment);
        }
        if (value == null) return List.of();
        if (value instanceof Collection<?> collection) return collection.stream().filter(Objects::nonNull).map(String::valueOf).toList();
        if (value instanceof Map<?, ?> map) return map.values().stream().filter(Objects::nonNull).map(String::valueOf).toList();
        return List.of(String.valueOf(value));
    }

    @GetMapping("/page/{type}/{filter}")
    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<HARDWARE>> ResponseEntity<Page<HARDWARE>> page(
            @PathVariable String type,
            @PathVariable String filter,
            @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            WebRequest request
    ) {
        if (!hardwareSpecService.isValidType(type)) {
            throw new IllegalArgumentException("Invalid type:" + type);
        }

        @SuppressWarnings("unchecked")
        Class<HARDWARE> hardwareType = (Class<HARDWARE>) hardwareSpecService.getType(type);

        Page<HARDWARE> page = hardwareSpecService.findPage(hardwareType, pageable);

        LocalDate lastLaunchDate = page.getContent().stream()
                .map(HardwareSpec::getLaunchDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(LocalDate.MIN);

        String etag = "\"" + page.getTotalElements() + ":" + lastLaunchDate + "\"";

        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                    .build();
        }

        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(page);
    }
}
