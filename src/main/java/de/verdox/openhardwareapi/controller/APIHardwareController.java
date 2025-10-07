package de.verdox.openhardwareapi.controller;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.model.HardwareSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/specs")
public class APIHardwareController {
    private final HardwareSpecService hardwareSpecService;

    @GetMapping("/{type}")
    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec> ResponseEntity<Page<HardwareSpec>> list(
            @PathVariable String type,
            @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            WebRequest request
    ) {
        if (!hardwareSpecService.isValidType(type)) {
            throw new IllegalArgumentException("Invalid type:" + type);
        }
        Class<HARDWARE> hardwareType = (Class<HARDWARE>) hardwareSpecService.getType(type);
        JpaSpecificationExecutor<HARDWARE> repo = hardwareSpecService.getRepo(hardwareType);
        if (repo == null) {
            throw new IllegalStateException("No repo found for type: " + type);
        }

        Page<HardwareSpec> page = repo.findAll(null, pageable).map(e -> e);

        String etag = "\"" + page.getTotalElements() + ":" +
                page.getContent().stream()
                        .map(HardwareSpec::getUpdatedAt)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(OffsetDateTime.now());

        if (request.checkNotModified(etag))
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .eTag(etag)
                .body(page);

    }

    @GetMapping("/byEan/{ean}/{type}")
    public ResponseEntity<HardwareSpec> byEan(
            @PathVariable(required = true) String ean,
            @PathVariable(required = false) String type,
            WebRequest request
    ) {
        if (!hardwareSpecService.isValidType(type)) {
            throw new IllegalArgumentException("Invalid type " + type);
        }

        Class<? extends HardwareSpec> hardwareType = hardwareSpecService.getType(type);
        return ResponseEntity.of(hardwareSpecService.findLightByEanMPNUPCSN(hardwareType, ean, null, null));
    }


    @GetMapping("/types")
    public ResponseEntity<Set<String>> getAllTypes() {
        return ResponseEntity.ok(hardwareSpecService.getAllValidTypes());
    }
}
