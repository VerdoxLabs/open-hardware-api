package de.verdox.hwapi.client.admin;

import java.time.Instant;

public final class HardwareAdminDtos {
    private HardwareAdminDtos() {}

    public record BackendStatus(
            boolean currentlyDeleting,
            boolean scrapingRunning,
            Double progress01,        // optional
            String message,           // optional
            Instant startedAt,        // optional
            Instant lastFinishedAt    // optional
    ) {}

    public record BackendStats(
            long totalHardware,
            long hardwareWithPriceTracking,
            long totalPricePoints,
            long totalListingsTracked,
            long typesCount,
            Instant lastScrapeAt
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
