package de.verdox.hwapi.pricing.application;

import de.verdox.hwapi.catalog.application.HardwareSpecService;
import de.verdox.hwapi.catalog.domain.HardwareSpec;
import de.verdox.hwapi.configuration.ScrapingEnabled;
import de.verdox.hwapi.integration.client.ebay.EbayMarketplace;
import de.verdox.hwapi.pricing.application.ebay.EbayAPITrackActiveListingsService;
import de.verdox.hwapi.pricing.application.kleinanzeigen.KleinanzeigenPriceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runs the non-sold marketplace imports in one controlled cycle. */
@Service
public class ActiveMarketplaceSyncService {
    private static final Logger LOGGER = Logger.getLogger(ActiveMarketplaceSyncService.class.getSimpleName());

    private final HardwareSpecService hardwareSpecService;
    private final EbayAPITrackActiveListingsService ebayService;
    private final KleinanzeigenPriceService kleinanzeigenService;
    private final ScrapingEnabled scrapingEnabled;
    private final int maxEbaySpecs;
    private final AtomicBoolean running = new AtomicBoolean();

    public ActiveMarketplaceSyncService(
            HardwareSpecService hardwareSpecService,
            EbayAPITrackActiveListingsService ebayService,
            KleinanzeigenPriceService kleinanzeigenService,
            ScrapingEnabled scrapingEnabled,
            @Value("${hwapi.pricing.active-listings.max-ebay-specs:250}") int maxEbaySpecs) {
        this.hardwareSpecService = hardwareSpecService;
        this.ebayService = ebayService;
        this.kleinanzeigenService = kleinanzeigenService;
        this.scrapingEnabled = scrapingEnabled;
        this.maxEbaySpecs = Math.max(1, maxEbaySpecs);
    }

    @Scheduled(cron = "${hwapi.pricing.active-listings.cron:0 20 * * * *}", zone = "Europe/Berlin")
    @Async
    public void runScheduledSync() {
        if (!scrapingEnabled.isEnabled() || !running.compareAndSet(false, true)) return;
        try {
            syncActiveListings();
        } finally {
            running.set(false);
        }
    }

    /** eBay active offers are fetched through Browse API; Kleinanzeigen remains Camoufox-backed. */
    void syncActiveListings() {
        List<HardwareSpec<?>> specs = hardwareSpecService.findAll();
        int processed = 0;
        for (HardwareSpec<?> spec : specs) {
            if (processed++ >= maxEbaySpecs) break;
            if ((spec.getEANs() == null || spec.getEANs().isEmpty())
                    && (spec.getMPNs() == null || spec.getMPNs().isEmpty())) continue;
            for (EbayMarketplace marketplace : EbayMarketplace.values()) {
                ebayService.fetchActiveListingsForSpec(spec, java.util.Set.of(marketplace.getCurrency()), marketplace);
            }
        }

        kleinanzeigenService.runScrape();
        LOGGER.log(Level.INFO, "Active marketplace sync finished: eBay specs={0}, Kleinanzeigen included", processed);
    }
}
