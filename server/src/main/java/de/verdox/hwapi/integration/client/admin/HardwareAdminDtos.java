package de.verdox.hwapi.integration.client.admin;

import java.time.Instant;
import java.util.List;

public final class HardwareAdminDtos {
    private HardwareAdminDtos() {}

    public record BackendStatus(
            boolean currentlyDeleting,
            boolean scrapingRunning,
            Double progress01,        // optional
            String message,           // optional
            Instant startedAt,        // optional
            Instant lastFinishedAt,   // optional
            java.util.List<ScraperStatus> scrapers
    ) {}

    public record ScraperStatus(
            String id, String baseUrl, boolean running, String phase, String currentUrl,
            int processedPages, int recognizedPages, int unreachablePages, int estimatedPages, int paginationPagesFound, String message, String lastError,
              Instant startedAt, Instant finishedAt, java.util.List<FailedScrape> failedLinks,
              boolean paginationKnown, long estimatedDurationSeconds, Instant pausedUntil
    ) {}

    public record FailedScrape(String url, String reason, Instant failedAt) {}

    public record BackendStats(
            long totalHardware,
            long hardwareWithPriceTracking,
            long totalPricePoints,
            long totalListingsTracked,
            long typesCount,
            Instant lastScrapeAt
    ) {}

    public record CacheOverview(
            Instant scannedAt,
            long totalFiles,
            long totalCatalogPages,
            long totalRecognizedProducts,
            long totalDetailPages,
            List<CacheSource> sources
    ) {}

    public record CacheSource(
            String website,
            String category,
            long cacheFiles,
            long paginationPages,
            long recognizedProducts,
            long detailPages,
            Instant latestCachedAt
    ) {}

    public record ActionResult(
            boolean accepted,
            String message
    ) {}

    public record AwinFetchStatus(
            boolean running,
            Instant nextRunAt,
            Instant startedAt,
            Instant lastFinishedAt,
            String message,
            String lastError,
            String currentFeedAdvertiser,
            String currentFeedRegion,
            long currentFeedIndex,
            long totalFeeds,
            long currentFeedProcessed,
            long currentFeedTotal,
            long overallProcessed,
            long overallTotal
    ) {}
}
