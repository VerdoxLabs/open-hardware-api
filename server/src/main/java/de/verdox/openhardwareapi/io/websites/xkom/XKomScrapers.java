package de.verdox.openhardwareapi.io.websites.xkom;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.io.api.WebsiteScraper;
import de.verdox.openhardwareapi.io.parser.ScrapeParser;
import de.verdox.openhardwareapi.io.websites.mindfactory.AbstractMultiMindfactoryScraper;
import de.verdox.openhardwareapi.model.*;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;
import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.extractEnumSet;
import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.extractFirstEnum;
import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.extractFirstString;
import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.find;
import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.parseFirstInt;

public class XKomScrapers {


    public static WebsiteScraper createForXKom(HardwareSpecService service) {
        return new WebsiteScraper()
                .withStrategy(new XKomScrapingStrategy())

                // --- CPU ---
                .withScrape(
                        "CPU",
                        CPU.class,
                        (EAN, UPC, MPN) -> (CPU) service.findLightByEanMPNUPCSN(CPU.class, EAN, UPC, MPN).orElse(new CPU()),
                        cpuScrape -> cpuScrape.addMainScrapeLogic((scraped, cpu) -> {
                                    new ScrapeParser<CPU>(scraped.specs())
                                            // TODO: x-kom Labels prüfen/anpassen
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
                                            .parse(cpu);
                                },
                                "http://x-kom.de/pc-komponenten-hardware/prozessoren"
                        )
                )

                // --- Motherboard ---
                .withScrape(
                        "Motherboard",
                        Motherboard.class,
                        (EAN, UPC, MPN) -> (Motherboard) service.findLightByEanMPNUPCSN(Motherboard.class, EAN, UPC, MPN).orElse(new Motherboard()),
                        mbScrape -> mbScrape.addMainScrapeLogic((scraped, mb) -> {
                                    var specs = scraped.specs();
                                    // TODO: x-kom Labels prüfen/anpassen
                                    mb.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "Mainboard Sockel:", specs, (s, e) -> s.contains(e.name())));
                                    mb.setChipset(extractFirstEnum(HardwareTypes.Chipset.class, "Mainboard Chipsatz:", specs,
                                            (s, e) -> e.name().equals(s.trim().replace("AMD ", "").replace("Intel ", ""))));
                                    mb.setFormFactor(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Mainboard Formfaktor:", specs,
                                            (s, e) -> s.contains(e.getName())));
                                    mb.setRamType(extractFirstEnum(HardwareTypes.RamType.class, "Arbeitsspeicher Typ:", specs, (s, e) -> e.name().contains(s)));
                                    mb.setRamSlots(4);
                                    mb.setRamCapacity((int) parseFirstInt("Max. Kapazität der Einzelmodule:", specs) * mb.getRamSlots());
                                },
                                "https://x-kom.de/pc-komponenten-hardware/mainboards"
                        )
                )

                // --- PSU ---
                .withScrape(
                        "PSU",
                        PSU.class,
                        (EAN, UPC, MPN) -> (PSU) service.findLightByEanMPNUPCSN(PSU.class, EAN, UPC, MPN).orElse(new PSU()),
                        psuScrape -> psuScrape.addMainScrapeLogic((scraped, psu) -> {
                                    var specs = scraped.specs();
                                    // TODO: x-kom Labels prüfen/anpassen
                                    psu.setWattage((int) parseFirstInt("Leistung:", specs));
                                    psu.setSize(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Netzteilformat", specs,
                                            (s, f) -> s.toUpperCase().contains(f.name())));
                                    psu.setEfficiencyRating(extractFirstEnum(HardwareTypes.PsuEfficiencyRating.class, "80 Plus Zertifikat:", specs,
                                            (s, r) -> s.toUpperCase().contains(r.name())));

                                    String modularityType = extractFirstString("Kabelmanagement:", specs);
                                    if (modularityType.equalsIgnoreCase("modular") && !psu.getModel().contains("CM Modular")) {
                                        psu.setModularity(HardwareTypes.PSU_MODULARITY.FULL_MODULAR);
                                    } else if (modularityType.equalsIgnoreCase("Non-Modular")) {
                                        psu.setModularity(HardwareTypes.PSU_MODULARITY.NON_MODULAR);
                                    } else {
                                        psu.setModularity(HardwareTypes.PSU_MODULARITY.SEMI_MODULAR);
                                    }
                                },
                                "https://x-kom.de/pc-komponenten-hardware/computer-netzteile/computer-netzteile"
                        )
                )

                // --- PC Case ---
                .withScrape(
                        "PCCase",
                        PCCase.class,
                        (EAN, UPC, MPN) -> (PCCase) service.findLightByEanMPNUPCSN(PCCase.class, EAN, UPC, MPN).orElse(new PCCase()),
                        caseScrape -> caseScrape.addMainScrapeLogic((scraped, pcCase) -> {
                                    var specs = scraped.specs();
                                    // TODO: x-kom Label prüfen (z. B. „Mainboard-Kompatibilität“)
                                    pcCase.setMotherboardSupport(
                                            extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "kompatible Mainboardformate", specs,
                                                    (s, ff) -> s.contains(ff.name()))
                                    );
                                },
                                "https://x-kom.de/pc-komponenten-hardware/computergehause/computergehause"
                        )
                )

                // --- GPU ---
                .withScrape(
                        "GPU",
                        GPU.class,
                        (EAN, UPC, MPN) -> (GPU) service.findLightByEanMPNUPCSN(GPU.class, EAN, UPC, MPN).orElse(new GPU()),
                        gpuScrape -> gpuScrape.addMainScrapeLogic((scraped, gpu) -> {
                                    var specs = scraped.specs();
                                    // TODO: x-kom Label für Chip/Modell prüfen
                                    gpu.setChip(find(service, gpu, extractFirstString("GPU Modell:", specs)));

                                    String pcie = extractFirstString("Schnittstelle:", specs);
                                    if (pcie.contains("PCIe 5.0")) {
                                        gpu.setPcieVersion(HardwareTypes.PcieVersion.GEN5);
                                    } else if (pcie.contains("PCIe 4.0")) {
                                        gpu.setPcieVersion(HardwareTypes.PcieVersion.GEN4);
                                    } else {
                                        gpu.setPcieVersion(HardwareTypes.PcieVersion.GEN3);
                                    }

                                    gpu.setVramGb(parseFirstInt("Grösse des Grafikspeichers:", specs));
                                    gpu.setVramType(extractFirstEnum(HardwareTypes.VRAM_TYPE.class, "Grafikspeichertyp:", specs,
                                            (s, t) -> s.contains(t.name())));
                                },
                                "https://x-kom.de/pc-komponenten-hardware/grafikkarten"
                        )
                )

                // --- CPU Cooler (air & AIO in einem Feed) ---
                .withScrape(
                        "CPU-Cooler",
                        CPUCooler.class,
                        (EAN, UPC, MPN) -> (CPUCooler) service.findLightByEanMPNUPCSN(CPUCooler.class, EAN, UPC, MPN).orElse(new CPUCooler()),
                        coolerScrape -> coolerScrape.addMainScrapeLogic((scraped, cooler) -> {
                                    var specs = scraped.specs();
                                    // Heuristik: Radiatorgröße => AIO, sonst Luft
                                    String rad = extractFirstString("Radiatorgröße:", specs); // TODO: falls x-kom anderes Label nutzt
                                    if (rad != null && !rad.isBlank()) {
                                        cooler.setType(HardwareTypes.CoolerType.AIO_LIQUID);
                                        cooler.setRadiatorLengthMm(parseFirstInt("Radiatorgröße:", specs));
                                    } else {
                                        cooler.setType(HardwareTypes.CoolerType.AIR);
                                    }
                                    cooler.setSupportedSockets(
                                            extractEnumSet(HardwareTypes.CpuSocket.class, "für folgende Sockel geeignet:", specs,
                                                    (s, sock) -> s.contains(sock.name()))
                                    );
                                },
                                "https://x-kom.de/pc-komponenten-hardware/computerkuhlungen/prozessorkuhlungen"
                        )
                )

                // --- RAM ---
                .withScrape(
                        "RAM",
                        RAM.class,
                        (EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()),
                        ramScrape -> ramScrape.addMainScrapeLogic((scraped, ram) -> {
                                    var specs = scraped.specs();
                                    // TODO: x-kom Labels prüfen/anpassen
                                    ram.setType(extractFirstEnum(HardwareTypes.RamType.class, "Art des Speichers:", specs, (s, e) -> s.contains(e.name())));
                                    ram.setSticks((int) parseFirstInt("Anzahl der Module:", specs));
                                    ram.setSizeGb((int) parseFirstInt("Kapazität der Einzelmodule:", specs));
                                    ram.setSpeedMtps((int) parseFirstInt("Max. Frequenz:", specs));

                                    ram.setCasLatency((int) parseFirstInt("Latenz (CL):", specs));
                                    ram.setRowAddressToColumnAddressDelay((int) parseFirstInt("RAS to CAS Delay (tRCD):", specs));
                                    ram.setRowPrechargeTime((int) parseFirstInt("Ras Precharge Time (tRP):", specs));
                                    ram.setRowActiveTime((int) parseFirstInt("Row Active Time (tRAS):", specs));
                                },
                                "https://x-kom.de/pc-komponenten-hardware/pc-arbeitsspeicher"
                        )
                )

                // --- SSD ---
                .withScrape(
                        "SSD",
                        Storage.class,
                        (EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()),
                        ssdScrape -> ssdScrape.addMainScrapeLogic((scraped, ssd) -> {
                                    var specs = scraped.specs();
                                    // TODO: x-kom Labels prüfen/anpassen
                                    int mul = extractFirstString("Kapazität:", specs).contains("TB") ? 1000 : 1;
                                    ssd.setCapacityGb((int) (parseFirstInt("Kapazität:", specs) * mul));
                                    ssd.setStorageType(HardwareTypes.StorageType.SSD);
                                    if (extractFirstString("Schnittstelle:", specs).contains("PCIe")) {
                                        ssd.setStorageInterface(HardwareTypes.StorageInterface.NVME);
                                    } else {
                                        ssd.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                                    }
                                },
                                "https://x-kom.de/pc-komponenten-hardware/festplatten-netzwerkfestplatten/ssd-festplatten"
                        )
                )

                // --- HDD ---
                .withScrape(
                        "HDD",
                        Storage.class,
                        (EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()),
                        hddScrape -> hddScrape.addMainScrapeLogic((scraped, hdd) -> {
                                    var specs = scraped.specs();
                                    // TODO: x-kom Labels prüfen/anpassen
                                    hdd.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                                    hdd.setStorageType(HardwareTypes.StorageType.HDD);
                                    int mul = extractFirstString("Kapazität:", specs).contains("TB") ? 1000 : 1;
                                    hdd.setCapacityGb((int) (parseFirstInt("Kapazität:", specs) * mul));
                                },
                                "https://x-kom.de/pc-komponenten-hardware/festplatten-netzwerkfestplatten/hdd-festplatten"
                        )
                );
    }

    public static AbstractMultiXKomScraper<CPU> forCPU(HardwareSpecService service) {
        return new AbstractMultiXKomScraper.Builder<CPU>()
                .withId("CPU")
                .withQuery((EAN, UPC, MPN) -> (CPU) service.findLightByEanMPNUPCSN(CPU.class, EAN, UPC, MPN).orElse(new CPU()))
                .withURLs(
                        "http://x-kom.de/pc-komponenten-hardware/prozessoren"
                )
                .withSpecsTranslation((cpu, specs) -> {

                })
                .build();
    }

    public static AbstractMultiXKomScraper<Motherboard> forMotherboard(HardwareSpecService service) {
        return new AbstractMultiXKomScraper.Builder<Motherboard>()
                .withId("Motherboard")
                .withQuery((EAN, UPC, MPN) -> (Motherboard) service.findLightByEanMPNUPCSN(Motherboard.class, EAN, UPC, MPN).orElse(new Motherboard()))
                .withURLs(
                        "https://x-kom.de/pc-komponenten-hardware/mainboards"
                )
                .withSpecsTranslation((mb, specs) -> {

                })
                .build();
    }

    public static AbstractMultiXKomScraper<PSU> forPSU(HardwareSpecService service) {
        return new AbstractMultiXKomScraper.Builder<PSU>()
                .withId("PSU")
                .withQuery((EAN, UPC, MPN) -> (PSU) service.findLightByEanMPNUPCSN(PSU.class, EAN, UPC, MPN).orElse(new PSU()))
                .withURLs(
                        "https://x-kom.de/pc-komponenten-hardware/computer-netzteile/computer-netzteile"
                )
                .withSpecsTranslation((psu, specs) -> {

                })
                .build();
    }

    public static AbstractMultiXKomScraper<PCCase> forCase(HardwareSpecService service) {
        return new AbstractMultiXKomScraper.Builder<PCCase>()
                .withId("PCCase")
                .withQuery((EAN, UPC, MPN) -> (PCCase) service.findLightByEanMPNUPCSN(PCCase.class, EAN, UPC, MPN).orElse(new PCCase()))
                .withURLs(
                        "https://x-kom.de/pc-komponenten-hardware/computergehause/computergehause"
                )
                .withSpecsTranslation((psu, specs) -> {

                })
                .build();
    }

    public static AbstractMultiXKomScraper<GPU> forGPU(HardwareSpecService service) {
        return new AbstractMultiXKomScraper.Builder<GPU>()
                .withId("GPU")
                .withQuery((EAN, UPC, MPN) -> (GPU) service.findLightByEanMPNUPCSN(GPU.class, EAN, UPC, MPN).orElse(new GPU()))
                .withURLs(
                        "https://x-kom.de/pc-komponenten-hardware/grafikkarten"
                )
                .withSpecsTranslation((gpu, specs) -> {

                })
                .build();
    }

    public static AbstractMultiXKomScraper<CPUCooler> forCPUCooler(HardwareSpecService service) {
        return new AbstractMultiXKomScraper.Builder<CPUCooler>()
                .withId("CPU-Air-Cooler")
                .withQuery((EAN, UPC, MPN) -> (CPUCooler) service.findLightByEanMPNUPCSN(CPUCooler.class, EAN, UPC, MPN).orElse(new CPUCooler()))
                .withURLs(
                        "https://x-kom.de/pc-komponenten-hardware/computerkuhlungen/prozessorkuhlungen"
                )
                .withSpecsTranslation((cpuCooler, specs) -> {

                })
                .build();
    }

    public static AbstractMultiXKomScraper<RAM> forRAM(HardwareSpecService service) {
        return new AbstractMultiXKomScraper.Builder<RAM>()
                .withId("RAM")
                .withQuery((EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()))
                .withURLs(
                        "https://x-kom.de/pc-komponenten-hardware/pc-arbeitsspeicher"
                )
                .withSpecsTranslation((ram, specs) -> {

                })
                .build();
    }

    public static AbstractMultiXKomScraper<Storage> forSSD(HardwareSpecService service) {
        return new AbstractMultiXKomScraper.Builder<Storage>()
                .withId("SSD")
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs(
                        "https://x-kom.de/pc-komponenten-hardware/festplatten-netzwerkfestplatten/ssd-festplatten"
                )
                .withSpecsTranslation((ssd, specs) -> {

                })
                .build();
    }

    public static AbstractMultiXKomScraper<Storage> forHDD(HardwareSpecService service) {
        return new AbstractMultiXKomScraper.Builder<Storage>()
                .withId("HDD")
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs(
                        "https://x-kom.de/pc-komponenten-hardware/festplatten-netzwerkfestplatten/hdd-festplatten"
                )
                .withSpecsTranslation((hdd, specs) -> {

                })
                .build();
    }
}
