package de.verdox.openhardwareapi.io.websites.mindfactory;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.io.api.WebsiteScraper;
import de.verdox.openhardwareapi.io.parser.ScrapeParser;
import de.verdox.openhardwareapi.model.*;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;

public class MindfactoryScrapers {

    public static WebsiteScraper create(HardwareSpecService service) {
        return new WebsiteScraper()
                .withStrategy(new MindfactoryScrapingStrategy())
                .withScrape(
                        "CPU",
                        CPU.class,
                        (EAN, UPC, MPN) -> (CPU) service.findLightByEanMPNUPCSN(CPU.class, EAN, UPC, MPN).orElse(new CPU()),
                        cpuSpecificScrape -> cpuSpecificScrape.addMainScrapeLogic(
                                (scraped, cpu) -> {
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
                                            .parse(cpu);
                                },
                                "https://www.mindfactory.de/Hardware/Prozessoren+(CPU).html"
                        )
                )

                .withScrape(
                        "Motherboard",
                        Motherboard.class,
                        (EAN, UPC, MPN) -> (Motherboard) service.findLightByEanMPNUPCSN(Motherboard.class, EAN, UPC, MPN).orElse(new Motherboard()),
                        mbScrape -> mbScrape.addMainScrapeLogic((scraped, mb) -> {
                                    var specs = scraped.specs();
                                    mb.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "Mainboard Sockel:", specs, (s, e) -> s.contains(e.name())));
                                    mb.setChipset(extractFirstEnum(HardwareTypes.Chipset.class, "Mainboard Chipsatz:", specs, (s, e) -> e.name().equals(s.trim().replace("AMD ", "").replace("Intel ", ""))));
                                    mb.setFormFactor(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Mainboard Formfaktor:", specs, (s, e) -> s.contains(e.getName())));
                                    mb.setRamType(extractFirstEnum(HardwareTypes.RamType.class, "Arbeitsspeicher Typ:", specs, (s, e) -> e.name().contains(s)));
                                    mb.setRamSlots(4);
                                    mb.setRamCapacity((int) parseFirstInt("Max. Kapazität der Einzelmodule:", specs) * mb.getRamSlots());
                                },
                                "https://www.mindfactory.de/Hardware/Mainboards/Desktop+Mainboards.html",
                                "https://www.mindfactory.de/Hardware/Mainboards/Server+Mainboards.html"
                        )
                )

