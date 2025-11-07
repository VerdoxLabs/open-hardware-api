package de.verdox.openhardwareapi.io.websites.pc_builder_io;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.io.api.WebsiteScraper;
import de.verdox.openhardwareapi.model.HardwareTypes;
import de.verdox.openhardwareapi.model.PCCase;
import de.verdox.openhardwareapi.model.values.DimensionsMm;
import org.jsoup.nodes.Document;

import java.util.function.BiPredicate;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;
import static de.verdox.openhardwareapi.io.websites.pc_kombo.PCKomboScrapers.parseTimings;

public class PCBuilderIOScrapers {

    public static final BiPredicate<String, Document> CHALLENGE_PREDICATE = (url, document) -> document.selectFirst("div.security-content") != null;
    public static final BiPredicate<String, Document> SHOULD_SAVE = (url, document) -> {
        return true;
    };

    public static WebsiteScraper create(HardwareSpecService service) {
        return new WebsiteScraper(service, "pc-builder.io")
                .withStrategy(new PCBuilderIOStrategy())
                .withChallengePageDetection(CHALLENGE_PREDICATE)
                .withShouldSavePredicate(SHOULD_SAVE)

                .withCPUScrape(cpu -> cpu
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();
                                    target.setCores((int) parseFirstInt("Core Count", specs));
                                    target.setBaseClockMhz(parseFirstDouble("Core Clock", specs));
                                    target.setBoostClockMhz(parseFirstDouble("Boost Clock", specs));
                                    target.setTdpWatts((int) parseFirstInt("TDP", specs));
                                    target.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "Socket", specs, (s, cpuSocket) -> s.contains(cpuSocket.name())));
                                    target.setIntegratedGraphics(extractFirstString("Integrated Graphics", specs));
                                    if (parseBoolean("Simultaneous Multithreading", specs)) {
                                        target.setThreads(target.getCores() * 2);
                                    }
                                },
                                "https://de.pc-builder.io/product-list/cpu"
                        )
                )

                .withMotherboardScrape(mb -> mb
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();
                                    target.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "Socket / CPU", specs, (s, type) -> s.contains(type.name())));
                                    target.setFormFactor(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Form Factor", specs, (s, type) -> s.contains(type.name())));
                                    target.setChipset(extractFirstEnum(HardwareTypes.Chipset.class, "Chipset", specs, (s, type) -> s.contains(type.name())));
                                    target.setRamCapacity((int) parseFirstInt("Memory Max", specs));
                                    target.setRamType(extractFirstEnum(HardwareTypes.RamType.class, "Memory Type", specs, (s, type) -> s.contains(type.name())));
                                    target.setRamSlots((int) parseFirstInt("Memory Slots", specs));
                                },
                                "https://de.pc-builder.io/product-list/motherboard"
                        )
                )

                .withPSUScraper(psu -> psu
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();

                                    target.setWattage((int) parseFirstInt("Wattage", specs));
                                    target.setSize(extractFirstEnum(HardwareTypes.PSUFormFactor.class, "Type", specs, (s, type) -> s.contains(type.name())));
                                    target.setEfficiencyRating(extractFirstEnum(HardwareTypes.PsuEfficiencyRating.class, "Efficiency Rating", specs, (s, type) -> s.toUpperCase().contains(type.name())));

                                    String modularity = extractFirstString("Modular", specs);
                                    if (modularity.equals("Full")) {
                                        target.setModularity(HardwareTypes.PSU_MODULARITY.FULL_MODULAR);
                                    } else if (modularity.equals("No")) {
                                        target.setModularity(HardwareTypes.PSU_MODULARITY.NON_MODULAR);
                                    } else {
                                        target.setModularity(HardwareTypes.PSU_MODULARITY.SEMI_MODULAR);
                                    }
                                },
                                "https://de.pc-builder.io/product-list/power-supply"
                        )
                )

                .withPCCaseScraper(cs -> cs
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();

                                    DimensionsMm d = new DimensionsMm();
                                    String[] dims = extractFirstString("Dimensions", specs).replace("mm", "").split(" x ");
                                    if (dims.length == 3) {
                                        d.setDepth(Double.parseDouble(dims[0]));
                                        d.setWidth(Double.parseDouble(dims[1]));
                                        d.setHeight(Double.parseDouble(dims[2]));
                                    }
                                    target.setDimensions(d);
                                    target.setSizeClass(PCCase.classify(d));

                                    target.setMotherboardSupport(
                                            extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "Motherboard Form Factor", specs,
                                                    (s, ff) -> s.contains(ff.name()))
                                    );
                                },
                                "https://de.pc-builder.io/product-list/case"
                        )
                )

                .withGPUScrape(gpu -> gpu
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();

                                    target.setChip(find(service, target, extractFirstString("Chipset", specs)));
                                    target.setVramGb(parseFirstInt("Memory", specs));
                                    target.setVramType(extractFirstEnum(HardwareTypes.VRAM_TYPE.class, "Memory Type", specs, (s, v) -> s.contains(v.name())));
                                    target.setTdp(parseFirstInt("TDP", specs));
                                    target.setLengthMm((int) parseFirstDouble("Length", specs));

                                    target.setGpuCanonicalName(
                                            extractFirstString("Chipset", specs)
                                                    .replace("NVIDIA", "")
                                                    .replace("GeForce", "")
                                                    .replace("AMD", "")
                                                    .replace("Radeon", "")
                                                    .replace("Intel ", "").trim()
                                    );
                                },
                                "https://de.pc-builder.io/product-list/gpu"
                        )
                )

                .withCPUCoolerScrape("CPU Cooler", air -> air
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();
                                    target.setType(extractFirstString("Water Cooled", specs).contains("Yes") ? HardwareTypes.CoolerType.AIO_LIQUID : HardwareTypes.CoolerType.AIR);
                                    target.setSupportedSockets(
                                            extractEnumSet(HardwareTypes.CpuSocket.class, "CPU Socket", specs,
                                                    (s, ff) -> s.contains(ff.name()))
                                    );
                                    target.setRadiatorLengthMm(parseFirstInt("Water Cooled", specs));

                                },
                                "https://de.pc-builder.io/product-list/cpu-cooler"
                        )
                )

                .withRAMScraper("RAM", ram -> ram
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();

                                    String[] sticksAndGBPerStick = extractFirstString("Modules", specs).split(" x ");
                                    target.setSticks(Integer.parseInt(sticksAndGBPerStick[0]));
                                    target.setSizeGb(Integer.parseInt(sticksAndGBPerStick[1].replace("GB", "").trim()));

                                    String[] typeAndSpeed = extractFirstString("Speed", specs).split("-");

                                    target.setType(extractFirstEnum(HardwareTypes.RamType.class, "Form Factor", specs, (s, v) -> s.contains(v.name())));
                                    target.setSpeedMtps(Integer.parseInt(typeAndSpeed[1]));

                                    int[] timings = parseTimings(extractFirstString("Timing", specs));
                                    target.setCasLatency(timings[0]);
                                    target.setRowAddressToColumnAddressDelay(timings[1]);
                                    target.setRowPrechargeTime(timings[2]);
                                    target.setRowActiveTime(timings[3]);


                                },
                                "https://de.pc-builder.io/product-list/ram"
                        )
                )

                .withDisplayScrape("Display", display -> display
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();
                                    target.setInchSize(parseFirstDouble("Screen Size", specs));
                                    target.setRefreshRate((int) parseFirstInt("Refresh Rate", specs));
                                    target.setCurved(parseBoolean("Curved Screen", specs));
                                    target.setIntegratedSpeakers(parseBoolean("Built-in Speakers", specs));
                                    target.setResponseTimeMS(parseFirstDouble("Response Time (G2G)", specs));

                                    String[] resolution = extractFirstString("Resolution", specs).split(" x ");

                                    target.setResWidth(Integer.parseInt(resolution[0]));
                                    target.setResHeight(Integer.parseInt(resolution[1]));
                                },
                                "https://de.pc-builder.io/product-list/monitor"
                        )
                )


                .withStorageScraper("Storage", st -> st
                        .addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setStorageInterface(parseBoolean("NVME", specs) ? HardwareTypes.StorageInterface.NVME : HardwareTypes.StorageInterface.SATA);
                            target.setStorageType(extractFirstEnum(HardwareTypes.StorageType.class, "Storage", specs, (s, v) -> s.contains(v.name())));
                            String gbSize = extractFirstString("Capacity", specs).toLowerCase().replace("tb", "000").replace(" ", "").replace("gb", "").trim();
                            target.setCapacityGb(Integer.parseInt(gbSize));
                        }, "https://de.pc-builder.io/product-list/storage")
                );


    }
}

