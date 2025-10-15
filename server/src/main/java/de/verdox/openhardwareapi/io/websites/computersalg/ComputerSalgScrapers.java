package de.verdox.openhardwareapi.io.websites.computersalg;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.io.api.WebsiteScraper;
import de.verdox.openhardwareapi.io.parser.ScrapeParser;
import de.verdox.openhardwareapi.model.CPU;
import de.verdox.openhardwareapi.model.HardwareTypes;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;

public class ComputerSalgScrapers {

    public static WebsiteScraper create(HardwareSpecService service) {
        return new WebsiteScraper(service, "computersalg.de")
                .withStrategy(new ComputerSalgScrapingStrategy())

                // CPU
                .withCPUScrape(cpu -> cpu.addMainScrapeLogic((scraped, target) -> {
                            new ScrapeParser<CPU>(scraped.specs())
                                    .parseString("Prozessor Modell:", CPU::getModel, CPU::setModel)
                                    .parseNumber("Anzahl der CPU Kerne:", Integer::parseInt, CPU::getCores, CPU::setCores, 0)
                                    .parseNumber("Anzahl der Threads:", Integer::parseInt, CPU::getThreads, CPU::setThreads, 0)
                                    .parseNumber("Prozessortakt:", s -> Double.parseDouble(s) * 1000, CPU::getBaseClockMhz, CPU::setBaseClockMhz, 0d)
                                    .parseNumber("Max. Turbotakt:", s -> Double.parseDouble(s) * 1000, CPU::getBoostClockMhz, CPU::setBoostClockMhz, 0d)
                                    .parseEnum("Sockel:", CPU::getSocket, CPU::setSocket,
                                            (s, sock) -> s.toUpperCase().contains(sock.name().toUpperCase()) || sock.name().toUpperCase().contains(s.toUpperCase()),
                                            HardwareTypes.CpuSocket.UNKNOWN)
                                    .parseNumber("TDP:", Integer::parseInt, CPU::getTdpWatts, CPU::setTdpWatts, 0)
                                    .parseNumber("L3 Cache:", Integer::parseInt, CPU::getL3CacheMb, CPU::setL3CacheMb, 0)
                                    .parseString("Integrierte Grafik:", CPU::getIntegratedGraphics, CPU::setIntegratedGraphics)
                                    .parse(target);
                        },
                        "https://www.computersalg.de/l/1486/amd-cpu",
                        "https://www.computersalg.de/l/1487/intel-cpu"))

                // Motherboard
                .withMotherboardScrape(mb -> mb.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "Mainboard Sockel:", specs, (s, e) -> s.contains(e.name())));
                            target.setChipset(extractFirstEnum(HardwareTypes.Chipset.class, "Mainboard Chipsatz:", specs,
                                    (s, e) -> e.name().equals(s.trim().replace("AMD ", "").replace("Intel ", ""))));
                            target.setFormFactor(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Mainboard Formfaktor:", specs,
                                    (s, e) -> s.contains(e.getName())));
                            target.setRamType(extractFirstEnum(HardwareTypes.RamType.class, "Arbeitsspeicher Typ:", specs, (s, e) -> e.name().contains(s)));
                            target.setRamSlots(4);
                            target.setRamCapacity((int) parseFirstInt("Max. Kapazität der Einzelmodule:", specs) * target.getRamSlots());
                        },
                        "https://www.computersalg.de/l/1489/amd-mainboards",
                        "https://www.computersalg.de/l/1490/intel-mainboards"))

                // PSU
                .withPSUScraper(psu -> psu.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setWattage((int) parseFirstInt("Leistung:", specs));
                            target.setSize(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Netzteilformat", specs,
                                    (s, f) -> s.toUpperCase().contains(f.name())));
                            target.setEfficiencyRating(extractFirstEnum(HardwareTypes.PsuEfficiencyRating.class, "80 Plus Zertifikat:", specs,
                                    (s, r) -> s.toUpperCase().contains(r.name())));
                            String modularityType = extractFirstString("Kabelmanagement:", specs);
                            if (modularityType.equalsIgnoreCase("modular") && !target.getModel().contains("CM Modular")) {
                                target.setModularity(HardwareTypes.PSU_MODULARITY.FULL_MODULAR);
                            } else if (modularityType.equalsIgnoreCase("Non-Modular")) {
                                target.setModularity(HardwareTypes.PSU_MODULARITY.NON_MODULAR);
                            } else {
                                target.setModularity(HardwareTypes.PSU_MODULARITY.SEMI_MODULAR);
                            }
                        },
                        "https://www.computersalg.de/l/1958/pc-server-netzteile"))

                // PC Case
                .withPCCaseScraper(cs -> cs.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setMotherboardSupport(
                                    extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "kompatible Mainboardformate", specs,
                                            (s, ff) -> s.contains(ff.name()))
                            );
                        },
                        "https://www.computersalg.de/l/5446/alle-geh%c3%a4use"))

                // GPU
                .withGPUScrape(gpu -> gpu.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setChip(find(service, target, extractFirstString("GPU Modell:", specs)));
                            String pcie = extractFirstString("Schnittstelle:", specs);
                            if (pcie.contains("PCIe 5.0")) target.setPcieVersion(HardwareTypes.PcieVersion.GEN5);
                            else if (pcie.contains("PCIe 4.0")) target.setPcieVersion(HardwareTypes.PcieVersion.GEN4);
                            else target.setPcieVersion(HardwareTypes.PcieVersion.GEN3);
                            target.setVramGb(parseFirstInt("Grösse des Grafikspeichers:", specs));
                            target.setVramType(extractFirstEnum(HardwareTypes.VRAM_TYPE.class, "Grafikspeichertyp:", specs, (s, t) -> s.contains(t.name())));
                        },
                        "https://www.computersalg.de/l/4177/nvidia",
                        "https://www.computersalg.de/l/4178/amd"))

                // RAM (Varianten)
                .withRAMScraper("RAM", ram -> ram
                        .addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            // gemeinsame Felder: werden pro Variante gesetzt
                            target.setSticks((int) parseFirstInt("Anzahl der Module:", specs));
                            target.setSizeGb((int) parseFirstInt("Kapazität der Einzelmodule:", specs));
                            target.setSpeedMtps((int) parseFirstInt("Max. Frequenz:", specs));
                            // Timings (falls vorhanden)
                            target.setCasLatency((int) parseFirstInt("Latenz (CL):", specs));
                            target.setRowAddressToColumnAddressDelay((int) parseFirstInt("RAS to CAS Delay (tRCD):", specs));
                            target.setRowPrechargeTime((int) parseFirstInt("Ras Precharge Time (tRP):", specs));
                            target.setRowActiveTime((int) parseFirstInt("Row Active Time (tRAS):", specs));
                        })
                        .addVariant("DDR5", (scraped, t) -> t.setType(HardwareTypes.RamType.DDR5), "https://www.computersalg.de/l/7819/ddr5")
                        .addVariant("DDR4", (scraped, t) -> t.setType(HardwareTypes.RamType.DDR4), "https://www.computersalg.de/l/1502/ddr4")
                        .addVariant("DDR3", (scraped, t) -> t.setType(HardwareTypes.RamType.DDR3), "https://www.computersalg.de/l/1501/ddr3")
                        .addVariant("DDR2", (scraped, t) -> t.setType(HardwareTypes.RamType.DDR2), "https://www.computersalg.de/l/1500/ddr2")
                        .addVariant("DDR", (scraped, t) -> t.setType(HardwareTypes.RamType.DDR), "https://www.computersalg.de/l/1499/ddr")
                )

                // Storage (SSD/HDD Varianten)
                .withStorageScraper("Storage", st -> st
                        .addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            int mul = extractFirstString("Kapazität:", specs).contains("TB") ? 1000 : 1;
                            int cap = (int) parseFirstInt("Kapazität:", specs);
                            if (cap > 0) target.setCapacityGb(cap * mul);
                            // Interface-Heuristik für SSD
                            String iface = extractFirstString("Schnittstelle:", specs);
                            if (target.getStorageType() == HardwareTypes.StorageType.SSD) {
                                target.setStorageInterface(iface != null && iface.contains("PCIe")
                                        ? HardwareTypes.StorageInterface.NVME
                                        : HardwareTypes.StorageInterface.SATA);
                            }
                        })
                        .addVariant("SSD", (scraped, ssd) -> {
                            ssd.setStorageType(HardwareTypes.StorageType.SSD);
                        }, "https://www.computersalg.de/l/238/ssd")
                        .addVariant("HDD", (scraped, hdd) -> {
                            hdd.setStorageType(HardwareTypes.StorageType.HDD);
                            hdd.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                        }, "https://www.computersalg.de/l/1320/interne-festplatten")
                );
    }

}
