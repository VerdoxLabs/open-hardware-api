package de.verdox.openhardwareapi.controller;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import de.verdox.openhardwareapi.BadRequestException;
import de.verdox.openhardwareapi.component.repository.HardwareSpecificRepo;
import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/specs")
public class APISpecUploadController {
    private final ObjectMapper om;
/*    private final javax.validation.Validator validator;*/
    private final HardwareSpecService hardwareSpecService;

    public APISpecUploadController(ObjectMapper om, /*javax.validation.Validator validator, */HardwareSpecService hardwareSpecService) {
        this.om = om;
        /*this.validator = validator;*/
        this.hardwareSpecService = hardwareSpecService;
    }

    // ---------- Single ----------
    @PostMapping(path = "/{type}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public <HARDWARE extends HardwareSpec> ResponseEntity<?> uploadOne(@PathVariable String type, @RequestBody String json) {
        if (!hardwareSpecService.isValidType(type)) {
            throw new IllegalArgumentException("Invalid type:" + type);
        }
        Class<HARDWARE> hardwareType = (Class<HARDWARE>) hardwareSpecService.getType(type);
        HardwareSpecificRepo<HARDWARE> repo = hardwareSpecService.getRepo(hardwareType);

        HARDWARE entity = readStrict(json, hardwareType);
        validateOrThrow(entity);

        var saved = repo.save(entity);
        URI location = URI.create("/api/v1/specs/%s/%d".formatted(type, saved.getId()));
        return ResponseEntity.created(location).body(saved);
    }

    // ---------- Bulk (JSON-Array oder NDJSON) ----------
    @PostMapping(path = "/{type}/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public <HARDWARE extends HardwareSpec> ResponseEntity<?> uploadBulk(@PathVariable String type, @RequestBody String body,
                                                                        @RequestParam(defaultValue = "true") boolean allOrNothing) {
        if (!hardwareSpecService.isValidType(type)) {
            throw new IllegalArgumentException("Invalid type:" + type);
        }
        Class<HARDWARE> hardwareType = (Class<HARDWARE>) hardwareSpecService.getType(type);
        HardwareSpecificRepo<HARDWARE> repo = hardwareSpecService.getRepo(hardwareType);

        List<HARDWARE> items = parseJsonArrayOrNdjson(body, hardwareType);

        // Validierung & Save
        List<HARDWARE> toSave = new ArrayList<>(items.size());
        List<BulkError> errors = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            try {
                validateOrThrow(items.get(i));
                toSave.add(items.get(i));
            } catch (BadRequestException ex) {
                if (allOrNothing) throw ex; // brich komplett ab
                errors.add(new BulkError(i, ex.getMessage()));
            }
        }

        List<HARDWARE> saved = repo.saveAll(toSave);

        if (!errors.isEmpty()) {
            // 207 Multi-Status ähnlich — hier 200 mit gemischtem Ergebnis
            Map<String, Object> result = Map.of(
                    "savedCount", saved.size(),
                    "failedCount", errors.size(),
                    "errors", errors
            );
            return ResponseEntity.status(HttpStatus.OK).body(result);
        }

        return ResponseEntity.ok(Map.of("savedCount", saved.size()));
    }

    // ---------- Optional: Datei-Upload (Array oder NDJSON im File) ----------
    @PostMapping(path = "/{type}/bulkFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> uploadBulkFile(@PathVariable String type,
                                            @RequestPart("file") MultipartFile file,
                                            @RequestParam(defaultValue = "true") boolean allOrNothing) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        return uploadBulk(type, content, allOrNothing);
    }

    // ---- helpers ----
    private <T> T readStrict(String json, Class<T> cls) {
        try {
            return om.readValue(json, cls);
        } catch (MismatchedInputException e) {
            throw bad("Invalid payload for %s: %s".formatted(cls.getSimpleName(), e.getOriginalMessage()));
        } catch (IOException e) {
            throw bad("Cannot parse JSON: " + e.getMessage());
        }
    }

    private <T> List<T> parseJsonArrayOrNdjson(String body, Class<T> cls) {
        // 1) Versuche JSON-Array
        try {
            JavaType listType = om.getTypeFactory().constructCollectionType(List.class, cls);
            return om.readValue(body, listType);
        } catch (IOException ignore) { /* fallthrough */ }

        // 2) NDJSON (eine Entity pro Zeile)
        List<T> result = new ArrayList<>();
        int lineNo = 0;
        for (String line : body.split("\\R")) {
            lineNo++;
            String s = line.trim();
            if (s.isEmpty()) continue;
            try {
                result.add(om.readValue(s, cls));
            } catch (IOException e) {
                throw bad("NDJSON parse error at line %d: %s".formatted(lineNo, e.getMessage()));
            }
        }
        if (result.isEmpty()) {
            throw bad("Empty bulk payload (expected JSON array or NDJSON lines).");
        }
        return result;
    }

    private void validateOrThrow(Object entity) {
/*        Set<ConstraintViolation<Object>> violations = validator.validate(entity);
        if (!violations.isEmpty()) {
            String msg = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw bad("Validation failed: " + msg);
        }*/
    }

    private static BadRequestException bad(String msg) {
        return new BadRequestException(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg));
    }

    // Lightweight Fehlerobjekt für Bulk
    public record BulkError(int index, String error) {
    }
}
