package de.verdox.hwapi.io.websites.pc_builder_io;

import de.verdox.hwapi.hardwareapi.component.service.HardwareSpecService;
import de.verdox.hwapi.io.api.WebsiteScraper;
import de.verdox.hwapi.model.HardwareTypes;
import de.verdox.hwapi.model.PCCase;
import de.verdox.hwapi.model.values.DimensionsMm;
import org.jsoup.nodes.Document;

import java.util.function.BiPredicate;

import static de.verdox.hwapi.io.api.ComponentWebScraper.*;
import static de.verdox.hwapi.io.websites.pc_kombo.PCKomboScrapers.parseTimings;

public class PCBuilderIOScrapers {

    public static final BiPredicate<String, Document> CHALLENGE_PREDICATE = (url, document) -> document.selectFirst("div.security-content") != null;
    public static final BiPredicate<String, Document> SHOULD_SAVE = (url, document) -> {
        return document.selectFirst("span.code-label") == null;
    };

    public static WebsiteScraper create(HardwareSpecService service) {
        return new WebsiteScraper(service, "pc-builder.io")
                .withStrategy(new PCBuilderIOStrategy())
                .withChallengePageDetection(CHALLENGE_PREDICATE)
                .withShouldSavePredicate(SHOULD_SAVE)
                .withBaseLogic((scrapedSpecs, hardwareSpec) -> {
                    if(scrapedSpecs.specs().containsKey("img")) {
                        hardwareSpec.getPictureUrls().addAll(scrapedSpecs.specs().get("img"));
                    }
                })
                .withCPUScrape(cpu -> cpu
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();
                                    target.setCores((int) parseFirstInt("core count", specs));
                                    target.setBaseClockMhz(parseFirstDouble("core clock", specs) * 1000);
                                    target.setBoostClockMhz(parseFirstDouble("boost clock", specs) * 1000);
                                    target.setTdpWatts((int) parseFirstInt("tdp", specs));
                                    target.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "socket", specs, (s, cpuSocket) -> s.contains(cpuSocket.name())));
                                    target.setIntegratedGraphics(extractFirstString("integrated graphics", specs));
                                    if (extractFirstString("simultaneous multithreading", specs).contains("Yes")) {
                                        target.setThreads(target.getCores() * 2);
                                    } else {
                                        target.setThreads(target.getCores());
                                    }
                                },
                                "https://de.pc-builder.io/product-list/cpu"
                        )
                )

                .withMotherboardScrape(mb -> mb
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();
                                    target.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "socket / cpu", specs, (s, type) -> s.contains(type.name())));
                                    target.setFormFactor(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "form factor", specs, (s, type) -> s.contains(type.name())));
                                    target.setChipset(extractFirstEnum(HardwareTypes.Chipset.class, "chipset", specs, (s, type) -> s.contains(type.name())));
                                    target.setRamCapacity((int) parseFirstInt("memory max", specs));
                                    target.setRamType(extractFirstEnum(HardwareTypes.RamType.class, "memory type", specs, (s, type) -> s.contains(type.name())));
                                    target.setRamSlots((int) parseFirstInt("memory slots", specs));
                                },
                                "https://de.pc-builder.io/product-list/motherboard"
                        )
                )

                .withPSUScraper(psu -> psu
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();

                                    target.setWattage((int) parseFirstInt("wattage", specs));
                                    target.setSize(extractFirstEnum(HardwareTypes.PSUFormFactor.class, "type", specs, (s, type) -> s.contains(type.name())));
                                    target.setEfficiencyRating(extractFirstEnum(HardwareTypes.PsuEfficiencyRating.class, "efficiency rating", specs, (s, type) -> s.toUpperCase().contains(type.name())));

                                    String modularity = extractFirstString("modular", specs);
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
                                    String[] dims = extractFirstString("dimensions", specs).replace("mm", "").split("x");
                                    if (dims.length == 3) {
                                        d.setDepth(Double.parseDouble(dims[0]));
                                        d.setWidth(Double.parseDouble(dims[1]));
                                        d.setHeight(Double.parseDouble(dims[2]));
                                        target.setDimensions(d);
                                        target.setSizeClass(PCCase.classify(d));
                                    }


                                    target.setMotherboardSupport(
                                            extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "motherboard form factor", specs,
                                                    (s, ff) -> s.contains(ff.name()))
                                    );
                                },
                                "https://de.pc-builder.io/product-list/case"
                        )
                )

                .withGPUScrape(gpu -> gpu
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();

                                    target.setChip(find(service, target, extractFirstString("chipset", specs)));
                                    target.setVramGb(parseFirstInt("memory", specs));
                                    target.setVramType(extractFirstEnumPatternMatching(HardwareTypes.VRAM_TYPE.class, "memory type", specs));
                                    target.setTdp(parseFirstInt("tdp", specs));
                                    target.setLengthMm((int) parseFirstDouble("length", specs));

                                    target.setGpuCanonicalName(
                                            extractFirstString("chipset", specs)
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
                                    target.setType(extractFirstString("water cooled", specs).contains("Yes") ? HardwareTypes.CoolerType.AIO_LIQUID : HardwareTypes.CoolerType.AIR);
                                    target.setSupportedSockets(
                                            extractEnumSet(HardwareTypes.CpuSocket.class, "cpu socket", specs,
                                                    (s, ff) -> s.contains(ff.name()))
                                    );
                                    int parsed = Math.toIntExact(parseFirstInt("water cooled", specs));
                                    target.setRadiatorLengthMm(Math.max(0, parsed));
                                },
                                "https://de.pc-builder.io/product-list/cpu-cooler"
                        )
                )

                .withRAMScraper("RAM", ram -> ram
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();

                                    String[] sticksAndGBPerStick = extractFirstString("modules", specs).split("x");

                                    target.setSticks(Integer.parseInt(sticksAndGBPerStick[0].trim()));
                                    target.setSizeGb(Integer.parseInt(sticksAndGBPerStick[1].replace("GB", "").trim()));

                                    String[] typeAndSpeed = extractFirstString("speed", specs).split("-");

                                    target.setType(extractFirstEnum(HardwareTypes.RamType.class, "form factor", specs, (s, v) -> s.contains(v.name())));
                                    target.setFormFactor(extractFirstEnum(HardwareTypes.RamFormFactor.class, "form factor", specs, (s, v) -> s.contains(v.name())));
                                    target.setSpeedMtps(Integer.parseInt(typeAndSpeed[1].trim()));

                                    int[] timings = parseTimings(extractFirstString("timing", specs));
                                    target.setCasLatency(timings[0]);
                                    target.setRowAddressToColumnAddressDelay(timings[1]);
                                    target.setRowPrechargeTime(timings[2]);
                                    target.setRowActiveTime(timings[3]);

                                    if (target.getCasLatency() == 0) {
                                        target.setCasLatency((int) parseFirstInt("cas latency", specs));
                                    }

                                    target.setECC(!extractFirstString("ecc / registered", specs).contains("Non-ECC"));

                                    target.setHasHeatSpreader(parseBoolean("heat spreader", specs));
                                },
                                "https://de.pc-builder.io/product-list/ram"
                        )
                )

                .withDisplayScrape("Display", display -> display
                        .addMainScrapeLogic((scraped, target) -> {
                                    var specs = scraped.specs();
                                    target.setInchSize(parseFirstDouble("screen size", specs));
                                    target.setRefreshRate((int) parseFirstInt("refresh rate", specs));
                                    target.setCurved(parseBoolean("curved screen", specs));
                                    target.setIntegratedSpeakers(parseBoolean("built-in speakers", specs));
                                    target.setResponseTimeMS(parseFirstDouble("response time (G2G)", specs));

                                    String[] resolution = extractFirstString("resolution", specs).split("x");

                                    target.setResWidth(Integer.parseInt(resolution[0].trim()));
                                    target.setResHeight(Integer.parseInt(resolution[1].trim()));
                                },
                                "https://de.pc-builder.io/product-list/monitor"
                        )
                )


                .withStorageScraper("Storage", st -> st
                        .addMainScrapeLogic((scraped, target) -> {
                            var specs = scraped.specs();
                            target.setStorageInterface(parseBoolean("nvme", specs) ? HardwareTypes.StorageInterface.NVME : HardwareTypes.StorageInterface.SATA);
                            target.setStorageType(extractFirstEnum(HardwareTypes.StorageType.class, "storage", specs, (s, v) -> s.contains(v.name())));
                            String gbSize = extractFirstString("capacity", specs).toLowerCase().replace("tb", "000").replace(" ", "").replace("gb", "").trim();
                            target.setCapacityGb((int) Double.parseDouble(gbSize.trim()));
                        }, "https://de.pc-builder.io/product-list/storage")
                );


    }
}

