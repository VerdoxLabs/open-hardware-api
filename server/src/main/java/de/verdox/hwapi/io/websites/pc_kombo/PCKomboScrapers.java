package de.verdox.hwapi.io.websites.pc_kombo;

import de.verdox.hwapi.hardwareapi.component.service.HardwareSpecService;
import de.verdox.hwapi.hardwareapi.component.service.ScrapingService;
import de.verdox.hwapi.io.api.WebsiteScraper;
import de.verdox.hwapi.model.HardwareTypes;
import de.verdox.hwapi.model.PCCase;
import de.verdox.hwapi.model.values.*;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import static de.verdox.hwapi.io.api.ComponentWebScraper.*;

public class PCKomboScrapers {
    public static WebsiteScraper create(HardwareSpecService service) {
        return new WebsiteScraper(service, "pc-kombo.com")
                .withStrategy(new PCKomboStrategy())

                .withCPUScrape(cpu -> cpu.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "Socket", specs, (s, e) -> e.name().contains(s)));
                            target.setTdpWatts((int) parseFirstInt("TDP", specs));
                            target.setIntegratedGraphics(extractFirstString("Integrated graphics", specs));
                            target.setCores((int) parseFirstInt("Cores", specs));
                            target.setThreads((int) parseFirstInt("Threads", specs));
                            target.setBaseClockMhz(parseFirstInt("Base Clock", specs) * 1000);
                            target.setBoostClockMhz(parseFirstInt("Turbo Clock", specs) * 1000);
                            target.setL3CacheMb((int) parseFirstInt("L3 Cache", specs));
                        },
                        "https://www.pc-kombo.com/us/components/cpus"))

                .withDisplayScrape(
                        disp -> disp.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setManufacturer(specs.get("Producer").getFirst());
                            String[] resolutions = extractFirstString("Resolution", specs).split("x");
                            target.setRefreshRate((int) parseFirstInt("Refresh Rate", specs));
                            target.setDisplayPanel(extractFirstEnum(HardwareTypes.DisplayPanel.class, "Panel", specs, (s, p) -> p.getName().equalsIgnoreCase(s)));
                            target.setDisplaySyncs(extractEnumSet(HardwareTypes.DisplaySync.class, "Sync", specs, (s, syn) -> syn.getName().equalsIgnoreCase(s)));
                            target.setHdmiPorts((int) parseFirstInt("HDMI", specs));
                            target.setDisplayPorts((int) parseFirstInt("DisplayPort", specs));
                            target.setDviPorts((int) parseFirstInt("DVI", specs));
                            target.setVgaPorts((int) parseFirstInt("VGA", specs));
                            target.setResponseTimeMS(parseFirstDouble("Response Time", specs));
                            target.setInchSize(parseFirstDouble("Size", specs));
                            if (resolutions.length >= 1) target.setResWidth(parseIntSafely(resolutions[0]));
                            if (resolutions.length >= 2) target.setResHeight(parseIntSafely(resolutions[1]));
                            target.setIntegratedSpeakers(parseBoolean("Speakers", specs));
                            target.setCurved(parseBoolean("Curved", specs));
                            target.setAdjustableSize(parseBoolean("Adjustable Height", specs));
                        })
                )

                .withMotherboardScrape(mb -> mb.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "Socket", specs, (s, e) -> e.name().contains(s)));
                            target.setChipset(extractFirstEnum(HardwareTypes.Chipset.class, "Chipset", specs, (s, e) -> e.name().contains(s)));
                            target.setFormFactor(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Motherboard", specs, (s, ff) -> ff.getName().equalsIgnoreCase(s)));
                            target.setRamType(extractFirstEnum(HardwareTypes.RamType.class, "Memory Type", specs, (s, e) -> e.name().contains(s)));
                            target.setRamSlots((int) parseFirstInt("Ramslots", specs));
                            target.setRamCapacity((int) parseFirstInt("Memory Capacity", specs));
                            target.setSataSlots((int) parseFirstInt("SATA", specs));

                            Set<M2Slot> m2Slots = new HashSet<>();
                            Set<PcieSlot> pcieSlots = new HashSet<>();
                            Set<USBPort> usbPort = new HashSet<>();

                            int m2Gen3Slots = Math.toIntExact(parseFirstInt("M.2 (PCI-E 3.0)", specs));
                            int m2Gen4Slots = Math.toIntExact(parseFirstInt("M.2 (PCI-E 4.0)", specs));
                            if (m2Gen3Slots > 0)
                                m2Slots.add(new M2Slot(HardwareTypes.PcieVersion.GEN3, HardwareTypes.StorageInterface.NVME, m2Gen3Slots));
                            if (m2Gen4Slots > 0)
                                m2Slots.add(new M2Slot(HardwareTypes.PcieVersion.GEN4, HardwareTypes.StorageInterface.NVME, m2Gen4Slots));

                            int p3x1 = Math.toIntExact(parseFirstInt("PCI-E 3.0 x1", specs));
                            int p3x4 = Math.toIntExact(parseFirstInt("PCI-E 3.0 x4", specs));
                            int p3x8 = Math.toIntExact(parseFirstInt("PCI-E 3.0 x8", specs));
                            int p3x16 = Math.toIntExact(parseFirstInt("PCI-E 3.0 x16", specs));
                            int p4x1 = Math.toIntExact(parseFirstInt("PCI-E 4.0 x1", specs));
                            int p4x4 = Math.toIntExact(parseFirstInt("PCI-E 4.0 x4", specs));
                            int p4x8 = Math.toIntExact(parseFirstInt("PCI-E 4.0 x8", specs));
                            int p4x16 = Math.toIntExact(parseFirstInt("PCI-E 4.0 x16", specs));

                            if (p3x1 > 0) pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN3, 1, p3x1));
                            if (p3x4 > 0) pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN3, 4, p3x4));
                            if (p3x8 > 0) pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN3, 8, p3x8));
                            if (p3x16 > 0) pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN3, 16, p3x16));
                            if (p4x1 > 0) pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN4, 1, p4x1));
                            if (p4x4 > 0) pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN4, 4, p4x4));
                            if (p4x8 > 0) pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN4, 8, p4x8));
                            if (p4x16 > 0) pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN4, 16, p4x16));

                            int usb3Slots = Math.toIntExact(parseFirstInt("USB 3 Slots", specs));
                            if (usb3Slots > 0)
                                usbPort.add(new USBPort(HardwareTypes.UsbConnectorType.USB_A, HardwareTypes.UsbVersion.USB3_2_GEN1, usb3Slots));
                            int usbC = Math.toIntExact(parseFirstInt("USB 3 Type-C", specs));
                            if (usbC > 0)
                                usbPort.add(new USBPort(HardwareTypes.UsbConnectorType.USB_C, HardwareTypes.UsbVersion.USB3_2_GEN1, usbC));

                            target.setM2Slots(m2Slots);
                            target.setPcieSlots(pcieSlots);
                            target.setUsbPort(usbPort);
                            target.setUsb3Headers(Math.toIntExact(parseFirstInt("USB 3 Headers", specs)));
                        },
                        "https://www.pc-kombo.com/us/components/motherboards"))

                .withPSUScraper(psu -> psu.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setWattage((int) parseFirstInt("Watt", specs));
                            target.setSize(extractFirstEnum(HardwareTypes.PSUFormFactor.class, "Size", specs, (s, ff) -> ff.getName().equalsIgnoreCase(s)));
                            target.setEfficiencyRating(extractFirstEnum(HardwareTypes.PsuEfficiencyRating.class, "Efficiency Rating", specs, (s, e) -> e.name().contains(s.toUpperCase())));
                            Set<PowerConnector> cons = new HashSet<>();
                            int c8 = Math.toIntExact(parseFirstInt("PCI-E cables 8-pin", specs));
                            int c6 = Math.toIntExact(parseFirstInt("PCI-E cables 6-pin", specs));
                            if (c8 > 0) cons.add(new PowerConnector(HardwareTypes.PowerConnectorType.PCIE_8_PIN, c8));
                            if (c6 > 0) cons.add(new PowerConnector(HardwareTypes.PowerConnectorType.PCIE_6_PIN, c6));
                            cons.add(new PowerConnector(HardwareTypes.PowerConnectorType.ATX_24_PIN, 1));
                            target.setConnectors(cons);
                        },
                        "https://www.pc-kombo.com/us/components/psus"))

                .withPCCaseScraper(cs -> cs.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setManufacturer(specs.get("Producer").getFirst());
                            target.setDimensions(new DimensionsMm());
                            target.getDimensions().setWidth(parseFirstDouble("Width", specs));
                            target.getDimensions().setDepth(parseFirstDouble("Depth", specs));
                            target.getDimensions().setHeight(parseFirstDouble("Height", specs));
                            target.setSizeClass(PCCase.classify(target.getDimensions()));
                            if (target.getModel().contains("Midi-Tower") || target.getModel().contains("Midi"))
                                target.setSizeClass(HardwareTypes.CaseSizeClass.MID_TOWER);
                            else if (target.getModel().contains("Big-Tower") || target.getModel().contains("Big Tower"))
                                target.setSizeClass(HardwareTypes.CaseSizeClass.FULL_TOWER);
                            target.setMotherboardSupport(extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "Motherboard", specs, (s, ff) -> ff.getName().equalsIgnoreCase(s)));
                            target.setMaxGpuLengthMm(parseFirstDouble("Supported GPU length", specs));
                            target.setMaxCpuCoolerHeightMm(parseFirstDouble("Supported CPU cooler height", specs));
                        },
                        "https://www.pc-kombo.com/us/components/cases"))

                .withGPUScrape(gpu -> gpu.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setChip(find(service, target, target.getModel()));
                            target.setLengthMm(parseFirstDouble("Length", specs));
                            target.setTdp(parseFirstDouble("TDP", specs));
                            target.setVramGb(parseFirstDouble("Vram", specs));

                            target.setGpuCanonicalName(
                                    extractFirstString("gpu-chip", specs)
                                            .replace("NVIDIA", "")
                                            .replace("GeForce", "")
                                            .replace("AMD", "")
                                            .replace("Radeon", "")
                                            .replace("Intel ", "")
                                            .trim()
                            );
                        },
                        "https://www.pc-kombo.com/us/components/gpus"))

                .withCPUCoolerScrape("CPU-Cooler", cool -> cool.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setType(target.getModel().contains("Liquid") ? HardwareTypes.CoolerType.AIO_LIQUID : HardwareTypes.CoolerType.AIR);
                            if (target.getType() == HardwareTypes.CoolerType.AIO_LIQUID) {
                                if (target.getModel().contains("360")) target.setRadiatorLengthMm(360);
                                else if (target.getModel().contains("240")) target.setRadiatorLengthMm(240);
                                else if (target.getModel().contains("120")) target.setRadiatorLengthMm(120);
                            }
                            target.setSupportedSockets(extractEnumSet(HardwareTypes.CpuSocket.class, "Supported Sockets", specs,
                                    (s, sock) -> s.toUpperCase().contains(sock.name())));
                            target.setTdpWatts((int) parseFirstInt("TDP", specs));
                        },
                        "https://www.pc-kombo.com/us/components/cpucoolers"))

                .withRAMScraper("RAM", ram -> ram.addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            int sticks = Math.toIntExact(parseFirstInt("Sticks", specs));
                            target.setType(extractDdrType(extractFirstString("Ram Type", specs)));
                            target.setSizeGb(Math.toIntExact(parseFirstInt("Size", specs) / sticks));
                            target.setSpeedMtps(Math.toIntExact(parseFirstInt("Clock", specs)));
                            int[] timings = parseTimings(extractFirstString("Timings", specs));
                            target.setCasLatency(timings[0]);
                            target.setRowAddressToColumnAddressDelay(timings[1]);
                            target.setRowPrechargeTime(timings[2]);
                            target.setRowActiveTime(timings[3]);
                            target.setSticks(sticks);
                        },
                        "https://www.pc-kombo.com/us/components/rams"))

                .withStorageScraper("Storage", st -> st
                        .addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setCapacityGb((int) parseFirstInt("Size", specs));
                            if (target.getStorageType() == HardwareTypes.StorageType.SSD) {
                                String ff = extractFirstString("Form Factor", specs);
                                target.setStorageInterface("M.2".equals(ff) ? HardwareTypes.StorageInterface.NVME : HardwareTypes.StorageInterface.SATA);
                            } else {
                                target.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                            }
                        })
                        .addVariant("SSD", (scraped, s) -> s.setStorageType(HardwareTypes.StorageType.SSD),
                                "https://www.pc-kombo.com/us/components/ssds")
                        .addVariant("HDD", (scraped, h) -> {
                            h.setStorageType(HardwareTypes.StorageType.HDD);
                            h.setStorageInterface(HardwareTypes.StorageInterface.SATA);
                            // pc-kombo Size in TB → *1000 ist im Original bereits geschehen; hier setzen wir nur Interface
                        }, "https://www.pc-kombo.com/us/components/hdds")
                );
    }


    public static HardwareTypes.RamType extractDdrType(String text) {
        for (HardwareTypes.RamType value : HardwareTypes.RamType.values()) {
            if (text.toLowerCase().contains(value.name().toLowerCase())) {
                return value;
            }
        }
        return HardwareTypes.RamType.DDR4;
    }

    public static int[] parseTimings(String timings) {
        try {
            if (timings == null || timings.isEmpty()) return new int[4];
            String[] parts = timings.replace("--", "-").replace("-2N", "").replace("CL", "").split("-");

            int[] result = new int[4];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Integer.parseInt(parts[i].trim().replace("2N", ""));
            }
            return result;
        } catch (Exception e) {
            ScrapingService.LOGGER.log(Level.FINE, "Failed to parse timings " + timings, e.getMessage());
            return new int[4];
        }
    }

    private static int parseIntSafely(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
