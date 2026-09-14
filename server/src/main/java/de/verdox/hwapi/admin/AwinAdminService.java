package de.verdox.hwapi.admin;

import de.verdox.hwapi.integration.client.admin.HardwareAdminDtos;
import de.verdox.hwapi.pricing.application.awin.AwinTrackActiveListingsService;
import org.springframework.stereotype.Service;

@Service
public class AwinAdminService {

    private final AwinTrackActiveListingsService awin;

    public AwinAdminService(AwinTrackActiveListingsService awin) {
        this.awin = awin;
    }

    public HardwareAdminDtos.AwinFetchStatus getStatus() {
        var s = awin.getFetchStatus();
        return new HardwareAdminDtos.AwinFetchStatus(
                s.running(),
                s.nextRunAt(),
                s.startedAt(),
                s.lastFinishedAt(),
                s.message(),
                s.lastError(),
                s.currentFeedAdvertiser(),
                s.currentFeedRegion(),
                s.currentFeedIndex(),
                s.totalFeeds(),
                s.currentFeedProcessed(),
                s.currentFeedTotal(),
                s.overallProcessed(),
                s.overallTotal()
        );
    }
}