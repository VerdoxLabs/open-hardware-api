package de.verdox.hwapi.controllerapi;

import de.verdox.hwapi.client.admin.HardwareAdminDtos;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class HardwareAPIAdminController {

    private final HardwareAdminService adminService;
    private final AwinAdminService awinAdminService;

    public HardwareAPIAdminController(HardwareAdminService adminService, AwinAdminService awinAdminService) {
        this.adminService = adminService;
        this.awinAdminService = awinAdminService;
    }

    /**
     * UI Polling: Status ob Scraping läuft + Progress/Message.
     */
    @GetMapping("/status")
    public HardwareAdminDtos.BackendStatus status() {
        return adminService.getStatus();
    }

    /**
     * Statistiken fürs Admin-Dashboard.
     */
    @GetMapping("/stats")
    public HardwareAdminDtos.BackendStats stats() {
        return adminService.getStats();
    }

    /**
     * Scraping neustarten.
     * - 202 Accepted wenn gestartet
     * - 409 Conflict wenn bereits ein Scrape läuft
     */
    @PostMapping("/scraping/restart")
    public ResponseEntity<HardwareAdminDtos.ActionResult> restartScraping() {
        HardwareAdminDtos.ActionResult result = adminService.restartScraping();
        if (!result.accepted()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    /**
     * Danger Zone: Alle Hardware-Daten löschen.
     * (Specs + Preise + Benchmarks etc. je nach Service-Implementierung)
     */
    @DeleteMapping("/hardware")
    public ResponseEntity<HardwareAdminDtos.ActionResult> deleteAllHardware() {
        HardwareAdminDtos.ActionResult result = adminService.deleteAllHardwareData();
        if (!result.accepted()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/awin/status")
    public HardwareAdminDtos.AwinFetchStatus awinStatus() {
        return awinAdminService.getStatus();
    }
}