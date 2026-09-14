package de.verdox.hwapi.catalog.ingestion.websites.pcpartpicker;

import de.verdox.hwapi.catalog.application.HardwareSpecService;
import de.verdox.hwapi.catalog.domain.PCPartPickerProduct;
import de.verdox.hwapi.catalog.ingestion.api.WebsiteScraper;
import java.time.Duration;

import static de.verdox.hwapi.catalog.ingestion.api.ComponentWebScraper.extractFirstString;

public class PCPartPickerScrapers {
    public static WebsiteScraper create(HardwareSpecService service) {
        WebsiteScraper scraper = new WebsiteScraper(service, "pcpartpicker.com")
                .withStrategy(new PCPartPickerStrategy())
                .withMinLiveRequestInterval(Duration.ofSeconds(60))
                .withShouldSavePredicate(PCPartPickerCpuScraper::isUsableCatalogSnapshot);

        // Categories without a dedicated entity are retained losslessly as generic
        // PCPartPicker products. Their category and every visible spec are persisted.
        addGeneric(scraper, "headphones", "https://pcpartpicker.com/products/headphones/");
        addGeneric(scraper, "keyboards", "https://pcpartpicker.com/products/keyboard/");
        addGeneric(scraper, "mice", "https://pcpartpicker.com/products/mouse/");
        addGeneric(scraper, "speakers", "https://pcpartpicker.com/products/speakers/");
        addGeneric(scraper, "webcams", "https://pcpartpicker.com/products/webcam/");
        addGeneric(scraper, "sound-cards", "https://pcpartpicker.com/products/sound-card/");
        addGeneric(scraper, "wired-networking", "https://pcpartpicker.com/products/wired-network-card/");
        addGeneric(scraper, "wireless-networking", "https://pcpartpicker.com/products/wireless-network-card/");
        addGeneric(scraper, "fan-controllers", "https://pcpartpicker.com/products/fan-controller/");
        addGeneric(scraper, "thermal-compound", "https://pcpartpicker.com/products/thermal-paste/");
        addGeneric(scraper, "external-hard-drives", "https://pcpartpicker.com/products/external-hard-drive/");
        addGeneric(scraper, "optical-drives", "https://pcpartpicker.com/products/optical-drive/");
        addGeneric(scraper, "operating-systems", "https://pcpartpicker.com/products/os/");
        return scraper;
    }

    private static void addGeneric(WebsiteScraper scraper, String category, String url) {
        scraper.withScrape("PCPartPicker/" + category, PCPartPickerProduct::new,
                product -> product.addMainScrapeLogic((scraped, target) -> {
                    target.setCategory(category);
                    target.setSpecifications(scraped.specs());
                    PCPartPickerImageStore.storeFirstProductImage(scraped.specs(), target);
                }, url));
    }

    /*
     * Typed core categories remain intentionally separate.  Their data belongs in
     * CPU/GPU/RAM/... rather than in the generic compatibility container above.
     */
    private static WebsiteScraper legacyTypedTemplate(HardwareSpecService service) {
        return new WebsiteScraper(service, "pcpartpicker.com")
                .withStrategy(new PCPartPickerStrategy())

                .withCPUScrape(cpu -> cpu.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                        },
                        "https://pcpartpicker.com/products/cpu/"))

                .withMotherboardScrape(mb -> mb.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                        },
                        "https://pcpartpicker.com/products/motherboard/"))

                .withPSUScraper(psu -> psu.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                        },
                        "https://pcpartpicker.com/products/power-supply/"))

                .withPCCaseScraper(cs -> cs.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                        },
                        "https://pcpartpicker.com/products/case/"))

                .withGPUScrape(gpu -> gpu.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();

                            target.setGpuCanonicalName(
                                    extractFirstString("gpu-chip", specs)
                                            .replace("NVIDIA", "")
                                            .replace("GeForce", "")
                                            .replace("AMD", "")
                                            .replace("Radeon", "")
                                            .replace("Intel ", "")
                                            .trim()
                            );
                        },
                        "https://pcpartpicker.com/products/video-card/"))

                .withCPUCoolerScrape("CPU-Cooler", cool -> cool.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                        },
                        "https://pcpartpicker.com/products/cpu-cooler/"))

                .withRAMScraper("RAM", ram -> ram.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                        },
                        "https://pcpartpicker.com/products/memory/"))

                .withStorageScraper("Storage", st -> st
                        .addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();

                        }, "https://pcpartpicker.com/products/internal-hard-drive/")
                );
    }
}
