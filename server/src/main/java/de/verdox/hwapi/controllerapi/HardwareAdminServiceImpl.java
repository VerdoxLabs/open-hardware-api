package de.verdox.hwapi.controllerapi;

import de.verdox.hwapi.client.admin.HardwareAdminDtos;
import de.verdox.hwapi.hardwareapi.component.service.HardwareSpecService;
import de.verdox.hwapi.hardwareapi.scraping.ScrapingService;
import de.verdox.hwapi.priceapi.component.service.ItemPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class HardwareAdminServiceImpl implements HardwareAdminService {
    private static final Logger LOGGER = Logger.getLogger(HardwareAdminServiceImpl.class.getName());

    private final HardwareSpecService hardwareSpecService;
    private final ItemPriceService itemPriceService;
    private final ScrapingService scrapingService;
    private final AtomicBoolean currentlyDeleting = new AtomicBoolean(false);

    @Override
    public HardwareAdminDtos.BackendStatus getStatus() {
        return new HardwareAdminDtos.BackendStatus(
                currentlyDeleting.get(),
                scrapingService.isRunning(),
                scrapingService.getProgress01().orElse(null),
                scrapingService.getStatusMessage().orElse(null),
                scrapingService.getStartedAt().orElse(null),
                scrapingService.getLastFinishedAt().orElse(null)
        );
    }

    @Override
    public HardwareAdminDtos.BackendStats getStats() {
        long totalHardware = hardwareSpecService.countAllHardware();
        long typesCount = hardwareSpecService.getAllValidTypes().size();

        long hardwareWithPriceTracking = itemPriceService.countDistinctSpecsWithAnyPricePoints();

        long totalPricePoints = itemPriceService.countAllPricePoints();
        long totalListingsTracked = itemPriceService.countTrackedListings();

        Instant lastScrapeAt = scrapingService.getLastFinishedAt().orElse(null);

        return new HardwareAdminDtos.BackendStats(
                totalHardware,
                hardwareWithPriceTracking,
                totalPricePoints,
                totalListingsTracked,
                typesCount,
                lastScrapeAt
        );
    }

    @Override
    public HardwareAdminDtos.ActionResult restartScraping() {
        if(currentlyDeleting.get()) {
            return new HardwareAdminDtos.ActionResult(false, "Deleting all hardware data currently. Cannot start scraping now.");
        }
        if (scrapingService.isRunning()) {
            return new HardwareAdminDtos.ActionResult(false, "Scraping is already running. Please try again later");
        }

        scrapingService.startScraping(); // async
        return new HardwareAdminDtos.ActionResult(true, "Scraping started.");
    }

    @Override
    @Transactional
    public HardwareAdminDtos.ActionResult deleteAllHardwareData() {
        if(currentlyDeleting.get()) {
            return new HardwareAdminDtos.ActionResult(false, "Deleting all hardware data currently. Cannot restart deleting.");
        }
        if (scrapingService.isRunning()) {
            return new HardwareAdminDtos.ActionResult(false, "Cannot delete. Currently scraping.");
        }
        long start = System.currentTimeMillis();
        LOGGER.info("Deleting all hardware data. This may take a while...");
        currentlyDeleting.set(true);
        hardwareSpecService.deleteAllHardwareData();
        currentlyDeleting.set(false);
        LOGGER.info(String.format("Deleted all hardware data in %d ms", System.currentTimeMillis() - start));
        return new HardwareAdminDtos.ActionResult(true, "Deleted all hardware data from db.");
    }
}
