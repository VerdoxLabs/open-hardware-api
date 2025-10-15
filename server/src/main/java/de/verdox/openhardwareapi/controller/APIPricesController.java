package de.verdox.openhardwareapi.controller;

import de.verdox.openhardwareapi.component.repository.RemoteSoldItemRepository;
import de.verdox.openhardwareapi.component.service.RemoteSoldItemService;
import de.verdox.openhardwareapi.model.values.Currency;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/prices")
public class APIPricesController {
    private final RemoteSoldItemService service;

    public APIPricesController(RemoteSoldItemService service) {
        this.service = service;
    }

    // --- AVG CURRENT ---

    // Default 3 Monate, optional ?currency=EUR
    @GetMapping("/{ean}/avg-current")
    public ResponseEntity<BigDecimal> getAvgCurrentDefault(
            @PathVariable String ean,
            @org.springframework.web.bind.annotation.RequestParam(value = "currency", required = false) String currency) {
        return service.getCurrentAveragePriceForEan(ean, Currency.findCurrency(currency), 3)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // Mit monthsSince, optional ?currency=EUR
    @GetMapping("/{ean}/avg-current/{monthsSince}")
    public ResponseEntity<BigDecimal> getAvgCurrent(
            @PathVariable String ean,
            @PathVariable @Min(0) int monthsSince,
            @org.springframework.web.bind.annotation.RequestParam(value = "currency", required = false) String currency) {
        return service.getCurrentAveragePriceForEan(ean, Currency.findCurrency(currency), monthsSince)
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
}
