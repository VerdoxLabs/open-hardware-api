package de.verdox.hwapi.catalog.ingestion.opendb;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/opendb")
@RequiredArgsConstructor
public class OpenDbAdminController {
    private final OpenDbImportService service;

    @GetMapping("/status")
    public OpenDbImportService.OpenDbStatus status() {
        return service.status();
    }

    @PostMapping("/import")
    public ResponseEntity<ImportAction> importNow() {
        boolean accepted = service.startAsyncImport();
        ImportAction action = new ImportAction(accepted, accepted
                ? "OpenDB-Import wurde gestartet."
                : "OpenDB-Import ist deaktiviert oder läuft bereits.");
        return ResponseEntity.status(accepted ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT).body(action);
    }

    public record ImportAction(boolean accepted, String message) { }
}
