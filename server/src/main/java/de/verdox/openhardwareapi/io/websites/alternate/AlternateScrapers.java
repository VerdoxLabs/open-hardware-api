package de.verdox.openhardwareapi.io.websites.alternate;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.io.api.WebsiteScraper;
import de.verdox.openhardwareapi.io.parser.ScrapeParser;
import de.verdox.openhardwareapi.model.*;

public class AlternateScrapers {

    public static WebsiteScraper createForAlternate(HardwareSpecService service) {
        return new WebsiteScraper()
                .withStrategy(new AlternateScrapingStrategy())

                // --- CPU ---
                .withScrape(
                        "CPU",
                        CPU.class,
                        (EAN, UPC, MPN) -> (CPU) service.findLightByEanMPNUPCSN(CPU.class, EAN, UPC, MPN).orElse(new CPU()),
                        cpuScrape -> cpuScrape.addMainScrapeLogic((scraped, cpu) -> {
                                    var specs = scraped.specs();
                                    new ScrapeParser<CPU>(specs)
                                            .parseString("CPU-Hersteller", HardwareSpec::getManufacturer, HardwareSpec::setManufacturer)
                                            .parseEnum("Sockel", CPU::getSocket, CPU::setSocket,
                                                    (s, sock) -> s.toUpperCase().contains(sock.name()) || sock.name().contains(s.toUpperCase()),
                                                    HardwareTypes.CpuSocket.UNKNOWN)
                                            .parseEnum("CPU-Sockel", CPU::getSocket, CPU::setSocket,
                                                    (s, sock) -> s.toUpperCase().contains(sock.name()) || sock.name().contains(s.toUpperCase()),
                                                    HardwareTypes.CpuSocket.UNKNOWN)
                                            .parseNumber("CPU-Kerne", Integer::parseInt, CPU::getCores, CPU::setCores, 0)
                                            .parseNumber("Threads", Integer::parseInt, CPU::getThreads, CPU::setThreads, 0)
                                            .parseNumber("TDP", Integer::parseInt, CPU::getTdpWatts, CPU::setTdpWatts, 0)
                                            .parseNumber("Basistakt", s -> Double.parseDouble(s) * 1000, CPU::getBaseClockMhz, CPU::setBaseClockMhz, 0d)
                                            .parseNumber("Boost-Takt", s -> Double.parseDouble(s) * 1000, CPU::getBoostClockMhz, CPU::setBoostClockMhz, 0d)
                                            .parseNumber("L3-Cache", Integer::parseInt, CPU::getL3CacheMb, CPU::setL3CacheMb, 0)
                                            .parseString("Integrierte Grafik", CPU::getIntegratedGraphics, CPU::setIntegratedGraphics)
                                            .parse(cpu);
                                },
                                "https://www.alternate.de/CPUs"
                        )
                )

                // --- Motherboard ---
                .withScrape(
                        "Motherboard",
                        Motherboard.class,
                        (EAN, UPC, MPN) -> (Motherboard) service.findLightByEanMPNUPCSN(Motherboard.class, EAN, UPC, MPN).orElse(new Motherboard()),
                        mbScrape -> mbScrape.addMainScrapeLogic((scraped, mb) -> {
                                    var specs = scraped.specs();
                                    mb.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "CPU-Sockel", specs, (s, e) -> s.contains(e.name())));
                                    mb.setChipset(extractFirstEnum(HardwareTypes.Chipset.class, "Chipsatz", specs, (s, e) -> e.name().equals(s.trim().replace("AMD ", "").replace("Intel ", ""))));
                                    mb.setFormFactor(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Formfaktor", specs, (s, e) -> s.contains(e.getName())));
                                    mb.setRamType(extractFirstEnum(HardwareTypes.RamType.class, "Speichertyp", specs, (s, e) -> e.name().contains(s)));
                                    mb.setRamSlots((int) parseFirstInt("Speichersteckplätze", specs));
                                    mb.setRamCapacity((int) parseFirstInt("Max. Arbeitsspeicher", specs));
                                    mb.setSataSlots((int) parseFirstInt("SATA 6G (intern)", specs));
                                },
                                "https://www.alternate.de/Mainboards/Intel-Mainboards",
                                "https://www.alternate.de/Mainboards/AMD-Mainboards",
                                "https://www.alternate.de/Mainboards/ATX-Mainboards",
                                "https://www.alternate.de/Mainboards/Micro-ATX-Mainboards",
                                "https://www.alternate.de/Mainboards/Mini-ITX-Mainboards"
                        )
                )

