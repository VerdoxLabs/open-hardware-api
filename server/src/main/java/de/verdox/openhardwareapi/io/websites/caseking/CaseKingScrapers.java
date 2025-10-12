package de.verdox.openhardwareapi.io.websites.caseking;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.io.api.WebsiteScraper;
import de.verdox.openhardwareapi.io.parser.ScrapeParser;
import de.verdox.openhardwareapi.io.pc_combo_scraper.RAMKomboScraper;
import de.verdox.openhardwareapi.model.*;
import de.verdox.openhardwareapi.model.values.DimensionsMm;
import de.verdox.openhardwareapi.model.values.M2Slot;
import de.verdox.openhardwareapi.model.values.PcieSlot;
import de.verdox.openhardwareapi.model.values.USBPort;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;

public class CaseKingScrapers {

    public static WebsiteScraper createForCaseking(HardwareSpecService service) {
        return new WebsiteScraper()
                .withStrategy(new CasekingScrapingStrategy())

                // --- CPU ---
                .withScrape(
                        "CPU",
                        CPU.class,
                        (EAN, UPC, MPN) -> (CPU) service.findLightByEanMPNUPCSN(CPU.class, EAN, UPC, MPN).orElse(new CPU()),
                        cpuScrape -> cpuScrape.addMainScrapeLogic((scraped, cpu) -> {
                                    var specs = scraped.specs();
                                    new ScrapeParser<CPU>(specs)
                                            .parseString("CPU-Hersteller", HardwareSpec::getManufacturer, HardwareSpec::setManufacturer)
                                            .parseEnum("CPU-Sockel", CPU::getSocket, CPU::setSocket,
                                                    (s, sock) -> s.toUpperCase().contains(sock.name().toUpperCase()) || sock.name().toUpperCase().contains(s.toUpperCase()),
                                                    HardwareTypes.CpuSocket.UNKNOWN)
                                            .parseNumber("CPU-Kerne", Integer::parseInt, CPU::getCores, CPU::setCores, 0)
                                            .parseNumber("Leistungs-Kerne", Integer::parseInt, CPU::getPerformanceCores, CPU::setPerformanceCores, 0)
                                            .parseNumber("Effizienz-Kerne", Integer::parseInt, CPU::getEfficiencyCores, CPU::setEfficiencyCores, 0)
                                            .parseNumber("CPU-Threads", Integer::parseInt, CPU::getThreads, CPU::setThreads, 0)
                                            .parseNumber("L3-Cache", Integer::parseInt, CPU::getL3CacheMb, CPU::setL3CacheMb, 0)
                                            .parseNumber("Level-3-Cache", Integer::parseInt, CPU::getL3CacheMb, CPU::setL3CacheMb, 0)
                                            .parseNumber("TDP", Integer::parseInt, CPU::getTdpWatts, CPU::setTdpWatts, 0)
                                            .parseNumber("max. CPU-Takt (Basis)", s -> Double.parseDouble(s) * 1000, CPU::getBaseClockMhz, CPU::setBaseClockMhz, 0d)
                                            .parseNumber("max. CPU-Takt (Turbo / Boost)", s -> Double.parseDouble(s) * 1000, CPU::getBoostClockMhz, CPU::setBoostClockMhz, 0d)
                                            .parseNumber("Basistakt", s -> Double.parseDouble(s) * 1000, CPU::getBaseClockMhz, CPU::setBaseClockMhz, 0d)
                                            .parseNumber("Boost-Takt", s -> Double.parseDouble(s) * 1000, CPU::getBoostClockMhz, CPU::setBoostClockMhz, 0d)
                                            .parseNumber("Basistakt (P-Kerne)", s -> Double.parseDouble(s) * 1000, CPU::getBaseClockMhzPerformance, CPU::setBaseClockMhzPerformance, 0d)
                                            .parseNumber("Basistakt (E-Kerne)", s -> Double.parseDouble(s) * 1000, CPU::getBaseClockMhzEfficiency, CPU::setBaseClockMhzEfficiency, 0d)
                                            .parse(cpu);
                                },
                                "https://www.caseking.de/pc-komponenten/cpus-prozessoren/amd",
                                "https://www.caseking.de/pc-komponenten/cpus-prozessoren/intel"
                        )
                )

                // --- GPU ---
                .withScrape(
                        "GPU",
                        GPU.class,
                        (EAN, UPC, MPN) -> (GPU) service.findLightByEanMPNUPCSN(GPU.class, EAN, UPC, MPN).orElse(new GPU()),
                        gpuScrape -> gpuScrape.addMainScrapeLogic((scraped, gpu) -> {
                                    var specs = scraped.specs();

                                    gpu.setLengthMm((int) parseFirstDouble("Länge / Tiefe", specs));
                                    gpu.setChip(find(service, gpu, extractFirstString("GPU-Serienname", specs)));

                                    String pcie = extractFirstString("Anschluss/Slot-Standard", specs);
                                    if ("PCIe 5.0".equals(pcie)) {
                                        gpu.setPcieVersion(HardwareTypes.PcieVersion.GEN5);
                                    } else if ("PCIe 4.0".equals(pcie)) {
                                        gpu.setPcieVersion(HardwareTypes.PcieVersion.GEN4);
                                    } else {
                                        gpu.setPcieVersion(HardwareTypes.PcieVersion.GEN3);
                                    }

                                    gpu.setVramType(extractFirstEnum(HardwareTypes.VRAM_TYPE.class, "GPU-Speichertyp", specs, (s, v) -> s.contains(v.name())));
                                    gpu.setVramGb(parseFirstInt("GPU-Speichergröße (GB)", specs));
                                },
                                "https://www.caseking.de/pc-komponenten/grafikkarten/amd",
                                "https://www.caseking.de/pc-komponenten/grafikkarten/nvidia",
                                "https://www.caseking.de/pc-komponenten/grafikkarten/intel"
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

                                    // Sticks: zwei mögliche Felder, nimm das erste sinnvolle
                                    int sticksA = (int) parseFirstInt("Anzahl der Module", specs);
                                    int sticksB = (int) parseFirstInt("RAM Riegel:", specs);
                                    ram.setSticks(sticksA > 0 ? sticksA : (sticksB > 0 ? sticksB : 0));

                                    // Größe: entweder Gesamtgröße / Sticks, oder Einzelkapazität
                                    int totalGb = (int) parseFirstInt("Speichergröße (gesamt)", specs);
                                    int perStickGb = (int) parseFirstInt("Kapazität:", specs);
                                    if (perStickGb > 0) {
                                        ram.setSizeGb(perStickGb);
                                    } else if (totalGb > 0 && ram.getSticks() > 0) {
                                        ram.setSizeGb(Math.max(1, totalGb / ram.getSticks()));
                                    }

                                    ram.setSpeedMtps((int) parseFirstInt("Arbeitsspeicher Takt", specs));

                                    // Timings – einzelne Felder, danach Fallback „Latenzen:“
                                    int cl = (int) parseFirstInt("CL (CAS-Latenz)", specs);
                                    int trcd = (int) parseFirstInt("tRCD (RAS-to-CAS-Delay)", specs);
                                    int trp = (int) parseFirstInt("tRP (RAS-Precharge-Time)", specs);
                                    int tras = (int) parseFirstInt("tRAS (Row-Active-Time)", specs);

                                    if (cl > 0) ram.setCasLatency(cl);
                                    if (trcd > 0) ram.setRowAddressToColumnAddressDelay(trcd);
                                    if (trp > 0) ram.setRowPrechargeTime(trp);
                                    if (tras > 0) ram.setRowActiveTime(tras);

                                    if (ram.getCasLatency() == 0) {
                                        int[] t = RAMKomboScraper.parseTimings(extractFirstString("Latenzen:", specs));
                                        if (t != null && t.length >= 4) {
                                            ram.setCasLatency(t[0]);
                                            ram.setRowAddressToColumnAddressDelay(t[1]);
                                            ram.setRowPrechargeTime(t[2]);
                                            ram.setRowActiveTime(t[3]);
                                        }
                                    }
                                },
                                "https://www.caseking.de/pc-komponenten/arbeitsspeicher/ddr5",
                                "https://www.caseking.de/pc-komponenten/arbeitsspeicher/ddr4"
                        )
                )

