package de.verdox.hwapi.catalog.ingestion.websites.pcpartpicker;

import de.verdox.hwapi.catalog.application.HardwareSpecService;
import de.verdox.hwapi.catalog.domain.*;
import de.verdox.hwapi.catalog.ingestion.api.WebsiteScraper;
import de.verdox.hwapi.catalog.ingestion.api.selenium.DomainRateLimiter;
import org.jsoup.nodes.Document;

import java.time.Duration;
import java.util.function.Supplier;

/** Typed PCPartPicker scrapers for the component entities supported by the catalog. */
public final class PCPartPickerScrapers {
    private static final Duration RATE_LIMIT_COOLDOWN = Duration.ofHours(2);
    private PCPartPickerScrapers() {}

    public static WebsiteScraper create(HardwareSpecService service) {
        WebsiteScraper scraper = new WebsiteScraper(service, "pcpartpicker.com")
                .withStrategy(new PCPartPickerStrategy())
                .withMinLiveRequestInterval(Duration.ofSeconds(60))
                .withChallengePageDetection(PCPartPickerScrapers::isRateLimitPage)
                .withShouldSavePredicate(PCPartPickerCpuScraper::isUsableCatalogSnapshot);

        scraper.withGPUScrape("GPU", s -> s.addMainScrapeLogic((scraped, target) -> {
            target.setGpuCanonicalName(first(scraped.specs(), "gpu-chip"));
        }, "https://pcpartpicker.com/products/video-card/"));
        scraper.withMotherboardScrape("Motherboard", s -> s.addMainScrapeLogic((scraped, target) -> {}, "https://pcpartpicker.com/products/motherboard/"));
        scraper.withPSUScraper("PSU", s -> s.addMainScrapeLogic((scraped, target) -> {}, "https://pcpartpicker.com/products/power-supply/"));
        scraper.withPCCaseScraper("PCCase", s -> s.addMainScrapeLogic((scraped, target) -> {}, "https://pcpartpicker.com/products/case/"));
        scraper.withCPUCoolerScrape("CPUCooler", s -> s.addMainScrapeLogic((scraped, target) -> {}, "https://pcpartpicker.com/products/cpu-cooler/"));
        scraper.withRAMScraper("RAM", s -> s.addMainScrapeLogic((scraped, target) -> {}, "https://pcpartpicker.com/products/memory/"));
        scraper.withStorageScraper("Storage", s -> s.addMainScrapeLogic((scraped, target) -> {}, "https://pcpartpicker.com/products/internal-hard-drive/"));
        scraper.withScrape("Fan", Fan::new, s -> s.addMainScrapeLogic((scraped, target) -> {}, "https://pcpartpicker.com/products/case-fan/"));
        addAccessory(scraper, "Headphones", Headphones::new, "headphones", "https://pcpartpicker.com/products/headphones/");
        addAccessory(scraper, "Keyboard", Keyboard::new, "keyboards", "https://pcpartpicker.com/products/keyboard/");
        addAccessory(scraper, "Mouse", Mouse::new, "mice", "https://pcpartpicker.com/products/mouse/");
        addAccessory(scraper, "Speakers", Speakers::new, "speakers", "https://pcpartpicker.com/products/speakers/");
        addAccessory(scraper, "Webcam", Webcam::new, "webcams", "https://pcpartpicker.com/products/webcam/");
        addAccessory(scraper, "SoundCard", SoundCard::new, "sound-cards", "https://pcpartpicker.com/products/sound-card/");
        addAccessory(scraper, "WiredNetworkCard", WiredNetworkCard::new, "wired-networking", "https://pcpartpicker.com/products/wired-network-card/");
        addAccessory(scraper, "WirelessNetworkCard", WirelessNetworkCard::new, "wireless-networking", "https://pcpartpicker.com/products/wireless-network-card/");
        addAccessory(scraper, "FanController", FanController::new, "fan-controllers", "https://pcpartpicker.com/products/fan-controller/");
        addAccessory(scraper, "ThermalCompound", ThermalCompound::new, "thermal-compound", "https://pcpartpicker.com/products/thermal-paste/");
        addAccessory(scraper, "ExternalHardDrive", ExternalHardDrive::new, "external-hard-drives", "https://pcpartpicker.com/products/external-hard-drive/");
        addAccessory(scraper, "OpticalDrive", OpticalDrive::new, "optical-drives", "https://pcpartpicker.com/products/optical-drive/");
        addAccessory(scraper, "OperatingSystem", OperatingSystem::new, "operating-systems", "https://pcpartpicker.com/products/os/");
        return scraper;
    }

    /** PCPartPicker returns HTTP 200 with this HTML instead of a useful error status. */
    static boolean isRateLimitPage(String url, Document document) {
        if (url == null || !url.contains("pcpartpicker.com") || document == null) {
            return false;
        }
        String text = document.text();
        boolean blocked = text != null
                && text.toLowerCase(java.util.Locale.ROOT).contains("pcpartpicker is unavailable")
                && text.toLowerCase(java.util.Locale.ROOT).contains("refcode:");
        if (blocked) {
            DomainRateLimiter.pause("pcpartpicker.com", RATE_LIMIT_COOLDOWN);
        }
        return blocked;
    }

    private static <H extends CatalogAccessory<H>> void addAccessory(WebsiteScraper scraper, String id,
                                                                            Supplier<H> constructor, String category, String url) {
        scraper.withScrape(id, constructor, s -> s.addMainScrapeLogic((scraped, target) -> {
            target.setSpecifications(scraped.specs());
        }, url));
    }

    private static String first(java.util.Map<String, java.util.List<String>> specs, String key) {
        return specs.getOrDefault(key, java.util.List.of("unknown")).getFirst();
    }

}
