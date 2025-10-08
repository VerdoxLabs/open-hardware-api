package de.verdox.openhardwareapi.io.websites.caseking;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.io.parser.ScrapeParser;
import de.verdox.openhardwareapi.model.*;
import de.verdox.openhardwareapi.model.values.DimensionsMm;
import de.verdox.openhardwareapi.model.values.M2Slot;
import de.verdox.openhardwareapi.model.values.PcieSlot;
import de.verdox.openhardwareapi.model.values.USBPort;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;

public class CaseKingScrapers {

    public static AbstractMultiCasekingScraper<CPU> forCPU(HardwareSpecService service) {
        return new AbstractMultiCasekingScraper.Builder<CPU>()
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
                .withQuery((EAN, UPC, MPN) -> (GPU) service.findLightByEanMPNUPCSN(GPU.class, EAN, UPC, MPN).orElse(new GPU()))
                .withURLs("https://www.caseking.de/pc-komponenten/grafikkarten/amd", "https://www.caseking.de/pc-komponenten/grafikkarten/nvidia", "https://www.caseking.de/pc-komponenten/grafikkarten/intel")
                .withSpecsTranslation((gpu, specs) -> {
                    gpu.setLengthMm((int) parseFirstDouble("Länge / Tiefe", specs));
                    gpu.setModel(extractFirstString("AMD Radeon RX 9060 XT 16GB", specs));
                    find(service, gpu, extractFirstString("GPU-Serienname", specs));

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
                .withQuery((EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()))
                .withURLs("https://www.caseking.de/pc-komponenten/arbeitsspeicher/ddr5", "https://www.caseking.de/pc-komponenten/arbeitsspeicher/ddr4")
                .withSpecsTranslation((ram, specs) -> {
                    ram.setType(extractFirstEnum(HardwareTypes.RamType.class, "Speichertyp", specs, (s, e) -> s.contains(e.name())));
                    ram.setSticks((int) parseFirstInt("Anzahl der Module", specs));
                    ram.setSizeGb((int) parseFirstInt("Speichergröße (gesamt)", specs));
                    ram.setSpeedMtps((int) parseFirstInt("Arbeitsspeicher Takt", specs));
                    ram.setCasLatency((int) parseFirstInt("CL (CAS-Latenz)", specs));
                    ram.setCasLatency((int) parseFirstInt("tRCD (RAS-to-CAS-Delay)", specs));
                    ram.setCasLatency((int) parseFirstInt("tRP (RAS-Precharge-Time)", specs));
                    ram.setCasLatency((int) parseFirstInt("tRAS (Row-Active-Time)", specs));
                })
                .build();
    }

    public static AbstractMultiCasekingScraper<PCCase> forCase(HardwareSpecService service) {
        return new AbstractMultiCasekingScraper.Builder<PCCase>()
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
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs("https://www.caseking.de/pc-komponenten/laufwerke/festplatten-hdds")
                .withSpecsTranslation((hdd, specs) -> {
                    hdd.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                    hdd.setStorageType(HardwareTypes.StorageType.HDD);
                    hdd.setCapacityGb((int) parseFirstInt("HDD-Kapazität (gesamt)", specs));
                })
                .build();
    }

    public static AbstractMultiCasekingScraper<Storage> forSSD(HardwareSpecService service) {
        return new AbstractMultiCasekingScraper.Builder<Storage>()
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs("https://www.caseking.de/pc-komponenten/laufwerke/solid-state-drives-ssd")
                .withSpecsTranslation((ssd, specs) -> {
                    ssd.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                    ssd.setStorageType(HardwareTypes.StorageType.SSD);
                    ssd.setCapacityGb((int) parseFirstInt("SSD-Kapazität (gesamt)", specs));
                })
                .build();
    }

    public static AbstractMultiCasekingScraper<PSU> forPSU(HardwareSpecService service) {
        return new AbstractMultiCasekingScraper.Builder<PSU>()
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
                .withQuery((EAN, UPC, MPN) -> (Motherboard) service.findLightByEanMPNUPCSN(Motherboard.class, EAN, UPC, MPN).orElse(new Motherboard()))
                .withURLs("https://www.caseking.de/pc-komponenten/mainboards/amd", "https://www.caseking.de/pc-komponenten/mainboards/intel")
                .withSpecsTranslation((mb, specs) -> {
                    mb.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "CPU-Sockel", specs, (s, cpuSocket) -> s.contains(cpuSocket.name())));
                    mb.setFormFactor(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Mainboard Formfaktor", specs, (s, e) -> s.contains(e.getName())));
                    mb.setChipset(extractFirstEnum(HardwareTypes.Chipset.class, "Mainboard-Chipsatz", specs, (s, e) -> e.name().equals(s.trim().replace("AMD ", "").replace("Intel ", ""))));
                    mb.setRamType(extractFirstEnum(HardwareTypes.RamType.class, "Speichertyp", specs, (s, e) -> s.contains(e.name())));
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
