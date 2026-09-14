package de.verdox.hwapi.admin;

import de.verdox.hwapi.pricing.application.awin.AwinFeed;
import de.verdox.hwapi.pricing.application.awin.AwinFeedOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/** Read-only endpoints used by the embedded database console. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/awin")
public class AwinFeedAdminController {
    private final AwinFeedOverviewService feedOverviewService;

    @GetMapping("/feeds")
    public List<AwinFeed> feeds() throws IOException, InterruptedException {
        return feedOverviewService.loadActiveFeeds();
    }
}
