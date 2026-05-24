package de.verdox.hwapi.hardwareapi.component.controller;

import de.verdox.hwapi.hardwareapi.component.service.HardwareSpecService;
import de.verdox.hwapi.model.HardwareSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/specs")
public class APIHardwareController {
    private final HardwareSpecService hardwareSpecService;

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
