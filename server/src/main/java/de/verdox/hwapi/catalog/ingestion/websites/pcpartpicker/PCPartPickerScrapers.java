package de.verdox.hwapi.catalog.ingestion.websites.pcpartpicker;

import de.verdox.hwapi.catalog.application.HardwareSpecService;
import de.verdox.hwapi.catalog.domain.*;
import de.verdox.hwapi.catalog.ingestion.api.WebsiteScraper;

import java.time.Duration;
import java.util.function.Supplier;

/** Typed PCPartPicker scrapers for the component entities supported by the catalog. */
public final class PCPartPickerScrapers {
    private PCPartPickerScrapers() {}

    public static WebsiteScraper create(HardwareSpecService service) {
        WebsiteScraper scraper = new WebsiteScraper(service, "pcpartpicker.com")
                .withStrategy(new PCPartPickerStrategy())
                .withMinLiveRequestInterval(Duration.ofSeconds(60))
                .withShouldSavePredicate(PCPartPickerCpuScraper::isUsableCatalogSnapshot);

        scraper.withGPUScrape("PCPartPicker/GPU", s -> s.addMainScrapeLogic((scraped, target) -> {
            target.setGpuCanonicalName(first(scraped.specs(), "gpu-chip"));
            storeImage(scraped, target);
        }, "https://pcpartpicker.com/products/video-card/"));
        scraper.withMotherboardScrape("PCPartPicker/Motherboard", s -> s.addMainScrapeLogic((scraped, target) -> storeImage(scraped, target), "https://pcpartpicker.com/products/motherboard/"));
        scraper.withPSUScraper("PCPartPicker/PSU", s -> s.addMainScrapeLogic((scraped, target) -> storeImage(scraped, target), "https://pcpartpicker.com/products/power-supply/"));
        scraper.withPCCaseScraper("PCPartPicker/PCCase", s -> s.addMainScrapeLogic((scraped, target) -> storeImage(scraped, target), "https://pcpartpicker.com/products/case/"));
        scraper.withCPUCoolerScrape("PCPartPicker/CPUCooler", s -> s.addMainScrapeLogic((scraped, target) -> storeImage(scraped, target), "https://pcpartpicker.com/products/cpu-cooler/"));
        scraper.withRAMScraper("PCPartPicker/RAM", s -> s.addMainScrapeLogic((scraped, target) -> storeImage(scraped, target), "https://pcpartpicker.com/products/memory/"));
        scraper.withStorageScraper("PCPartPicker/Storage", s -> s.addMainScrapeLogic((scraped, target) -> storeImage(scraped, target), "https://pcpartpicker.com/products/internal-hard-drive/"));
        scraper.withScrape("PCPartPicker/Fan", Fan::new, s -> s.addMainScrapeLogic((scraped, target) -> storeImage(scraped, target), "https://pcpartpicker.com/products/case-fan/"));
        addAccessory(scraper, "PCPartPicker/Headphones", Headphones::new, "headphones", "https://pcpartpicker.com/products/headphones/");
        addAccessory(scraper, "PCPartPicker/Keyboard", Keyboard::new, "keyboards", "https://pcpartpicker.com/products/keyboard/");
        addAccessory(scraper, "PCPartPicker/Mouse", Mouse::new, "mice", "https://pcpartpicker.com/products/mouse/");
        addAccessory(scraper, "PCPartPicker/Speakers", Speakers::new, "speakers", "https://pcpartpicker.com/products/speakers/");
        addAccessory(scraper, "PCPartPicker/Webcam", Webcam::new, "webcams", "https://pcpartpicker.com/products/webcam/");
        addAccessory(scraper, "PCPartPicker/SoundCard", SoundCard::new, "sound-cards", "https://pcpartpicker.com/products/sound-card/");
        addAccessory(scraper, "PCPartPicker/WiredNetworkCard", WiredNetworkCard::new, "wired-networking", "https://pcpartpicker.com/products/wired-network-card/");
        addAccessory(scraper, "PCPartPicker/WirelessNetworkCard", WirelessNetworkCard::new, "wireless-networking", "https://pcpartpicker.com/products/wireless-network-card/");
        addAccessory(scraper, "PCPartPicker/FanController", FanController::new, "fan-controllers", "https://pcpartpicker.com/products/fan-controller/");
        addAccessory(scraper, "PCPartPicker/ThermalCompound", ThermalCompound::new, "thermal-compound", "https://pcpartpicker.com/products/thermal-paste/");
        addAccessory(scraper, "PCPartPicker/ExternalHardDrive", ExternalHardDrive::new, "external-hard-drives", "https://pcpartpicker.com/products/external-hard-drive/");
        addAccessory(scraper, "PCPartPicker/OpticalDrive", OpticalDrive::new, "optical-drives", "https://pcpartpicker.com/products/optical-drive/");
        addAccessory(scraper, "PCPartPicker/OperatingSystem", OperatingSystem::new, "operating-systems", "https://pcpartpicker.com/products/os/");
        return scraper;
    }

    private static <H extends CatalogAccessory<H>> void addAccessory(WebsiteScraper scraper, String id,
                                                                            Supplier<H> constructor, String category, String url) {
        scraper.withScrape(id, constructor, s -> s.addMainScrapeLogic((scraped, target) -> {
            target.setSpecifications(scraped.specs());
            storeImage(scraped, target);
        }, url));
    }

    private static String first(java.util.Map<String, java.util.List<String>> specs, String key) {
        return specs.getOrDefault(key, java.util.List.of("unknown")).getFirst();
    }

    private static void storeImage(de.verdox.hwapi.catalog.ingestion.api.ComponentWebScraper.ScrapedSpecs scraped, HardwareSpec<?> target) {
        PCPartPickerImageStore.storeFirstProductImage(scraped.specs(), target);
    }
}
