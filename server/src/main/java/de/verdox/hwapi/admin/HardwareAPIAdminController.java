package de.verdox.hwapi.admin;

import de.verdox.hwapi.integration.client.admin.HardwareAdminDtos;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/admin")
public class HardwareAPIAdminController {

    private final HardwareAdminService adminService;
    private final AwinAdminService awinAdminService;
    private final HardwareBackupService backupService;
    private final CacheOverviewService cacheOverviewService;
    private final HardwareLiveUpdateService liveUpdateService;

    public HardwareAPIAdminController(HardwareAdminService adminService, AwinAdminService awinAdminService, HardwareBackupService backupService, CacheOverviewService cacheOverviewService, HardwareLiveUpdateService liveUpdateService) {
        this.adminService = adminService;
        this.awinAdminService = awinAdminService;
        this.backupService = backupService;
        this.cacheOverviewService = cacheOverviewService;
        this.liveUpdateService = liveUpdateService;
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

    @GetMapping(value = "/hardware/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter hardwareEvents() {
        return liveUpdateService.subscribe();
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return liveUpdateService.subscribe();
    }

    @GetMapping("/cache/overview")
    public HardwareAdminDtos.CacheOverview cacheOverview() {
        return cacheOverviewService.scan();
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

    @GetMapping(value = "/backups/hardware", produces = "application/zip")
    public void exportHardwareBackup(HttpServletResponse response) throws IOException {
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=hardware-catalog-backup.zip");
        backupService.exportTo(response.getOutputStream());
    }

    @PostMapping(value = "/backups/hardware/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HardwareBackupService.ImportResult importHardwareBackup(@RequestParam("file") MultipartFile file) throws IOException {
        return backupService.importBackup(file);
    }

    @GetMapping("/awin/status")
    public HardwareAdminDtos.AwinFetchStatus awinStatus() {
        return awinAdminService.getStatus();
    }
}