                // --- PC Case ---
                .withScrape(
                        "PCCase",
                        PCCase.class,
                        (EAN, UPC, MPN) -> (PCCase) service.findLightByEanMPNUPCSN(PCCase.class, EAN, UPC, MPN).orElse(new PCCase()),
                        caseScrape -> caseScrape.addMainScrapeLogic((scraped, pcCase) -> {
                                    var specs = scraped.specs();

                                    DimensionsMm d = new DimensionsMm();
                                    d.setHeight(parseFirstDouble("Höhe", specs));
                                    d.setWidth(parseFirstDouble("Breite", specs));
                                    d.setDepth(parseFirstDouble("Länge / Tiefe", specs));
                                    pcCase.setDimensions(d);

                                    pcCase.setMaxCpuCoolerHeightMm(parseFirstInt("max. CPU-Kühlerhöhe", specs));
                                    pcCase.setMaxGpuLengthMm(parseFirstInt("max. Grafikkartenlänge", specs));
                                    pcCase.setMotherboardSupport(
                                            extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "kompatible Mainboardformate", specs,
                                                    (s, ff) -> s.contains(ff.name()))
                                    );
                                    pcCase.setSizeClass(PCCase.classify(d));
                                },
                                "https://www.caseking.de/gehaeuse-und-modding/computer-gehaeuse"
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

                                    int mul = 1;
                                    String capStr = extractFirstString("HDD-Kapazität (gesamt)", specs);
                                    if (capStr == null || capStr.isBlank()) capStr = extractFirstString("Kapazität:", specs);
                                    if (capStr != null && capStr.contains("TB")) mul = 1000;

                                    int cap = (int) parseFirstInt("HDD-Kapazität (gesamt)", specs);
                                    if (cap <= 0) cap = (int) parseFirstInt("Kapazität:", specs);
                                    hdd.setCapacityGb(cap * mul);
                                },
                                "https://www.caseking.de/pc-komponenten/laufwerke/festplatten-hdds"
                        )
                )

                // --- SSD ---
                .withScrape(
                        "SSD",
                        Storage.class,
                        (EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()),
                        ssdScrape -> ssdScrape.addMainScrapeLogic((scraped, ssd) -> {
                                    var specs = scraped.specs();

                                    // Interface: erst Modell-Heuristik, dann Fallback auf Feld
                                    if (ssd.getModel() != null && ssd.getModel().toUpperCase().contains("NVME")) {
                                        ssd.setStorageInterface(HardwareTypes.StorageInterface.NVME);
                                    } else {
                                        String iface = extractFirstString("Schnittstelle", specs);
                                        if (iface != null && iface.toUpperCase().contains("PCI")) {
                                            ssd.setStorageInterface(HardwareTypes.StorageInterface.NVME);
                                        } else {
                                            ssd.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                                        }
                                    }
                                    ssd.setStorageType(HardwareTypes.StorageType.SSD);

                                    int mul = 1;
                                    String capStr = extractFirstString("SSD-Kapazität (gesamt)", specs);
                                    if (capStr == null || capStr.isBlank()) capStr = extractFirstString("Kapazität:", specs);
                                    if (capStr != null && capStr.contains("TB")) mul = 1000;

                                    int cap = (int) parseFirstInt("SSD-Kapazität (gesamt)", specs);
                                    if (cap <= 0) cap = (int) parseFirstInt("Kapazität:", specs);
                                    ssd.setCapacityGb(cap * mul);
                                },
                                "https://www.caseking.de/pc-komponenten/laufwerke/solid-state-drives-ssd"
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
                                    psu.setEfficiencyRating(extractFirstEnum(HardwareTypes.PsuEfficiencyRating.class, "80PLUS Effizienz", specs,
                                            (s, r) -> s.toUpperCase().contains(r.name())));

                                    boolean modular = parseBoolean("modulares Netzteil", specs);
                                    if (!modular) {
                                        psu.setModularity(HardwareTypes.PSU_MODULARITY.NON_MODULAR);
                                    } else {
                                        String modularityType = extractFirstString("modularer Typ", specs);
                                        if ("vollmodular".equalsIgnoreCase(modularityType)) {
                                            psu.setModularity(HardwareTypes.PSU_MODULARITY.FULL_MODULAR);
                                        } else {
                                            psu.setModularity(HardwareTypes.PSU_MODULARITY.SEMI_MODULAR);
                                        }
                                    }

                                    // Hinweis: Wenn du einen dedizierten PSU-Formfaktor-Enum hast, hier tauschen.
                                    psu.setSize(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Netzteilformat", specs,
                                            (s, f) -> s.toUpperCase().contains(f.name())));
                                },
                                "https://www.caseking.de/pc-komponenten/netzteile"
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
                                    mb.setFormFactor(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Mainboard Formfaktor", specs, (s, e) -> s.contains(e.getName())));
                                    mb.setChipset(extractFirstEnum(HardwareTypes.Chipset.class, "Mainboard-Chipsatz", specs,
                                            (s, e) -> e.name().equals(s.trim().replace("AMD ", "").replace("Intel ", ""))));
                                    mb.setRamType(extractFirstEnum(HardwareTypes.RamType.class, "Speichertyp", specs, (s, e) -> e.name().contains(s)));
                                    mb.setRamSlots((int) parseFirstInt("Speicherslots", specs));
                                    mb.setRamCapacity((int) parseFirstInt("max. Arbeitsspeicher", specs));
                                    mb.setSataSlots((int) parseFirstInt("SATA 6G (intern)", specs));

                                    String[] types = new String[] {"M.2 PCIe", "PCIe"};
                                    String[] pciGens = new String[] {"1.0", "2.0", "3.0", "4.0", "5.0", "6.0"};
                                    String[] pciLanes = new String[] {"x1", "x2", "x4", "x8", "x16"};
                                    String[] suffixes = new String[] {"(mechanisch)", ""};

                                    for (int ti = 0; ti < types.length; ti++) {
                                        for (int gi = 0; gi < pciGens.length; gi++) {
                                            for (int li = 0; li < pciLanes.length; li++) {
                                                for (int si = 0; si < suffixes.length; si++) {
                                                    String search = types[ti] + " " + pciGens[gi] + " " + pciLanes[li] + " " + suffixes[si];
                                                    int amount = (int) parseFirstInt(search, specs);

                                                    if (ti == 0) { // M.2
                                                        mb.getM2Slots().add(new M2Slot(HardwareTypes.PcieVersion.values()[gi], HardwareTypes.StorageInterface.NVME, amount));
                                                    } else { // PCIe
                                                        mb.getPcieSlots().add(new PcieSlot(HardwareTypes.PcieVersion.values()[gi], li > 0 ? 2 * li : 1, amount));
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    mb.setUsb3Headers((int) parseFirstInt("USB 3.0 (intern) Header", specs));

                                    int usb3_2C = (int) parseFirstInt("USB 3.2 (extern) Type C", specs);
                                    if (usb3_2C > 0) mb.getUsbPort().add(new USBPort(HardwareTypes.UsbConnectorType.USB_C, HardwareTypes.UsbVersion.USB3_2_GEN2, usb3_2C));

                                    int usb3_1A = (int) parseFirstInt("USB 3.1 (extern) Type A", specs);
                                    if (usb3_1A > 0) mb.getUsbPort().add(new USBPort(HardwareTypes.UsbConnectorType.USB_A, HardwareTypes.UsbVersion.USB3_1, usb3_1A));

                                    int usb3_0A = (int) parseFirstInt("USB 3.0 (extern) Type A", specs);
                                    if (usb3_0A > 0) mb.getUsbPort().add(new USBPort(HardwareTypes.UsbConnectorType.USB_A, HardwareTypes.UsbVersion.USB3_0, usb3_0A));

                                    int usb2_0A = (int) parseFirstInt("USB 2.0 (extern) Type A", specs);
                                    if (usb2_0A > 0) mb.getUsbPort().add(new USBPort(HardwareTypes.UsbConnectorType.USB_A, HardwareTypes.UsbVersion.USB2_0, usb2_0A));
                                },
                                "https://www.caseking.de/pc-komponenten/mainboards/amd",
                                "https://www.caseking.de/pc-komponenten/mainboards/intel"
                        )
                );
    }


    public static AbstractMultiCasekingScraper<CPU> forCPU(HardwareSpecService service) {
        return new AbstractMultiCasekingScraper.Builder<CPU>()
                .withId("CPU")
                .withQuery((EAN, UPC, MPN) -> (CPU) service.findLightByEanMPNUPCSN(CPU.class, EAN, UPC, MPN).orElse(new CPU()))
                .withURLs("https://www.caseking.de/pc-komponenten/cpus-prozessoren/amd", "https://www.caseking.de/pc-komponenten/cpus-prozessoren/intel")
                .withSpecsTranslation((cpu, specs) -> {
                            new ScrapeParser<CPU>(specs)
                                    .parseString("CPU-Hersteller", HardwareSpec::getManufacturer, HardwareSpec::setManufacturer)
                                    .parseEnum("CPU-Sockel", CPU::getSocket, CPU::setSocket, (s, cpuSocket) -> s.toUpperCase().contains(cpuSocket.name().toUpperCase()) || cpuSocket.name().toUpperCase().contains(s.toUpperCase()), HardwareTypes.CpuSocket.UNKNOWN)
                                    .parseNumber("CPU-Kerne", Integer::parseInt, CPU::getCores, CPU::setCores, 0)

                                    .parseNumber("Leistungs-Kerne", Integer::parseInt, CPU::getPerformanceCores, CPU::setPerformanceCores, 0)
                                    .parseNumber("Effizienz-Kerne", Integer::parseInt, CPU::getEfficiencyCores, CPU::setEfficiencyCores, 0)

                                    .parseNumber("CPU-Threads", Integer::parseInt, CPU::getThreads, CPU::setThreads, 0)
                                    .parseNumber("L3-Cache", Integer::parseInt, CPU::getL3CacheMb, CPU::setL3CacheMb, 0)
                                    .parseNumber("Level-3-Cache", Integer::parseInt, CPU::getL3CacheMb, CPU::setL3CacheMb, 0)
                                    .parseNumber("TDP", Integer::parseInt, CPU::getTdpWatts, CPU::setTdpWatts, 0)

                                    .parseNumber("max. CPU-Takt (Basis)", s -> Double.parseDouble(s) * 1000, CPU::getBaseClockMhz, CPU::setBaseClockMhz, 0d)
                                    .parseNumber("max. CPU-Takt (Turbo / Boost)", s -> Double.parseDouble(s) * 1000, CPU::getBoostClockMhz, CPU::setBoostClockMhz, 0d)

                                    .parseNumber("Basistakt", s -> Double.parseDouble(s) * 1000, CPU::getBaseClockMhz, CPU::setBaseClockMhz, 0d)
                                    .parseNumber("Boost-Takt", s -> Double.parseDouble(s) * 1000, CPU::getBoostClockMhz, CPU::setBoostClockMhz, 0d)

                                    .parseNumber("Basistakt (P-Kerne)", s -> Double.parseDouble(s) * 1000, CPU::getBaseClockMhzPerformance, CPU::setBaseClockMhzPerformance, 0d)
                                    .parseNumber("Basistakt (E-Kerne)", s -> Double.parseDouble(s) * 1000, CPU::getBaseClockMhzEfficiency, CPU::setBaseClockMhzEfficiency, 0d)
                                    .parse(cpu);
                        }
                )
                .build();

    }

    public static AbstractMultiCasekingScraper<GPU> forGPU(HardwareSpecService service) {
        return new AbstractMultiCasekingScraper.Builder<GPU>()
                .withId("GPU")
                .withQuery((EAN, UPC, MPN) -> (GPU) service.findLightByEanMPNUPCSN(GPU.class, EAN, UPC, MPN).orElse(new GPU()))
                .withURLs("https://www.caseking.de/pc-komponenten/grafikkarten/amd", "https://www.caseking.de/pc-komponenten/grafikkarten/nvidia", "https://www.caseking.de/pc-komponenten/grafikkarten/intel")
                .withSpecsTranslation((gpu, specs) -> {
                    gpu.setLengthMm((int) parseFirstDouble("Länge / Tiefe", specs));

                    gpu.setChip(find(service, gpu, extractFirstString("GPU-Serienname", specs)));

                    String pcie = extractFirstString("Anschluss/Slot-Standard", specs);
                    if (pcie.equals("PCIe 5.0")) {
                        gpu.setPcieVersion(HardwareTypes.PcieVersion.GEN5);
                    } else if (pcie.equals("PCIe 4.0")) {
                        gpu.setPcieVersion(HardwareTypes.PcieVersion.GEN4);
                    } else {
                        gpu.setPcieVersion(HardwareTypes.PcieVersion.GEN3);
                    }

                    gpu.setVramType(extractFirstEnum(HardwareTypes.VRAM_TYPE.class, "GPU-Speichertyp", specs, (s, vramType) -> s.contains(vramType.name())));
                    gpu.setVramGb(parseFirstInt("GPU-Speichergröße (GB)", specs));
                })
                .build();
    }

    public static AbstractMultiCasekingScraper<RAM> forRAM(HardwareSpecService service) {
        return new AbstractMultiCasekingScraper.Builder<RAM>()
                .withId("RAM")
                .withQuery((EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()))
                .withURLs("https://www.caseking.de/pc-komponenten/arbeitsspeicher/ddr5", "https://www.caseking.de/pc-komponenten/arbeitsspeicher/ddr4")
                .withSpecsTranslation((ram, specs) -> {
                    ram.setType(extractFirstEnum(HardwareTypes.RamType.class, "Speichertyp", specs, (s, e) -> s.contains(e.name())));
                    ram.setSticks((int) parseFirstInt("Anzahl der Module", specs));
                    ram.setSticks((int) parseFirstInt("RAM Riegel:", specs));

                    ram.setSizeGb((int) parseFirstInt("Speichergröße (gesamt)", specs) / Math.max(1, ram.getSticks()));
                    ram.setSizeGb((int) parseFirstInt("Kapazität:", specs));
                    ram.setSpeedMtps((int) parseFirstInt("Arbeitsspeicher Takt", specs));
                    ram.setCasLatency((int) parseFirstInt("CL (CAS-Latenz)", specs));
                    ram.setCasLatency((int) parseFirstInt("tRCD (RAS-to-CAS-Delay)", specs));
                    ram.setCasLatency((int) parseFirstInt("tRP (RAS-Precharge-Time)", specs));
                    ram.setCasLatency((int) parseFirstInt("tRAS (Row-Active-Time)", specs));

                    if (ram.getCasLatency() == 0) {

                        int[] timings = RAMKomboScraper.parseTimings(extractFirstString("Latenzen:", specs));
                        ram.setCasLatency(timings[0]);
                        ram.setRowAddressToColumnAddressDelay(timings[1]);
                        ram.setRowPrechargeTime(timings[2]);
                        ram.setRowActiveTime(timings[3]);

                    }

                })
                .build();
    }

    public static AbstractMultiCasekingScraper<PCCase> forCase(HardwareSpecService service) {
        return new AbstractMultiCasekingScraper.Builder<PCCase>()
                .withId("PCCase")
                .withQuery((EAN, UPC, MPN) -> (PCCase) service.findLightByEanMPNUPCSN(PCCase.class, EAN, UPC, MPN).orElse(new PCCase()))
                .withURLs("https://www.caseking.de/gehaeuse-und-modding/computer-gehaeuse")
                .withSpecsTranslation((pcCase, specs) -> {

                    DimensionsMm dimensionsMm = new DimensionsMm();
                    dimensionsMm.setHeight(parseFirstDouble("Höhe", specs));
                    dimensionsMm.setWidth(parseFirstDouble("Breite", specs));
                    dimensionsMm.setDepth(parseFirstDouble("Länge / Tiefe", specs));
                    pcCase.setDimensions(dimensionsMm);

                    pcCase.setMaxCpuCoolerHeightMm(parseFirstInt("max. CPU-Kühlerhöhe", specs));
                    pcCase.setMaxGpuLengthMm(parseFirstInt("max. Grafikkartenlänge", specs));
                    pcCase.setMotherboardSupport(extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "kompatible Mainboardformate", specs, (s, motherboardFormFactor) -> s.contains(motherboardFormFactor.name())));
                    pcCase.setSizeClass(PCCase.classify(dimensionsMm));
                })
                .build();
    }

    public static AbstractMultiCasekingScraper<Storage> forHDD(HardwareSpecService service) {
        return new AbstractMultiCasekingScraper.Builder<Storage>()
                .withId("HDD")
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs("https://www.caseking.de/pc-komponenten/laufwerke/festplatten-hdds")
                .withSpecsTranslation((hdd, specs) -> {
                    hdd.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                    hdd.setStorageType(HardwareTypes.StorageType.HDD);

                    int multiply = 1;
                    if (extractFirstString("SSD-Kapazität (gesamt)", specs).contains("TB")) {
                        multiply = 1000;
                    }

                    hdd.setCapacityGb((int) parseFirstInt("HDD-Kapazität (gesamt)", specs) * multiply);
                    hdd.setCapacityGb((int) parseFirstInt("Kapazität:", specs) * multiply);
                })
                .build();
    }

    public static AbstractMultiCasekingScraper<Storage> forSSD(HardwareSpecService service) {
        return new AbstractMultiCasekingScraper.Builder<Storage>()
                .withId("SSD")
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs("https://www.caseking.de/pc-komponenten/laufwerke/solid-state-drives-ssd")
                .withSpecsTranslation((ssd, specs) -> {

                    if (ssd.getModel().contains("NVMe")) {
                        ssd.setStorageInterface(HardwareTypes.StorageInterface.NVME);
                    } else {
                        ssd.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                    }
                    ssd.setStorageType(HardwareTypes.StorageType.SSD);

                    int multiply = 1;
                    if (extractFirstString("SSD-Kapazität (gesamt)", specs).contains("TB")) {
                        multiply = 1000;
                    }

                    ssd.setCapacityGb((int) parseFirstInt("SSD-Kapazität (gesamt)", specs) * multiply);
                    ssd.setCapacityGb((int) parseFirstInt("Kapazität:", specs) * multiply);
                })
                .build();
    }

    public static AbstractMultiCasekingScraper<PSU> forPSU(HardwareSpecService service) {
        return new AbstractMultiCasekingScraper.Builder<PSU>()
                .withId("PSU")
                .withQuery((EAN, UPC, MPN) -> (PSU) service.findLightByEanMPNUPCSN(PSU.class, EAN, UPC, MPN).orElse(new PSU()))
                .withURLs("https://www.caseking.de/pc-komponenten/netzteile")
                .withSpecsTranslation((psu, specs) -> {
                    psu.setWattage((int) parseFirstInt("Leistung", specs));
                    psu.setEfficiencyRating(extractFirstEnum(HardwareTypes.PsuEfficiencyRating.class, "80PLUS Effizienz", specs, (s, psuEfficiencyRating) -> s.toUpperCase().contains(psuEfficiencyRating.name())));
                    if (!parseBoolean("modulares Netzteil", specs)) {
                        psu.setModularity(HardwareTypes.PSU_MODULARITY.NON_MODULAR);
                    } else {
                        String modularityType = extractFirstString("modularer Typ", specs);
                        if (modularityType.equals("vollmodular")) {
                            psu.setModularity(HardwareTypes.PSU_MODULARITY.FULL_MODULAR);
                        } else {
                            psu.setModularity(HardwareTypes.PSU_MODULARITY.SEMI_MODULAR);
                        }
                    }
                    psu.setSize(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Netzteilformat", specs, (s, psuEfficiencyRating) -> s.toUpperCase().contains(psuEfficiencyRating.name())));
                })
                .build();
    }

    public static AbstractMultiCasekingScraper<Motherboard> forMotherboard(HardwareSpecService service) {
        return new AbstractMultiCasekingScraper.Builder<Motherboard>()
                .withId("Motherboard")
                .withQuery((EAN, UPC, MPN) -> (Motherboard) service.findLightByEanMPNUPCSN(Motherboard.class, EAN, UPC, MPN).orElse(new Motherboard()))
                .withURLs("https://www.caseking.de/pc-komponenten/mainboards/amd", "https://www.caseking.de/pc-komponenten/mainboards/intel")
                .withSpecsTranslation((mb, specs) -> {
                    mb.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "CPU-Sockel", specs, (s, cpuSocket) -> s.contains(cpuSocket.name())));
                    mb.setFormFactor(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Mainboard Formfaktor", specs, (s, e) -> s.contains(e.getName())));
                    mb.setChipset(extractFirstEnum(HardwareTypes.Chipset.class, "Mainboard-Chipsatz", specs, (s, e) -> e.name().equals(s.trim().replace("AMD ", "").replace("Intel ", ""))));
                    mb.setRamType(extractFirstEnum(HardwareTypes.RamType.class, "Speichertyp", specs, (s, e) -> e.name().contains(s)));
                    mb.setRamSlots((int) parseFirstInt("Speicherslots", specs));
                    mb.setRamCapacity((int) parseFirstInt("max. Arbeitsspeicher", specs));
                    mb.setSataSlots((int) parseFirstInt("SATA 6G (intern)", specs));

                    String[] types = new String[]{"M.2 PCIe", "PCIe"};
                    String[] pciGens = new String[]{"1.0", "2.0", "3.0", "4.0", "5.0", "6.0"};
                    String[] pciLanes = new String[]{"x1", "x2", "x4", "x8", "x16"};
                    String[] suffixes = new String[]{"(mechanisch)", ""};

                    for (int typeI = 0; typeI < types.length; typeI++) {
                        for (int pciGenI = 0; pciGenI < pciGens.length; pciGenI++) {
                            for (int pciLaneI = 0; pciLaneI < pciLanes.length; pciLaneI++) {
                                for (int suffixI = 0; suffixI < suffixes.length; suffixI++) {
                                    String type = types[typeI];
                                    String pciGen = pciGens[pciGenI];
                                    String pciLane = pciLanes[pciLaneI];
                                    String suffix = suffixes[suffixI];

                                    String search = type + " " + pciGen + " " + pciLane + " " + suffix;
                                    int amount = (int) parseFirstInt(search, specs);

                                    if (typeI == 0) {
                                        mb.getM2Slots().add(new M2Slot(HardwareTypes.PcieVersion.values()[pciGenI], HardwareTypes.StorageInterface.NVME, amount));
                                    } else {
                                        mb.getPcieSlots().add(new PcieSlot(HardwareTypes.PcieVersion.values()[pciGenI], pciLaneI > 0 ? 2 * pciLaneI : 1, amount));
                                    }
                                }
                            }
                        }
                    }

                    mb.setUsb3Headers((int) parseFirstInt("USB 3.0 (intern) Header", specs));

                    int usb3_2C = (int) parseFirstInt("USB 3.2 (extern) Type C", specs);
                    if (usb3_2C > 0) {
                        mb.getUsbPort().add(new USBPort(HardwareTypes.UsbConnectorType.USB_C, HardwareTypes.UsbVersion.USB3_2_GEN2, usb3_2C));
                    }
                    int usb3_1A = (int) parseFirstInt("USB 3.1 (extern) Type A", specs);
                    if (usb3_1A > 0) {
                        mb.getUsbPort().add(new USBPort(HardwareTypes.UsbConnectorType.USB_A, HardwareTypes.UsbVersion.USB3_1, usb3_2C));
                    }
                    int usb3_0A = (int) parseFirstInt("USB 3.0 (extern) Type A", specs);
                    if (usb3_0A > 0) {
                        mb.getUsbPort().add(new USBPort(HardwareTypes.UsbConnectorType.USB_A, HardwareTypes.UsbVersion.USB3_0, usb3_2C));
                    }
                    int usb2_0A = (int) parseFirstInt("USB 2.0 (extern) Type A", specs);
                    if (usb2_0A > 0) {
                        mb.getUsbPort().add(new USBPort(HardwareTypes.UsbConnectorType.USB_A, HardwareTypes.UsbVersion.USB2_0, usb3_2C));
                    }
                })
                .build();
    }
}
