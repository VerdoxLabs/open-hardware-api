package de.verdox.openhardwareapi.io.websites.xkom;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.io.api.WebsiteScraper;
import de.verdox.openhardwareapi.model.*;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.extractEnumSet;
import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.extractFirstString;

public class XKomScrapers {
    public static WebsiteScraper create(HardwareSpecService service) {
        return new WebsiteScraper(service, "x-kom.de")
                .withStrategy(new XKomScrapingStrategy())
                .withBaseLogic((scrapedSpecs, hardwareSpec) -> {
                    var specs = scrapedSpecs.specs();
                    if (specs.containsKey("Hersteller")) {
                        specs.put("manufacturer", specs.get("Hersteller"));
                    }
                })

                .withCPUScrape(cpu -> cpu.addMainScrapeLogic((scraped, target) -> {
                            // TODO: CPU-Felder/Labels für x-kom ergänzen
                        },
                        "https://x-kom.de/pc-komponenten-hardware/prozessoren"))

                .withMotherboardScrape(mb -> mb.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            // TODO: x-kom Labels mappen (z. B. „Sockel“, „Chipsatz“, „Formfaktor“ …)
                        },
                        "https://x-kom.de/pc-komponenten-hardware/mainboards"))

                .withPSUScraper(psu -> psu.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            // TODO: Watt/80PLUS/Modularität/Format mappen
                        },
                        "https://x-kom.de/pc-komponenten-hardware/computer-netzteile/computer-netzteile"))

                .withPCCaseScraper(cs -> cs.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setMotherboardSupport(
                                    extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "kompatible Mainboardformate", specs,
                                            (s, ff) -> s.contains(ff.name())));
                        },
                        "https://x-kom.de/pc-komponenten-hardware/computergehause/computergehause"))

                .withGPUScrape(gpu -> gpu.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            // TODO: Chip/PCIe/VRAM Felder von x-kom mappen
                        },
                        "https://x-kom.de/pc-komponenten-hardware/grafikkarten"))

                .withCPUCoolerScrape("CPU-Cooler", cool -> cool.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            // TODO: Luft vs. AIO + Sockets mappen
                        },
                        "https://x-kom.de/pc-komponenten-hardware/computerkuhlungen/prozessorkuhlungen"))

                .withRAMScraper("RAM", ram -> ram.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            // TODO: Typ/Größe/Takt/Timings mappen
                        },
                        "https://x-kom.de/pc-komponenten-hardware/pc-arbeitsspeicher"))

                .withStorageScraper("Storage", st -> st
                        .addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setStorageType(HardwareTypes.StorageType.SSD.equals(target.getStorageType())
                                    ? HardwareTypes.StorageType.SSD
                                    : target.getStorageType());
                            String format = extractFirstString("Format", specs);
                            if (format != null && format.contains("M.2")) {
                                target.setStorageInterface(HardwareTypes.StorageInterface.NVME);
                            } else {
                                target.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                            }
                        })
                        .addVariant("SSD", (scraped, s) -> s.setStorageType(HardwareTypes.StorageType.SSD),
                                "https://x-kom.de/pc-komponenten-hardware/festplatten-netzwerkfestplatten/ssd-festplatten")
                        .addVariant("HDD", (scraped, h) -> {
                            h.setStorageType(HardwareTypes.StorageType.HDD);
                            h.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                        }, "https://x-kom.de/pc-komponenten-hardware/festplatten-netzwerkfestplatten/hdd-festplatten")
                );
    }

}
