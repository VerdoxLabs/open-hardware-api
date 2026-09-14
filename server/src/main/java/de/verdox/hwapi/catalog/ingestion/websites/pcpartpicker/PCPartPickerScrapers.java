package de.verdox.hwapi.catalog.ingestion.websites.pcpartpicker;

import de.verdox.hwapi.catalog.application.HardwareSpecService;
import de.verdox.hwapi.catalog.ingestion.api.WebsiteScraper;

import static de.verdox.hwapi.catalog.ingestion.api.ComponentWebScraper.extractFirstString;

public class PCPartPickerScrapers {
    public static WebsiteScraper create(HardwareSpecService service) {
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
