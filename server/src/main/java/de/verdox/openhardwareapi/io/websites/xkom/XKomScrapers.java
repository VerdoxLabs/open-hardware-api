package de.verdox.openhardwareapi.io.websites.xkom;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.io.api.WebsiteScraper;
import de.verdox.openhardwareapi.model.HardwareTypes;
import de.verdox.openhardwareapi.model.PCCase;
import de.verdox.openhardwareapi.model.values.DimensionsMm;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;
import static de.verdox.openhardwareapi.io.websites.pc_kombo.PCKomboScrapers.parseTimings;

public class XKomScrapers {
    public static WebsiteScraper create(HardwareSpecService service) {
        return new WebsiteScraper(service, "x-kom.de")
                .withStrategy(new XKomScrapingStrategy())
                .withShouldSavePredicate((s, document) -> {
                    return document.selectFirst("div.page-wrapper") != null;
                })
                .withBaseLogic((scrapedSpecs, hardwareSpec) -> {
                    var specs = scrapedSpecs.specs();
                    if (specs.containsKey("Hersteller")) {
                        specs.put("manufacturer", specs.get("Hersteller"));
                    }
                    if (specs.containsKey("HAN")) {
                        specs.put("MPN", specs.get("HAN"));
                    }
                })
                .withCPUScrape(cpu -> cpu.addMainScrapeLogic((scraped, target) -> {
                            // TODO: CPU-Felder/Labels für x-kom ergänzen

                            var specs = scraped.specs();
                            target.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "CPU -Socket (Socket)", specs, (s, e) -> e.name().contains(s)));
                            target.setTdpWatts((int) parseFirstInt("Leistungsaufnahme (TDP)", specs));
                            target.setIntegratedGraphics(extractFirstString("Grafikmodell", specs));
                            target.setCores((int) parseFirstInt("Anzahl der physischen Kerne", specs));
                            target.setThreads((int) parseFirstInt("Anzahl der Threads", specs));
                            target.setBaseClockMhz(parseFirstInt("Kern-Taktfrequenz", specs) * 1000);
                            target.setL3CacheMb((int) parseFirstInt("Cache-Speicher", specs));

                        },
                        "https://x-kom.de/pc-komponenten-hardware/prozessoren"))

                .withMotherboardScrape(mb -> mb.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();


                            target.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "Prozessorsockel", specs, (s, e) -> s.contains(e.name())));
                            target.setChipset(extractFirstEnum(HardwareTypes.Chipset.class, "Chipsatz", specs, (s, e) -> e.name().equals(s.trim())));
                            target.setRamType(extractFirstEnum(HardwareTypes.RamType.class, "Unterstützter Speichertyp", specs, (s, e) -> s.contains(e.name())));
                            target.setRamSlots((int) parseFirstInt("Anzahl der Speicherbänke", specs));
                            target.setRamCapacity((int) parseFirstInt("Max. Arbeitsspeichergröße", specs));
                            target.setFormFactor(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Format", specs, (s, e) -> s.contains(e.getName())));

                        },
                        "https://x-kom.de/pc-komponenten-hardware/mainboards"))

                .withPSUScraper(psu -> psu.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();

                            target.setWattage((int) parseFirstInt("Maximale Leistung", specs));
                            target.setPsuPowerVersion((float) parseFirstDouble("Standard", specs));
                            target.setEfficiencyRating(extractFirstEnum(HardwareTypes.PsuEfficiencyRating.class, "Zertifikat", specs,
                                    (s, r) -> s.toUpperCase().contains(r.name())));
                            target.setSize(extractFirstEnum(HardwareTypes.PSUFormFactor.class, "Standard", specs, (s, f) -> s.toUpperCase().contains(f.name())));


                            String modularityType = extractFirstString("Kabeltyp", specs);

                            if (modularityType.equals("Nicht modular")) {
                                target.setModularity(HardwareTypes.PSU_MODULARITY.NON_MODULAR);
                            } else if (modularityType.equals("Modular")) {
                                target.setModularity(HardwareTypes.PSU_MODULARITY.FULL_MODULAR);
                            } else {
                                target.setModularity(HardwareTypes.PSU_MODULARITY.SEMI_MODULAR);
                            }
                        },
                        "https://x-kom.de/pc-komponenten-hardware/computer-netzteile/computer-netzteile"))

                .withPCCaseScraper(cs -> cs.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setMotherboardSupport(
                                    extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "Mainboard-Standard", specs,
                                            (s, ff) -> s.contains(ff.name())));

                            DimensionsMm d = new DimensionsMm();
                            d.setHeight(parseFirstDouble("Höhe [mm]", specs));
                            d.setWidth(parseFirstDouble("Breite [mm]", specs));
                            d.setDepth(parseFirstDouble("Tiefe [mm]", specs));
                            target.setDimensions(d);

                            target.setSizeClass(PCCase.classify(d));
                        },
                        "https://x-kom.de/pc-komponenten-hardware/computergehause/computergehause"))

                .withGPUScrape(gpu -> gpu.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();

                            target.setChip(find(service, target, extractFirstString("Grafikchip", specs)));
                            target.setVramType(extractFirstEnum(HardwareTypes.VRAM_TYPE.class, "Speichertyp", specs, (s, v) -> s.contains(v.name())));

                            String pcie = extractFirstString("Anschlusstyp", specs);

                            if (pcie.contains("PCIe 5.0")) {
                                target.setPcieVersion(HardwareTypes.PcieVersion.GEN5);
                            } else if (pcie.contains("PCIe 4.0")) {
                                target.setPcieVersion(HardwareTypes.PcieVersion.GEN4);
                            } else if (pcie.contains("PCIe 3.0")) {
                                target.setPcieVersion(HardwareTypes.PcieVersion.GEN3);
                            }

                            target.setTdp(parseFirstInt("Leistungsaufnahme", specs));

                            target.setGpuCanonicalName(
                                    extractFirstString("Grafikchip", specs)
                                            .replace("NVIDIA", "")
                                            .replace("GeForce", "")
                                            .replace("AMD", "")
                                            .replace("Radeon", "")
                                            .replace("Intel ", "").trim()
                            );

                            target.setVramGb(parseFirstInt("Speicher", specs));


                        },
                        "https://x-kom.de/pc-komponenten-hardware/grafikkarten"))

                .withCPUCoolerScrape("CPU-Cooler", cool -> cool.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();

                            if (extractFirstString("Kühlsystem", specs).equals("Aktiv")) {
                                target.setType(HardwareTypes.CoolerType.AIR);
                            } else if (extractFirstString("Kühlsystem", specs).equals("Wasser")) {
                                target.setType(HardwareTypes.CoolerType.AIO_LIQUID);
                            }

                            if (target.getModel().contains("360")) {
                                target.setRadiatorLengthMm(360);
                            } else if (target.getModel().contains("280")) {
                                target.setRadiatorLengthMm(280);
                            } else if (target.getModel().contains("240")) {
                                target.setRadiatorLengthMm(240);
                            } else if (target.getModel().contains("120")) {
                                target.setRadiatorLengthMm(120);
                            }

                            target.setTdpWatts((int) parseFirstInt("TDP (Thermal Design Power)", specs));
                            target.setSupportedSockets(extractEnumSet(HardwareTypes.CpuSocket.class, "Kompatibilität", specs,
                                    (s, sock) -> s.contains(sock.name())));


                        },
                        "https://x-kom.de/pc-komponenten-hardware/computerkuhlungen/prozessorkuhlungen"))

                .withRAMScraper("RAM", ram -> ram.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();

                            target.setType(extractFirstEnum(HardwareTypes.RamType.class, "Speichertyp", specs, (s, v) -> s.contains(v.name())));
                            target.setSizeGb(Math.toIntExact(parseFirstInt("Speichermodul-Kapazität", specs)));
                            target.setSticks(Math.toIntExact(parseFirstInt("Anzahl der Module", specs)));
                            target.setSpeedMtps(Math.toIntExact(parseFirstInt("Taktfrequenz", specs)));

                            int[] timings = parseTimings(extractFirstString("Timing", specs));
                            target.setCasLatency(timings[0]);
                            target.setRowAddressToColumnAddressDelay(timings[1]);
                            target.setRowPrechargeTime(timings[2]);
                            target.setRowActiveTime(timings[3]);

                            target.setECC(extractFirstString("ECC-Speicher", specs).equals("Ja"));
                        },
                        "https://x-kom.de/pc-komponenten-hardware/pc-arbeitsspeicher"))

                .withStorageScraper("Storage", st -> st
                        .addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setCapacityGb((int) parseFirstInt("Kapazität", scraped.specs()));
                        })
                        .addVariant("SSD", (scraped, s) -> {
                                    s.setStorageType(HardwareTypes.StorageType.SSD);

                                    if (extractFirstString("Schnittstelle", scraped.specs()).contains("SATA III")) {
                                        s.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                                    } else {
                                        s.setStorageInterface(HardwareTypes.StorageInterface.NVME);
                                    }

                                },
                                "https://x-kom.de/pc-komponenten-hardware/festplatten-netzwerkfestplatten/ssd-festplatten")
                        .addVariant("HDD", (scraped, h) -> {
                            h.setStorageType(HardwareTypes.StorageType.HDD);
                            h.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                        }, "https://x-kom.de/pc-komponenten-hardware/festplatten-netzwerkfestplatten/hdd-festplatten")
                );
    }

}
