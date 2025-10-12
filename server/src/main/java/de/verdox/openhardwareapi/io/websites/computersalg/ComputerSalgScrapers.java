package de.verdox.openhardwareapi.io.websites.computersalg;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.io.api.WebsiteScraper;
import de.verdox.openhardwareapi.io.parser.ScrapeParser;
import de.verdox.openhardwareapi.model.*;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;

public class ComputerSalgScrapers {

    public static WebsiteScraper createForComputerSalg(HardwareSpecService service) {
        return new WebsiteScraper()
                .withStrategy(new ComputerSalgScrapingStrategy())

                // --- CPU ---
                .withScrape(
                        "CPU",
                        CPU.class,
                        (EAN, UPC, MPN) -> (CPU) service.findLightByEanMPNUPCSN(CPU.class, EAN, UPC, MPN).orElse(new CPU()),
                        cpuScrape -> cpuScrape.addMainScrapeLogic((scraped, cpu) -> {
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
                                "https://www.computersalg.de/l/1486/amd-cpu",
                                "https://www.computersalg.de/l/1487/intel-cpu"
                        )
                )

                // --- Motherboard ---
                .withScrape(
                        "Motherboard",
                        Motherboard.class,
                        (EAN, UPC, MPN) -> (Motherboard) service.findLightByEanMPNUPCSN(Motherboard.class, EAN, UPC, MPN).orElse(new Motherboard()),
                        mbScrape -> mbScrape.addMainScrapeLogic((scraped, mb) -> {
                                    var specs = scraped.specs();
                                    mb.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "Mainboard Sockel:", specs, (s, e) -> s.contains(e.name())));
                                    mb.setChipset(extractFirstEnum(HardwareTypes.Chipset.class, "Mainboard Chipsatz:", specs,
                                            (s, e) -> e.name().equals(s.trim().replace("AMD ", "").replace("Intel ", ""))));
                                    mb.setFormFactor(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Mainboard Formfaktor:", specs,
                                            (s, e) -> s.contains(e.getName())));
                                    mb.setRamType(extractFirstEnum(HardwareTypes.RamType.class, "Arbeitsspeicher Typ:", specs, (s, e) -> e.name().contains(s)));
                                    mb.setRamSlots(4);
                                    mb.setRamCapacity((int) parseFirstInt("Max. Kapazität der Einzelmodule:", specs) * mb.getRamSlots());
                                },
                                "https://www.computersalg.de/l/1489/amd-mainboards",
                                "https://www.computersalg.de/l/1490/intel-mainboards"
                        )
                )

                // --- PSU ---
                .withScrape(
                        "PSU",
                        PSU.class,
                        (EAN, UPC, MPN) -> (PSU) service.findLightByEanMPNUPCSN(PSU.class, EAN, UPC, MPN).orElse(new PSU()),
                        psuScrape -> psuScrape.addMainScrapeLogic((scraped, psu) -> {
                                    var specs = scraped.specs();
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
                                "https://www.computersalg.de/l/1958/pc-server-netzteile"
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
                                            extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "kompatible Mainboardformate", specs,
                                                    (s, ff) -> s.contains(ff.name()))
                                    );
                                },
                                "https://www.computersalg.de/l/5446/alle-geh%c3%a4use"
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
                                "https://www.computersalg.de/l/4177/nvidia",
                                "https://www.computersalg.de/l/4178/amd"
                        )
                )

                // --- RAM: pro Typ eigene Feeds (wie bei deiner Vorlage) ---
                .withScrape(
                        "RAM-DDR5",
                        RAM.class,
                        (EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()),
                        ram5 -> ram5.addMainScrapeLogic((scraped, ram) -> {
                                    var specs = scraped.specs();
                                    ram.setType(HardwareTypes.RamType.DDR5);
                                    ram.setSticks((int) parseFirstInt("Anzahl der Module:", specs));
                                    ram.setSizeGb((int) parseFirstInt("Kapazität der Einzelmodule:", specs));
                                    ram.setSpeedMtps((int) parseFirstInt("Max. Frequenz:", specs));
                                    ram.setCasLatency((int) parseFirstInt("Latenz (CL):", specs));
                                    ram.setRowAddressToColumnAddressDelay((int) parseFirstInt("RAS to CAS Delay (tRCD):", specs));
                                    ram.setRowPrechargeTime((int) parseFirstInt("Ras Precharge Time (tRP):", specs));
                                    ram.setRowActiveTime((int) parseFirstInt("Row Active Time (tRAS):", specs));
                                },
                                "https://www.computersalg.de/l/7819/ddr5"
                        )
                )
                .withScrape(
                        "RAM-DDR4",
                        RAM.class,
                        (EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()),
                        ram4 -> ram4.addMainScrapeLogic((scraped, ram) -> {
                                    var specs = scraped.specs();
                                    ram.setType(HardwareTypes.RamType.DDR4);
                                    ram.setSticks((int) parseFirstInt("Anzahl der Module:", specs));
                                    ram.setSizeGb((int) parseFirstInt("Kapazität der Einzelmodule:", specs));
                                    ram.setSpeedMtps((int) parseFirstInt("Max. Frequenz:", specs));
                                    ram.setCasLatency((int) parseFirstInt("Latenz (CL):", specs));
                                    ram.setRowAddressToColumnAddressDelay((int) parseFirstInt("RAS to CAS Delay (tRCD):", specs));
                                    ram.setRowPrechargeTime((int) parseFirstInt("Ras Precharge Time (tRP):", specs));
                                    ram.setRowActiveTime((int) parseFirstInt("Row Active Time (tRAS):", specs));
                                },
                                "https://www.computersalg.de/l/1502/ddr4"
                        )
                )
                .withScrape(
                        "RAM-DDR3",
                        RAM.class,
                        (EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()),
                        ram3 -> ram3.addMainScrapeLogic((scraped, ram) -> {
                                    var specs = scraped.specs();
                                    ram.setType(HardwareTypes.RamType.DDR3);
                                    ram.setSticks((int) parseFirstInt("Anzahl der Module:", specs));
                                    ram.setSizeGb((int) parseFirstInt("Kapazität der Einzelmodule:", specs));
                                    ram.setSpeedMtps((int) parseFirstInt("Max. Frequenz:", specs));
                                },
                                "https://www.computersalg.de/l/1501/ddr3"
                        )
                )
                .withScrape(
                        "RAM-DDR2",
                        RAM.class,
                        (EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()),
                        ram2 -> ram2.addMainScrapeLogic((scraped, ram) -> {
                                    var specs = scraped.specs();
                                    ram.setType(HardwareTypes.RamType.DDR2);
                                    ram.setSticks((int) parseFirstInt("Anzahl der Module:", specs));
                                    ram.setSizeGb((int) parseFirstInt("Kapazität der Einzelmodule:", specs));
                                    ram.setSpeedMtps((int) parseFirstInt("Max. Frequenz:", specs));
                                },
                                "https://www.computersalg.de/l/1500/ddr2"
                        )
                )
                .withScrape(
                        "RAM-DDR",
                        RAM.class,
                        (EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()),
                        ram -> ram.addMainScrapeLogic((scraped, r) -> {
                                    var specs = scraped.specs();
                                    r.setType(HardwareTypes.RamType.DDR);
                                    r.setSticks((int) parseFirstInt("Anzahl der Module:", specs));
                                    r.setSizeGb((int) parseFirstInt("Kapazität der Einzelmodule:", specs));
                                    r.setSpeedMtps((int) parseFirstInt("Max. Frequenz:", specs));
                                },
                                "https://www.computersalg.de/l/1499/ddr"
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
                                "https://www.computersalg.de/l/238/ssd"
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
                                "https://www.computersalg.de/l/1320/interne-festplatten"
                        )
                );
    }


    public static AbstractComputerSalgMultiScraper<CPU> forCPU(HardwareSpecService service) {
        return new AbstractComputerSalgMultiScraper.Builder<CPU>()
                .withId("CPU")
                .withQuery((EAN, UPC, MPN) -> (CPU) service.findLightByEanMPNUPCSN(CPU.class, EAN, UPC, MPN).orElse(new CPU()))
                .withURLs(
                        "https://www.computersalg.de/l/1486/amd-cpu",
                        "https://www.computersalg.de/l/1487/intel-cpu"
                )
                .withSpecsTranslation((cpu, specs) -> {

                })
                .build();
    }

    public static AbstractComputerSalgMultiScraper<Motherboard> forMotherboard(HardwareSpecService service) {
        return new AbstractComputerSalgMultiScraper.Builder<Motherboard>()
                .withId("Motherboard")
                .withQuery((EAN, UPC, MPN) -> (Motherboard) service.findLightByEanMPNUPCSN(Motherboard.class, EAN, UPC, MPN).orElse(new Motherboard()))
                .withURLs(
                        "https://www.computersalg.de/l/1489/amd-mainboards",
                        "https://www.computersalg.de/l/1490/intel-mainboards"
                )
                .withSpecsTranslation((mb, specs) -> {

                })
                .build();
    }

    public static AbstractComputerSalgMultiScraper<PSU> forPSU(HardwareSpecService service) {
        return new AbstractComputerSalgMultiScraper.Builder<PSU>()
                .withId("PSU")
                .withQuery((EAN, UPC, MPN) -> (PSU) service.findLightByEanMPNUPCSN(PSU.class, EAN, UPC, MPN).orElse(new PSU()))
                .withURLs(
                        "https://www.computersalg.de/l/1958/pc-server-netzteile"
                )
                .withSpecsTranslation((psu, specs) -> {


                })
                .build();
    }

    public static AbstractComputerSalgMultiScraper<PCCase> forCase(HardwareSpecService service) {
        return new AbstractComputerSalgMultiScraper.Builder<PCCase>()
                .withId("PCCase")
                .withQuery((EAN, UPC, MPN) -> (PCCase) service.findLightByEanMPNUPCSN(PCCase.class, EAN, UPC, MPN).orElse(new PCCase()))
                .withURLs(
                        "https://www.computersalg.de/l/5446/alle-geh%c3%a4use"
                )
                .withSpecsTranslation((pcCase, specs) -> pcCase.setMotherboardSupport(extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "kompatible Mainboardformate", specs, (s, motherboardFormFactor) -> s.contains(motherboardFormFactor.name()))))
                .build();
    }

    public static AbstractComputerSalgMultiScraper<GPU> forGPU(HardwareSpecService service) {
        return new AbstractComputerSalgMultiScraper.Builder<GPU>()
                .withId("GPU")
                .withQuery((EAN, UPC, MPN) -> (GPU) service.findLightByEanMPNUPCSN(GPU.class, EAN, UPC, MPN).orElse(new GPU()))
                .withURLs(
                        "https://www.computersalg.de/l/4177/nvidia",
                        "https://www.computersalg.de/l/4178/amd"
                )
                .withSpecsTranslation((gpu, specs) -> {

                })
                .build();
    }

    public static AbstractComputerSalgMultiScraper<RAM> forRAMDDR5(HardwareSpecService service) {
        return new AbstractComputerSalgMultiScraper.Builder<RAM>()
                .withId("RAM-DDR5")
                .withQuery((EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()))
                .withURLs(
                        "https://www.computersalg.de/l/7819/ddr5"
                )
                .withSpecsTranslation((ram, specs) -> {
                    ram.setType(HardwareTypes.RamType.DDR5);
                })
                .build();
    }

    public static AbstractComputerSalgMultiScraper<RAM> forRAMDDR4(HardwareSpecService service) {
        return new AbstractComputerSalgMultiScraper.Builder<RAM>()
                .withId("RAM-DDR4")
                .withQuery((EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()))
                .withURLs(
                        "https://www.computersalg.de/l/1502/ddr4"
                )
                .withSpecsTranslation((ram, specs) -> {
                    ram.setType(HardwareTypes.RamType.DDR4);
                })
                .build();
    }

    public static AbstractComputerSalgMultiScraper<RAM> forRAMDDR3(HardwareSpecService service) {
        return new AbstractComputerSalgMultiScraper.Builder<RAM>()
                .withId("RAM-DDR3")
                .withQuery((EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()))
                .withURLs(
                        "https://www.computersalg.de/l/1501/ddr3"
                )
                .withSpecsTranslation((ram, specs) -> {
                    ram.setType(HardwareTypes.RamType.DDR3);
                })
                .build();
    }

    public static AbstractComputerSalgMultiScraper<RAM> forRAMDDR2(HardwareSpecService service) {
        return new AbstractComputerSalgMultiScraper.Builder<RAM>()
                .withId("RAM-DDR2")
                .withQuery((EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()))
                .withURLs(
                        "https://www.computersalg.de/l/1500/ddr2"
                )
                .withSpecsTranslation((ram, specs) -> {
                    ram.setType(HardwareTypes.RamType.DDR2);
                })
                .build();
    }

    public static AbstractComputerSalgMultiScraper<RAM> forRAMDDR(HardwareSpecService service) {
        return new AbstractComputerSalgMultiScraper.Builder<RAM>()
                .withId("RAM-DDR")
                .withQuery((EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()))
                .withURLs(
                        "https://www.computersalg.de/l/1499/ddr"
                )
                .withSpecsTranslation((ram, specs) -> {
                    ram.setType(HardwareTypes.RamType.DDR);
                })
                .build();
    }

    public static AbstractComputerSalgMultiScraper<Storage> forSSD(HardwareSpecService service) {
        return new AbstractComputerSalgMultiScraper.Builder<Storage>()
                .withId("SSD")
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs(
                        "https://www.computersalg.de/l/238/ssd"
                )
                .withSpecsTranslation((ssd, specs) -> {

                })
                .build();
    }

    public static AbstractComputerSalgMultiScraper<Storage> forHDD(HardwareSpecService service) {
        return new AbstractComputerSalgMultiScraper.Builder<Storage>()
                .withId("HDD")
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs(
                        "https://www.computersalg.de/l/1320/interne-festplatten"
                )
                .withSpecsTranslation((hdd, specs) -> {

                })
                .build();
    }
}
