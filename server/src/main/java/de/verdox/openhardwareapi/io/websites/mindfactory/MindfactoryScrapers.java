package de.verdox.openhardwareapi.io.websites.mindfactory;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.io.api.WebsiteScraper;
import de.verdox.openhardwareapi.io.parser.ScrapeParser;
import de.verdox.openhardwareapi.model.CPU;
import de.verdox.openhardwareapi.model.HardwareTypes;
import org.jsoup.nodes.Document;

import java.util.function.BiPredicate;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;

public class MindfactoryScrapers {

    public static final BiPredicate<String, Document> CHALLENGE_PREDICATE = (url, document) -> document.selectFirst("div.security-content") != null;
    public static final BiPredicate<String, Document> SHOULD_SAVE = (url, document) -> {
        if (!url.contains("product_info.php")) {
            return true;
        }

        var mainContent = document.selectFirst("div#mainContent");
        if (mainContent != null) {
            return mainContent.selectFirst("div#bProductInfo") != null;
        }
        return false;
    };

    public static WebsiteScraper create(HardwareSpecService service) {
        return new WebsiteScraper(service, "mindfactory.de")
                .withStrategy(new MindfactoryScrapingStrategy())
                .withChallengePageDetection(CHALLENGE_PREDICATE)
                .withShouldSavePredicate(SHOULD_SAVE)

                .withCPUScrape(cpu -> cpu
                        .addMainScrapeLogic((scraped, target) -> {
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
                                "https://www.mindfactory.de/Hardware/Prozessoren+(CPU).html"
                        )
                )

                .withMotherboardScrape(mb -> mb
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();
                                    target.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "Mainboard Sockel:", specs, (s, e) -> s.contains(e.name())));
                                    target.setChipset(extractFirstEnum(HardwareTypes.Chipset.class, "Mainboard Chipsatz:", specs,
                                            (s, e) -> e.name().equals(s.trim().replace("AMD ", "").replace("Intel ", ""))));
                                    target.setFormFactor(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Mainboard Formfaktor:", specs, (s, e) -> s.contains(e.getName())));
                                    target.setRamType(extractFirstEnum(HardwareTypes.RamType.class, "Arbeitsspeicher Typ:", specs, (s, e) -> e.name().contains(s)));
                                    target.setRamSlots(4);
                                    target.setRamCapacity((int) parseFirstInt("Max. Kapazität der Einzelmodule:", specs) * target.getRamSlots());
                                },
                                "https://www.mindfactory.de/Hardware/Mainboards/Desktop+Mainboards.html",
                                "https://www.mindfactory.de/Hardware/Mainboards/Server+Mainboards.html"
                        )
                )

                .withPSUScraper(psu -> psu
                        .addMainScrapeLogic((scraped, target) -> {
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
                                "https://www.mindfactory.de/Hardware/Netzteile+~+USVs+(PSU)/Netzteile+ATX.html",
                                "https://www.mindfactory.de/Hardware/Netzteile+~+USVs+(PSU)/Netzteile+SFX.html"
                        )
                )

                .withPCCaseScraper(cs -> cs
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();
                                    target.setMotherboardSupport(
                                            extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "kompatible Mainboardformate", specs, (s, ff) -> s.contains(ff.name())));
                                },
                                "https://www.mindfactory.de/Hardware/Gehaeuse/Big+Tower+Gehaeuse.html",
                                "https://www.mindfactory.de/Hardware/Gehaeuse/Midi+Tower+ohne+NT.html",
                                "https://www.mindfactory.de/Hardware/Gehaeuse/Mini+Tower+Gehaeuse.html",
                                "https://www.mindfactory.de/Hardware/Gehaeuse/Wuerfel+Gehaeuse.html",
                                "https://www.mindfactory.de/Hardware/Gehaeuse/Desktop+~+HTPC+Gehaeuse.html",
                                "https://www.mindfactory.de/Hardware/Gehaeuse/ITX+Gehaeuse.html",
                                "https://www.mindfactory.de/Hardware/Gehaeuse/Server+Gehaeuse.html",
                                "https://www.mindfactory.de/Hardware/Gehaeuse/Gehaeuse+gedaemmt.html"
                        )
                )

                .withGPUScrape(gpu -> gpu
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();
                                    target.setChip(find(service, target, extractFirstString("GPU Modell:", specs)));
                                    String pcie = extractFirstString("Schnittstelle:", specs);
                                    if (pcie.contains("PCIe 5.0")) target.setPcieVersion(HardwareTypes.PcieVersion.GEN5);
                                    else if (pcie.contains("PCIe 4.0")) target.setPcieVersion(HardwareTypes.PcieVersion.GEN4);
                                    else target.setPcieVersion(HardwareTypes.PcieVersion.GEN3);
                                    target.setVramGb(parseFirstInt("Grösse des Grafikspeichers:", specs));
                                    target.setVramType(extractFirstEnum(HardwareTypes.VRAM_TYPE.class, "Grafikspeichertyp:", specs, (s, t) -> s.contains(t.name())));
                                },
                                "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/GeForce+RTX+fuer+Gaming.html",
                                "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/GeForce+GT+fuer+Multimedia.html",
                                "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/Radeon+RX+Serie.html",
                                "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/Intel+Arc.html",
                                "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/AMD+Radeon+Pro+Duo.html",
                                "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/Quadro.html"
                        )
                )

                .withCPUCoolerScrape("CPU-Air-Cooler", air -> air
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();
                                    target.setType(HardwareTypes.CoolerType.AIR);
                                    target.setSupportedSockets(extractEnumSet(HardwareTypes.CpuSocket.class, "für folgende Sockel geeignet:", specs,
                                            (s, sock) -> s.contains(sock.name())));
                                },
                                "https://www.mindfactory.de/Hardware/Kuehlung+Luft/CPU+Kuehler.html"
                        )
                )

                .withCPUCoolerScrape("CPU-Liquid-Cooler", aio -> aio
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();
                                    target.setType(HardwareTypes.CoolerType.AIO_LIQUID);
                                    target.setSupportedSockets(extractEnumSet(HardwareTypes.CpuSocket.class, "für folgende Sockel geeignet:", specs,
                                            (s, sock) -> s.contains(sock.name())));
                                    target.setRadiatorLengthMm(parseFirstInt("Anschlüsse:", specs));
                                },
                                "https://www.mindfactory.de/Hardware/Kuehlung+Wasser+(WaKue)/All-in-One+WaKue+(AIO).html"
                        )
                )

                .withRAMScraper("RAM", ram -> ram
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();
                                    target.setType(extractFirstEnum(HardwareTypes.RamType.class, "Art des Speichers:", specs, (s, e) -> s.contains(e.name())));
                                    target.setSticks((int) parseFirstInt("Anzahl der Module:", specs));
                                    target.setSizeGb((int) parseFirstInt("Kapazität der Einzelmodule:", specs));
                                    target.setSpeedMtps((int) parseFirstInt("Max. Frequenz:", specs));
                                    target.setCasLatency((int) parseFirstInt("Latenz (CL):", specs));
                                    target.setRowAddressToColumnAddressDelay((int) parseFirstInt("RAS to CAS Delay (tRCD):", specs));
                                    target.setRowPrechargeTime((int) parseFirstInt("Ras Precharge Time (tRP):", specs));
                                    target.setRowActiveTime((int) parseFirstInt("Row Active Time (tRAS):", specs));
                                },
                                "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR5+Module.html",
                                "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR4+Module.html",
                                "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR3+Module.html",
                                "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR2+Module.html",
                                "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR5+ECC+Module.html",
                                "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR4+ECC+Module.html"
                        )
                )

                .withStorageScraper("Storage", st -> st
                        .addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            int mul = extractFirstString("Kapazität:", specs).contains("TB") ? 1000 : 1;
                            int cap = (int) parseFirstInt("Kapazität:", specs);
                            if (cap > 0) target.setCapacityGb(cap * mul);
                            if (target.getStorageType() == HardwareTypes.StorageType.SSD) {
                                String iface = extractFirstString("Schnittstelle:", specs);
                                target.setStorageInterface(iface != null && iface.contains("PCIe")
                                        ? HardwareTypes.StorageInterface.NVME
                                        : HardwareTypes.StorageInterface.SATA);
                            }
                        })
                        .addVariant("SSD", (scraped, s) -> s.setStorageType(HardwareTypes.StorageType.SSD),
                                "https://www.mindfactory.de/Hardware/Solid+State+Drives+(SSD).html")
                        .addVariant("HDD", (scraped, h) -> {
                                    h.setStorageType(HardwareTypes.StorageType.HDD);
                                    h.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                                }, "https://www.mindfactory.de/Hardware/Festplatten+(HDD)/Interne+Festplatten.html"
                        )
                );
    }
}