                .withScrape(
                        "PSU",
                        PSU.class,
                        (EAN, UPC, MPN) -> (PSU) service.findLightByEanMPNUPCSN(PSU.class, EAN, UPC, MPN).orElse(new PSU()),
                        psuScrape -> psuScrape.addMainScrapeLogic((scraped, psu) -> {
                                    var specs = scraped.specs();
                                    psu.setWattage((int) parseFirstInt("Leistung:", specs));
                                    // Größe/Formfaktor (ATX/SFX) – nutzt deinen vorhandenen Helper
                                    psu.setSize(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Netzteilformat", specs,
                                            (s, f) -> s.toUpperCase().contains(f.name())));
                                    // 80 PLUS
                                    psu.setEfficiencyRating(extractFirstEnum(HardwareTypes.PsuEfficiencyRating.class, "80 Plus Zertifikat:", specs,
                                            (s, r) -> s.toUpperCase().contains(r.name())));
                                    // Modularity
                                    String modularityType = extractFirstString("Kabelmanagement:", specs);
                                    if (modularityType.equalsIgnoreCase("modular") && !psu.getModel().contains("CM Modular")) {
                                        psu.setModularity(HardwareTypes.PSU_MODULARITY.FULL_MODULAR);
                                    } else if (modularityType.equalsIgnoreCase("Non-Modular")) {
                                        psu.setModularity(HardwareTypes.PSU_MODULARITY.NON_MODULAR);
                                    } else {
                                        psu.setModularity(HardwareTypes.PSU_MODULARITY.SEMI_MODULAR);
                                    }
                                },
                                "https://www.mindfactory.de/Hardware/Netzteile+~+USVs+(PSU)/Netzteile+ATX.html",
                                "https://www.mindfactory.de/Hardware/Netzteile+~+USVs+(PSU)/Netzteile+SFX.html"
                        )
                )

                // --- PC Case ---
                .withScrape(
                        "PCCase",
                        PCCase.class,
                        (EAN, UPC, MPN) -> (PCCase) service.findLightByEanMPNUPCSN(PCCase.class, EAN, UPC, MPN).orElse(new PCCase()),
                        caseScrape ->
                                caseScrape.addMainScrapeLogic((scraped, pcCase) -> {
                                            var specs = scraped.specs();
                                            pcCase.setMotherboardSupport(extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "kompatible Mainboardformate", specs, (s, ff) -> s.contains(ff.name())));
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

                // --- GPU ---
                .withScrape(
                        "GPU",
                        GPU.class,
                        (EAN, UPC, MPN) -> (GPU) service.findLightByEanMPNUPCSN(GPU.class, EAN, UPC, MPN).orElse(new GPU()),
                        gpuScrape -> gpuScrape.addMainScrapeLogic((scraped, gpu) -> {
                                    var specs = scraped.specs();
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
                                "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/GeForce+RTX+fuer+Gaming.html",
                                "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/GeForce+GT+fuer+Multimedia.html",
                                "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/Radeon+RX+Serie.html",
                                "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/Intel+Arc.html",
                                "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/AMD+Radeon+Pro+Duo.html",
                                "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/Quadro.html"
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
                                            extractEnumSet(HardwareTypes.CpuSocket.class, "für folgende Sockel geeignet:", specs,
                                                    (s, sock) -> s.contains(sock.name()))
                                    );
                                },
                                "https://www.mindfactory.de/Hardware/Kuehlung+Luft/CPU+Kuehler.html"
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
                                            extractEnumSet(HardwareTypes.CpuSocket.class, "für folgende Sockel geeignet:", specs,
                                                    (s, sock) -> s.contains(sock.name()))
                                    );
                                    cooler.setRadiatorLengthMm(parseFirstInt("Anschlüsse:", specs));
                                },
                                "https://www.mindfactory.de/Hardware/Kuehlung+Wasser+(WaKue)/All-in-One+WaKue+(AIO).html"
                        )
                )

                // --- RAM ---
                .withScrape(
                        "RAM",
                        RAM.class,
                        (EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()),
                        ramScrape -> ramScrape.addMainScrapeLogic((scraped, ram) -> {
                                    var specs = scraped.specs();
                                    ram.setType(extractFirstEnum(HardwareTypes.RamType.class, "Art des Speichers:", specs, (s, e) -> s.contains(e.name())));
                                    ram.setSticks((int) parseFirstInt("Anzahl der Module:", specs));
                                    ram.setSizeGb((int) parseFirstInt("Kapazität der Einzelmodule:", specs));
                                    ram.setSpeedMtps((int) parseFirstInt("Max. Frequenz:", specs));

                                    // Timing-Werte; deine Ziel-Entity scheint mehrere Felder zu haben – hier placeholder gemäß Vorlage
                                    ram.setCasLatency((int) parseFirstInt("Latenz (CL):", specs));
                                    ram.setRowAddressToColumnAddressDelay((int) parseFirstInt("RAS to CAS Delay (tRCD):", specs)); // vorausgesetzt es existiert ein Feld
                                    ram.setRowPrechargeTime((int) parseFirstInt("Ras Precharge Time (tRP):", specs));
                                    ram.setRowActiveTime((int) parseFirstInt("Row Active Time (tRAS):", specs));
                                },
                                "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR5+Module.html",
                                "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR4+Module.html",
                                "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR3+Module.html",
                                "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR2+Module.html",
                                "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR5+ECC+Module.html",
                                "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR4+ECC+Module.html"
                        )
                )

                // --- SSD ---
                .withScrape(
                        "SSD",
                        Storage.class,
                        (EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()),
                        ssdScrape -> ssdScrape.addMainScrapeLogic((scraped, ssd) -> {
                                    var specs = scraped.specs();
                                    int mul = extractFirstString("Kapazität:", specs).contains("TB") ? 1000 : 1;
                                    ssd.setCapacityGb((int) (parseFirstInt("Kapazität:", specs) * mul));
                                    ssd.setStorageType(HardwareTypes.StorageType.SSD);
                                    if (extractFirstString("Schnittstelle:", specs).contains("PCIe")) {
                                        ssd.setStorageInterface(HardwareTypes.StorageInterface.NVME);
                                    } else {
                                        ssd.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                                    }
                                },
                                "https://www.mindfactory.de/Hardware/Solid+State+Drives+(SSD).html"
                        )
                )

                // --- HDD ---
                .withScrape(
                        "HDD",
                        Storage.class,
                        (EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()),
                        hddScrape -> hddScrape.addMainScrapeLogic((scraped, hdd) -> {
                                    var specs = scraped.specs();
                                    hdd.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                                    hdd.setStorageType(HardwareTypes.StorageType.HDD);
                                    int mul = extractFirstString("Kapazität:", specs).contains("TB") ? 1000 : 1;
                                    hdd.setCapacityGb((int) (parseFirstInt("Kapazität:", specs) * mul));
                                },
                                "https://www.mindfactory.de/Hardware/Festplatten+(HDD)/Interne+Festplatten.html"
                        )
                );
    }

    public static AbstractMultiMindfactoryScraper<PSU> forPSU(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<PSU>()
                .withId("PSU")
                .withQuery((EAN, UPC, MPN) -> (PSU) service.findLightByEanMPNUPCSN(PSU.class, EAN, UPC, MPN).orElse(new PSU()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Netzteile+~+USVs+(PSU)/Netzteile+ATX.html",
                        "https://www.mindfactory.de/Hardware/Netzteile+~+USVs+(PSU)/Netzteile+SFX.html"
                )
                .withSpecsTranslation((psu, specs) -> {
                    psu.setWattage((int) parseFirstInt("Leistung:", specs));
                    psu.setSize(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Netzteilformat", specs, (s, psuEfficiencyRating) -> s.toUpperCase().contains(psuEfficiencyRating.name())));


                    psu.setEfficiencyRating(extractFirstEnum(HardwareTypes.PsuEfficiencyRating.class, "80 Plus Zertifikat:", specs, (s, psuEfficiencyRating) -> s.toUpperCase().contains(psuEfficiencyRating.name())));

                    String modularityType = extractFirstString("Kabelmanagement:", specs);
                    if (modularityType.equalsIgnoreCase("modular") && !psu.getModel().contains("CM Modular")) {
                        psu.setModularity(HardwareTypes.PSU_MODULARITY.FULL_MODULAR);
                    } else if (modularityType.equalsIgnoreCase("Non-Modular")) {
                        psu.setModularity(HardwareTypes.PSU_MODULARITY.NON_MODULAR);
                    } else {
                        psu.setModularity(HardwareTypes.PSU_MODULARITY.SEMI_MODULAR);
                    }

                })
                .build();
    }

    public static AbstractMultiMindfactoryScraper<PCCase> forCase(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<PCCase>()
                .withId("PCCase")
                .withQuery((EAN, UPC, MPN) -> (PCCase) service.findLightByEanMPNUPCSN(PCCase.class, EAN, UPC, MPN).orElse(new PCCase()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Gehaeuse/Big+Tower+Gehaeuse.html",
                        "https://www.mindfactory.de/Hardware/Gehaeuse/Midi+Tower+ohne+NT.html",
                        "https://www.mindfactory.de/Hardware/Gehaeuse/Mini+Tower+Gehaeuse.html",
                        "https://www.mindfactory.de/Hardware/Gehaeuse/Wuerfel+Gehaeuse.html",
                        "https://www.mindfactory.de/Hardware/Gehaeuse/Desktop+~+HTPC+Gehaeuse.html",
                        "https://www.mindfactory.de/Hardware/Gehaeuse/ITX+Gehaeuse.html",
                        "https://www.mindfactory.de/Hardware/Gehaeuse/Server+Gehaeuse.html",
                        "https://www.mindfactory.de/Hardware/Gehaeuse/Gehaeuse+gedaemmt.html"
                )
                .withSpecsTranslation((pcCase, specs) -> pcCase.setMotherboardSupport(extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "kompatible Mainboardformate", specs, (s, motherboardFormFactor) -> s.contains(motherboardFormFactor.name()))))
                .build();
    }

    public static AbstractMultiMindfactoryScraper<GPU> forGPU(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<GPU>()
                .withId("GPU")
                .withQuery((EAN, UPC, MPN) -> (GPU) service.findLightByEanMPNUPCSN(GPU.class, EAN, UPC, MPN).orElse(new GPU()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/GeForce+RTX+fuer+Gaming.html",
                        "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/GeForce+GT+fuer+Multimedia.html",
                        "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/Radeon+RX+Serie.html",
                        "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/Intel+Arc.html",
                        "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/AMD+Radeon+Pro+Duo.html",
                        "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/Quadro.html"
                )
                .withSpecsTranslation((gpu, specs) -> {

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
                    gpu.setVramType(extractFirstEnum(HardwareTypes.VRAM_TYPE.class, "Grafikspeichertyp:", specs, (s, vramType) -> s.contains(vramType.name())));
                })
                .build();
    }

    public static AbstractMultiMindfactoryScraper<CPUCooler> forCPUCooler(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<CPUCooler>()
                .withId("CPU-Air-Cooler")
                .withQuery((EAN, UPC, MPN) -> (CPUCooler) service.findLightByEanMPNUPCSN(CPUCooler.class, EAN, UPC, MPN).orElse(new CPUCooler()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Kuehlung+Luft/CPU+Kuehler.html"
                )
                .withSpecsTranslation((cpuCooler, specs) -> {
                    cpuCooler.setType(HardwareTypes.CoolerType.AIR);
                    cpuCooler.setSupportedSockets(extractEnumSet(HardwareTypes.CpuSocket.class, "für folgende Sockel geeignet:", specs, (s, socket) -> s.contains(socket.name())));
                })
                .build();
    }

    public static AbstractMultiMindfactoryScraper<CPUCooler> forCPULiquidCooler(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<CPUCooler>()
                .withId("CPU-Liquid-Cooler")
                .withQuery((EAN, UPC, MPN) -> (CPUCooler) service.findLightByEanMPNUPCSN(CPUCooler.class, EAN, UPC, MPN).orElse(new CPUCooler()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Kuehlung+Wasser+(WaKue)/All-in-One+WaKue+(AIO).html"
                )
                .withSpecsTranslation((cpuCooler, specs) -> {
                    cpuCooler.setType(HardwareTypes.CoolerType.AIO_LIQUID);
                    cpuCooler.setSupportedSockets(extractEnumSet(HardwareTypes.CpuSocket.class, "für folgende Sockel geeignet:", specs, (s, socket) -> s.contains(socket.name())));
                    cpuCooler.setRadiatorLengthMm(parseFirstInt("Anschlüsse:", specs));
                })
                .build();
    }

    public static AbstractMultiMindfactoryScraper<RAM> forRAM(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<RAM>()
                .withId("RAM")
                .withQuery((EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR5+Module.html",
                        "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR4+Module.html",
                        "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR3+Module.html",
                        "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR2+Module.html",
                        "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR5+ECC+Module.html",
                        "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR4+ECC+Module.html"
                )
                .withSpecsTranslation((ram, specs) -> {

                    ram.setType(extractFirstEnum(HardwareTypes.RamType.class, "Art des Speichers:", specs, (s, e) -> s.contains(e.name())));
                    ram.setSticks((int) parseFirstInt("Anzahl der Module:", specs));
                    ram.setSizeGb((int) parseFirstInt("Kapazität der Einzelmodule:", specs));
                    ram.setSpeedMtps((int) parseFirstInt("Max. Frequenz:", specs));

                    ram.setCasLatency((int) parseFirstInt("Latenz (CL):", specs));
                    ram.setCasLatency((int) parseFirstInt("RAS to CAS Delay (tRCD):", specs));
                    ram.setCasLatency((int) parseFirstInt("Ras Precharge Time (tRP):", specs));
                    ram.setCasLatency((int) parseFirstInt("Row Active Time (tRAS):", specs));
                })
                .build();
    }

    public static AbstractMultiMindfactoryScraper<Storage> forSSD(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<Storage>()
                .withId("SSD")
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Solid+State+Drives+(SSD).html"
                )
                .withSpecsTranslation((ssd, specs) -> {

                    int multiply = 1;
                    if (extractFirstString("Kapazität:", specs).contains("TB")) {
                        multiply = 1000;
                    }

                    ssd.setCapacityGb((int) (parseFirstInt("Kapazität:", specs) * multiply));
                    ssd.setStorageType(HardwareTypes.StorageType.SSD);
                    if (extractFirstString("Schnittstelle:", specs).contains("PCIe")) {
                        ssd.setStorageInterface(HardwareTypes.StorageInterface.NVME);
                    } else {
                        ssd.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                    }
                })
                .build();
    }

    public static AbstractMultiMindfactoryScraper<Storage> forHDD(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<Storage>()
                .withId("HDD")
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Festplatten+(HDD)/Interne+Festplatten.html"
                )
                .withSpecsTranslation((hdd, specs) -> {
                    hdd.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                    hdd.setStorageType(HardwareTypes.StorageType.HDD);
                    int multiply = 1;
                    if (extractFirstString("Kapazität:", specs).contains("TB")) {
                        multiply = 1000;
                    }

                    hdd.setCapacityGb((int) (parseFirstInt("Kapazität:", specs) * multiply));
                })
                .build();
    }
}
