package de.verdox.hwapi.io.websites.alternate;

import de.verdox.hwapi.hardwareapi.component.service.HardwareSpecService;
import de.verdox.hwapi.io.api.WebsiteScraper;
import de.verdox.hwapi.model.HardwareTypes;

public class AlternateScrapers {

    public static WebsiteScraper create(HardwareSpecService service) {
        return new WebsiteScraper(service, "alternate.de")
                .withStrategy(new AlternateScrapingStrategy())
                .withChallengePageDetection((s, document) -> {
                    return document.selectFirst("p#Truv1") != null;
                })
                .withShouldSavePredicate((s, document) -> {
                    return document.selectFirst("body#mainContent") != null;
                })

                .withCPUScrape(scrape -> scrape
                        .addMainScrapeLogic((scrapedSpecs, target) -> {
                            //TODO: Logik
                        }, "https://www.alternate.de/CPUs")
                )

                .withMotherboardScrape(scrape -> scrape
                        .addMainScrapeLogic((scrapedSpecs, target) -> {
                                    //TODO: Logik
                                }, "https://www.alternate.de/Mainboards/Intel-Mainboards",
                                "https://www.alternate.de/Mainboards/AMD-Mainboards",
                                "https://www.alternate.de/Mainboards/ATX-Mainboards",
                                "https://www.alternate.de/Mainboards/Micro-ATX-Mainboards",
                                "https://www.alternate.de/Mainboards/Mini-ITX-Mainboards")
                )

                .withPSUScraper(scrape -> scrape
                        .addMainScrapeLogic((scrapedSpecs, target) -> {
                                    //TODO: Logik
                                },
                                "https://www.alternate.de/Netzteile/unter-500-Watt",
                                "https://www.alternate.de/Netzteile/ab-500-Watt",
                                "https://www.alternate.de/Netzteile/ab-750-Watt",
                                "https://www.alternate.de/Netzteile/ab-1000-Watt",
                                "https://www.alternate.de/Netzteile/ATX-Netzteile"
                        )
                )

                .withPCCaseScraper(scrape -> scrape
                        .addMainScrapeLogic((scrapedSpecs, target) -> {
                                    //TODO: Logik
                                },
                                "https://www.alternate.de/PC-Geh%C3%A4use/Midi-Tower",
                                "https://www.alternate.de/PC-Geh%C3%A4use/Big-Tower",
                                "https://www.alternate.de/PC-Geh%C3%A4use/Cube-Geh%C3%A4use",
                                "https://www.alternate.de/PC-Geh%C3%A4use/RGB-Geh%C3%A4use",
                                "https://www.alternate.de/PC-Geh%C3%A4use/ATX-Geh%C3%A4use",
                                "https://www.alternate.de/PC-Geh%C3%A4use/Micro-ATX-Geh%C3%A4use",
                                "https://www.alternate.de/PC-Geh%C3%A4use/Mini-ITX-Geh%C3%A4use",
                                "https://www.alternate.de/PC-Geh%C3%A4use/Reverse-Connector-Geh%C3%A4use",
                                "https://www.alternate.de/PC-Geh%C3%A4use/Weisse-PC-Geh%C3%A4use"
                        )
                )

                .withGPUScrape(scrape -> scrape
                        .addMainScrapeLogic((scrapedSpecs, target) -> {
                                    //TODO: Logik
                                },
                                "https://www.alternate.de/Grafikkarten/NVIDIA-Grafikkarten",
                                "https://www.alternate.de/Grafikkarten/AMD-Grafikkarten",
                                "https://www.alternate.de/Grafikkarten/Intel-Grafikkarten"
                        )
                )

                .withCPUCoolerScrape("CPU-Air-Cooler", scrape -> scrape
                        .addMainScrapeLogic((scrapedSpecs, target) -> {
                                    //TODO: Logik
                                },
                                "https://www.alternate.de/CPU-K%C3%BChler"
                        )
                )

                .withCPUCoolerScrape("CPU-Liquid-Cooler", scrape -> scrape
                        .addMainScrapeLogic((scrapedSpecs, target) -> {
                                },
                                "https://www.alternate.de/Wasserk%C3%BChlungen/AiO-Wasserk%C3%BChlung"
                        )
                )

                .withRAMScraper("RAM", scrape -> scrape
                        .addMainScrapeLogic((scrapedSpecs, target) -> {
                            //TODO: Logik
                        })
                        .addVariant("DDR2", (scrapedSpecs, target) -> {
                            target.setType(HardwareTypes.RamType.DDR2);
                        }, "https://www.alternate.de/Arbeitsspeicher/DDR2-RAM")
                        .addVariant("DDR3", (scrapedSpecs, target) -> {
                            target.setType(HardwareTypes.RamType.DDR3);
                        }, "https://www.alternate.de/Arbeitsspeicher/DDR3-RAM")
                        .addVariant("DDR4", (scrapedSpecs, target) -> {
                            target.setType(HardwareTypes.RamType.DDR4);
                        }, "https://www.alternate.de/Arbeitsspeicher/DDR4-RAM")
                        .addVariant("DDR5", (scrapedSpecs, target) -> {
                            target.setType(HardwareTypes.RamType.DDR5);
                        }, "https://www.alternate.de/Arbeitsspeicher/DDR5-RAM")
                )

                .withStorageScraper("Storage", scrape -> scrape
                        .addMainScrapeLogic((scrapedSpecs, target) -> {
                            //TODO: Logik
                        })
                        .addVariant("SSD", (scrapedSpecs, target) -> {
                            target.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                            target.setStorageType(HardwareTypes.StorageType.SSD);
                        }, "https://www.alternate.de/SSD/SATA-SSD")
                        .addVariant("M_2", (scrapedSpecs, target) -> {
                            target.setStorageInterface(HardwareTypes.StorageInterface.NVME);
                            target.setStorageType(HardwareTypes.StorageType.SSD);
                        }, "https://www.alternate.de/SSD/M-2-SSD")
                        .addVariant("HDD", (scrapedSpecs, target) -> {
                            target.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                            target.setStorageType(HardwareTypes.StorageType.HDD);
                        }, "https://www.alternate.de/Festplatten/SATA-Festplatten")
                );
    }

}