                // --- PSU ---
                .withScrape(
                        "PSU",
                        PSU.class,
                        (EAN, UPC, MPN) -> (PSU) service.findLightByEanMPNUPCSN(PSU.class, EAN, UPC, MPN).orElse(new PSU()),
                        psuScrape -> psuScrape.addMainScrapeLogic((scraped, psu) -> {
                                    var specs = scraped.specs();
                                    psu.setWattage((int) parseFirstInt("Leistung", specs));
                                    psu.setEfficiencyRating(extractFirstEnum(HardwareTypes.PsuEfficiencyRating.class, "80 PLUS", specs, (s, r) -> s.toUpperCase().contains(r.name())));
                                    String mod = extractFirstString("Kabelmanagement", specs);
                                    if ("Modular".equalsIgnoreCase(mod) || "vollmodular".equalsIgnoreCase(mod)) {
                                        psu.setModularity(HardwareTypes.PSU_MODULARITY.FULL_MODULAR);
                                    } else if ("Nicht modular".equalsIgnoreCase(mod) || "Non-Modular".equalsIgnoreCase(mod)) {
                                        psu.setModularity(HardwareTypes.PSU_MODULARITY.NON_MODULAR);
                                    } else if (mod != null) {
                                        psu.setModularity(HardwareTypes.PSU_MODULARITY.SEMI_MODULAR);
                                    }
                                    psu.setSize(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Netzteilformat", specs, (s, ff) -> s.toUpperCase().contains(ff.name())));
                                },
                                "https://www.alternate.de/Netzteile/unter-500-Watt",
                                "https://www.alternate.de/Netzteile/ab-500-Watt",
                                "https://www.alternate.de/Netzteile/ab-750-Watt",
                                "https://www.alternate.de/Netzteile/ab-1000-Watt",
                                "https://www.alternate.de/Netzteile/ATX-Netzteile"
                        )
                )

                // --- PC Case ---
                .withScrape(
                        "PCCase",
                        PCCase.class,
                        (EAN, UPC, MPN) -> (PCCase) service.findLightByEanMPNUPCSN(PCCase.class, EAN, UPC, MPN).orElse(new PCCase()),
                        caseScrape -> caseScrape.addMainScrapeLogic((scraped, pcCase) -> {
                                    var specs = scraped.specs();
                                    pcCase.setMotherboardSupport(
                                            extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "kompatible Mainboardformate", specs, (s, ff) -> s.contains(ff.name()))
                                    );
                                    DimensionsMm d = new DimensionsMm();
                                    d.setHeight(parseFirstDouble("Höhe", specs));
                                    d.setWidth(parseFirstDouble("Breite", specs));
                                    d.setDepth(parseFirstDouble("Tiefe", specs));
                                    pcCase.setDimensions(d);
                                    pcCase.setMaxCpuCoolerHeightMm(parseFirstInt("max. CPU-Kühlerhöhe", specs));
                                    pcCase.setMaxGpuLengthMm(parseFirstInt("max. Grafikkartenlänge", specs));
                                    pcCase.setSizeClass(PCCase.classify(d));
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

                // --- GPU ---
                .withScrape(
                        "GPU",
                        GPU.class,
                        (EAN, UPC, MPN) -> (GPU) service.findLightByEanMPNUPCSN(GPU.class, EAN, UPC, MPN).orElse(new GPU()),
                        gpuScrape -> gpuScrape.addMainScrapeLogic((scraped, gpu) -> {
                                    var specs = scraped.specs();
                                    gpu.setChip(find(service, gpu, extractFirstString("GPU-Modell", specs)));
                                    String pcie = extractFirstString("Schnittstelle", specs);
                                    if (pcie != null && pcie.contains("5.0")) gpu.setPcieVersion(HardwareTypes.PcieVersion.GEN5);
                                    else if (pcie != null && pcie.contains("4.0")) gpu.setPcieVersion(HardwareTypes.PcieVersion.GEN4);
                                    else gpu.setPcieVersion(HardwareTypes.PcieVersion.GEN3);

                                    gpu.setVramGb(parseFirstInt("Speichergröße (GB)", specs));
                                    gpu.setVramType(extractFirstEnum(HardwareTypes.VRAM_TYPE.class, "Speichertyp", specs, (s, t) -> s.contains(t.name())));
                                    gpu.setLengthMm((int) parseFirstDouble("Länge", specs));
                                },
                                "https://www.alternate.de/Grafikkarten/NVIDIA-Grafikkarten",
                                "https://www.alternate.de/Grafikkarten/AMD-Grafikkarten",
                                "https://www.alternate.de/Grafikkarten/Intel-Grafikkarten"
                        )
                )

                // --- CPU Air Cooler ---
                .withScrape(
                        "CPU-Air-Cooler",
                        CPUCooler.class,
                        (EAN, UPC, MPN) -> (CPUCooler) service.findLightByEanMPNUPCSN(CPUCooler.class, EAN, UPC, MPN).orElse(new CPUCooler()),
                        airScrape -> airScrape.addMainScrapeLogic((scraped, cooler) -> {
                                    var specs = scraped.specs();
                                    cooler.setType(HardwareTypes.CoolerType.AIR);
                                    cooler.setSupportedSockets(
                                            extractEnumSet(HardwareTypes.CpuSocket.class, "Sockel-Kompatibilität", specs, (s, so) -> s.contains(so.name()))
                                    );
                                },
                                "https://www.alternate.de/CPU-K%C3%BChler"
                        )
                )

                // --- CPU AIO Liquid Cooler ---
                .withScrape(
                        "CPU-Liquid-Cooler",
                        CPUCooler.class,
                        (EAN, UPC, MPN) -> (CPUCooler) service.findLightByEanMPNUPCSN(CPUCooler.class, EAN, UPC, MPN).orElse(new CPUCooler()),
                        aioScrape -> aioScrape.addMainScrapeLogic((scraped, cooler) -> {
                                    var specs = scraped.specs();
                                    cooler.setType(HardwareTypes.CoolerType.AIO_LIQUID);
                                    cooler.setSupportedSockets(
                                            extractEnumSet(HardwareTypes.CpuSocket.class, "Sockel-Kompatibilität", specs, (s, so) -> s.contains(so.name()))
                                    );
                                    cooler.setRadiatorLengthMm(parseFirstInt("Radiatorgröße", specs));
                                },
                                "https://www.alternate.de/Wasserk%C3%BChlungen/AiO-Wasserk%C3%BChlung"
                        )
                )

                // --- RAM ---
                .withScrape(
                        "RAM",
                        RAM.class,
                        (EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()),
                        ramScrape -> ramScrape.addMainScrapeLogic((scraped, ram) -> {
                                    var specs = scraped.specs();
                                    ram.setType(extractFirstEnum(HardwareTypes.RamType.class, "Speichertyp", specs, (s, e) -> s.contains(e.name())));
                                    ram.setSticks((int) parseFirstInt("Anzahl Module", specs));
                                    if (ram.getSticks() == 0) ram.setSticks((int) parseFirstInt("RAM Riegel:", specs));
                                    ram.setSizeGb((int) parseFirstInt("Kapazität pro Modul", specs));
                                    if (ram.getSizeGb() == 0) {
                                        int total = (int) parseFirstInt("Speichergröße (gesamt)", specs);
                                        if (total > 0 && ram.getSticks() > 0) ram.setSizeGb(Math.max(1, total / ram.getSticks()));
                                    }
                                    ram.setSpeedMtps((int) parseFirstInt("Taktfrequenz", specs));
                                    ram.setCasLatency((int) parseFirstInt("CL (CAS-Latenz)", specs));
                                    ram.setTrcd((int) parseFirstInt("tRCD", specs));
                                    ram.setTrp((int) parseFirstInt("tRP", specs));
                                    ram.setTras((int) parseFirstInt("tRAS", specs));
                                },
                                "https://www.alternate.de/Arbeitsspeicher/DDR5-RAM",
                                "https://www.alternate.de/Arbeitsspeicher/DDR4-RAM",
                                "https://www.alternate.de/Arbeitsspeicher/DDR3-RAM",
                                "https://www.alternate.de/Arbeitsspeicher/DDR2-RAM"
                        )
                )

                // --- SATA SSD ---
                .withScrape(
                        "SATA-SSD",
                        Storage.class,
                        (EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()),
                        sataScrape -> sataScrape.addMainScrapeLogic((scraped, s) -> {
                                    var specs = scraped.specs();
                                    s.setStorageType(HardwareTypes.StorageType.SSD);
                                    s.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                                    int mul = extractFirstString("Kapazität", specs).contains("TB") ? 1000 : 1;
                                    s.setCapacityGb((int) (parseFirstInt("Kapazität", specs) * mul));
                                },
                                "https://www.alternate.de/SSD/SATA-SSD"
                        )
                )

                // --- M.2 / NVMe SSD ---
                .withScrape(
                        "M2-SSD",
                        Storage.class,
                        (EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()),
                        m2Scrape -> m2Scrape.addMainScrapeLogic((scraped, s) -> {
                                    var specs = scraped.specs();
                                    s.setStorageType(HardwareTypes.StorageType.SSD);
                                    String iface = extractFirstString("Schnittstelle", specs);
                                    if (iface != null && iface.toUpperCase().contains("PCI")) {
                                        s.setStorageInterface(HardwareTypes.StorageInterface.NVME);
                                    } else {
                                        s.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                                    }
                                    int mul = extractFirstString("Kapazität", specs).contains("TB") ? 1000 : 1;
                                    s.setCapacityGb((int) (parseFirstInt("Kapazität", specs) * mul));
                                },
                                "https://www.alternate.de/SSD/M-2-SSD"
                        )
                )

                // --- HDD ---
                .withScrape(
                        "HDD",
                        Storage.class,
                        (EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()),
                        hddScrape -> hddScrape.addMainScrapeLogic((scraped, s) -> {
                                    var specs = scraped.specs();
                                    s.setStorageType(HardwareTypes.StorageType.HDD);
                                    s.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                                    int mul = extractFirstString("Kapazität", specs).contains("TB") ? 1000 : 1;
                                    s.setCapacityGb((int) (parseFirstInt("Kapazität", specs) * mul));
                                },
                                "https://www.alternate.de/Festplatten/SATA-Festplatten"
                        )
                );
    }


    public static AbstractMultiAlternateScraper<CPU> forCPU(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<CPU>()
                .withId("CPU")
                .withQuery((EAN, UPC, MPN) -> (CPU) service.findLightByEanMPNUPCSN(CPU.class, EAN, UPC, MPN).orElse(new CPU()))
                .withURLs(
                        "https://www.alternate.de/CPUs"
                )
                .withSpecsTranslation((cpu, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<Motherboard> forMotherboard(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<Motherboard>()
                .withId("Motherboard")
                .withQuery((EAN, UPC, MPN) -> (Motherboard) service.findLightByEanMPNUPCSN(Motherboard.class, EAN, UPC, MPN).orElse(new Motherboard()))
                .withURLs(
                        "https://www.alternate.de/Mainboards/Intel-Mainboards",
                        "https://www.alternate.de/Mainboards/AMD-Mainboards",
                        "https://www.alternate.de/Mainboards/ATX-Mainboards",
                        "https://www.alternate.de/Mainboards/Micro-ATX-Mainboards",
                        "https://www.alternate.de/Mainboards/Mini-ITX-Mainboards"
                )
                .withSpecsTranslation((motherboard, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<PSU> forPSU(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<PSU>()
                .withId("PSU")
                .withQuery((EAN, UPC, MPN) -> (PSU) service.findLightByEanMPNUPCSN(PSU.class, EAN, UPC, MPN).orElse(new PSU()))
                .withURLs(
                        "https://www.alternate.de/Netzteile/unter-500-Watt",
                        "https://www.alternate.de/Netzteile/ab-500-Watt",
                        "https://www.alternate.de/Netzteile/ab-750-Watt",
                        "https://www.alternate.de/Netzteile/ab-750-Watt",
                        "https://www.alternate.de/Netzteile/ab-1000-Watt",
                        "https://www.alternate.de/Netzteile/ATX-Netzteile"
                        )
                .withSpecsTranslation((psu, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<PCCase> forCase(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<PCCase>()
                .withId("PCCase")
                .withQuery((EAN, UPC, MPN) -> (PCCase) service.findLightByEanMPNUPCSN(PCCase.class, EAN, UPC, MPN).orElse(new PCCase()))
                .withURLs(
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
                .withSpecsTranslation((pcCase, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<GPU> forGPU(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<GPU>()
                .withId("GPU")
                .withQuery((EAN, UPC, MPN) -> (GPU) service.findLightByEanMPNUPCSN(GPU.class, EAN, UPC, MPN).orElse(new GPU()))
                .withURLs(
                        "https://www.alternate.de/Grafikkarten/NVIDIA-Grafikkarten",
                        "https://www.alternate.de/Grafikkarten/AMD-Grafikkarten",
                        "https://www.alternate.de/Grafikkarten/Intel-Grafikkarten"
                )
                .withSpecsTranslation((gpu, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<CPUCooler> forCPUCooler(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<CPUCooler>()
                .withId("CPU-Air-Cooler")
                .withQuery((EAN, UPC, MPN) -> (CPUCooler) service.findLightByEanMPNUPCSN(CPUCooler.class, EAN, UPC, MPN).orElse(new CPUCooler()))
                .withURLs(
                        "https://www.alternate.de/CPU-K%C3%BChler"
                )
                .withSpecsTranslation((cpuCooler, specs) -> {
                    cpuCooler.setType(HardwareTypes.CoolerType.AIR);
                })
                .build();
    }

    public static AbstractMultiAlternateScraper<CPUCooler> forCPULiquidCooler(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<CPUCooler>()
                .withId("CPU-Liquid-Cooler")
                .withQuery((EAN, UPC, MPN) -> (CPUCooler) service.findLightByEanMPNUPCSN(CPUCooler.class, EAN, UPC, MPN).orElse(new CPUCooler()))
                .withURLs(
                        "https://www.alternate.de/Wasserk%C3%BChlungen/AiO-Wasserk%C3%BChlung"
                )
                .withSpecsTranslation((cpuCooler, specs) -> {
                    cpuCooler.setType(HardwareTypes.CoolerType.AIO_LIQUID);
                })
                .build();
    }

    public static AbstractMultiAlternateScraper<RAM> forRAM(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<RAM>()
                .withId("RAM")
                .withQuery((EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()))
                .withURLs(
                        "https://www.alternate.de/Arbeitsspeicher/DDR5-RAM",
                        "https://www.alternate.de/Arbeitsspeicher/DDR4-RAM",
                        "https://www.alternate.de/Arbeitsspeicher/DDR3-RAM",
                        "https://www.alternate.de/Arbeitsspeicher/DDR2-RAM"
                )
                .withSpecsTranslation((ram, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<Storage> forSataSSD(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<Storage>()
                .withId("SATA-SSD")
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs(
                        "https://www.alternate.de/SSD/SATA-SSD"
                )
                .withSpecsTranslation((storage, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<Storage> forM2SSD(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<Storage>()
                .withId("M2-SSD")
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs(
                        "https://www.alternate.de/SSD/M-2-SSD"
                )
                .withSpecsTranslation((storage, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<Storage> forHDD(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<Storage>()
                .withId("HDD")
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs(
                        "https://www.alternate.de/Festplatten/SATA-Festplatten"
                )
                .withSpecsTranslation((storage, specs) -> {

                })
                .build();
    }

}
