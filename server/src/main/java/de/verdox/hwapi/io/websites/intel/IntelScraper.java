package de.verdox.hwapi.io.websites.intel;

import de.verdox.hwapi.hardwareapi.component.service.HardwareSpecService;
import de.verdox.hwapi.io.api.WebsiteScraper;
import de.verdox.hwapi.io.parser.ScrapeParser;
import de.verdox.hwapi.model.CPU;
import de.verdox.hwapi.model.HardwareTypes;

import java.util.Set;

public class IntelScraper {
    private static final String INTEL_CORE_ULTRA_PROCESSORS = "PanelLabel236800";
    private static final String INTEL_PROCESSORS = "PanelLabel231726";
    private static final String INTEL_ATOM_PROCESSORS = "PanelLabel29035";
    private static final String INTEL_CELERON_PROCESSORS = "PanelLabel43521";
    private static final String INTEL_CORE_PROCESSORS = "PanelLabel122139";
    private static final String INTEL_XEON_PROCESSORS = "PanelLabel595";
    private static final String INTEL_PENTIUM_PROCESSORS = "PanelLabel29862";

    public static WebsiteScraper create(HardwareSpecService service) {
        return new WebsiteScraper(service, "intel.com")
                .withStrategy(new IntelScrapingStrategy())
                .withCPUScrape(cpu -> cpu.addMainScrapeLogic((scraped, target) -> {
                            new ScrapeParser<CPU>(scraped.specs())
                                    .parseString("Processor Number", CPU::getModel, CPU::setModel)

                                    .parseString("Code Name", CPU::getCodeName, (cpu1, s) -> cpu1.setCodeName(s.replace("Products formerly ", "")))

                                    .parseNumber("Total Cores", Integer::parseInt, CPU::getCores, CPU::setCores, 0)
                                    .parseNumber("Total Threads", Integer::parseInt, CPU::getCores, CPU::setCores, 0)

                                    .parseNumber("# of Performance-cores", Integer::parseInt, CPU::getPerformanceCores, CPU::setPerformanceCores, 0)
                                    .parseNumber("# of Efficient-cores", Integer::parseInt, CPU::getEfficiencyCores, CPU::setEfficiencyCores, 0)

                                    .parseNumber("Processor Base Frequency", s -> Double.parseDouble(s) * 1000, CPU::getBaseClockMhz, CPU::setBaseClockMhz, 0d)
                                    .parseNumber("Max Turbo Frequency", s -> Double.parseDouble(s) * 1000, CPU::getBoostClockMhz, CPU::setBoostClockMhz, 0d)

                                    .parseNumber("Cache", Integer::parseInt, CPU::getL3CacheMb, CPU::setL3CacheMb, 0)
                                    .parseNumber("TDP", Integer::parseInt, CPU::getTdpWatts, CPU::setTdpWatts, 0)
                                    .parseNumber("Maximum Turbo Power", Integer::parseInt, CPU::getTdpWatts, CPU::setTdpWatts, 0)

                                    .parseString("GPU Name‡", CPU::getIntegratedGraphics, CPU::setIntegratedGraphics)


                                    .parseEnum("Sockets Supported", CPU::getSocket, CPU::setSocket,
                                            (s, sock) -> s.toUpperCase().contains(sock.name().toUpperCase()) || sock.name().toUpperCase().contains(s.toUpperCase()),
                                            HardwareTypes.CpuSocket.UNKNOWN)

                                    .parse(target);

                            target.setManufacturer("Intel");
                            if (scraped.specs().get("Ordering Code") != null && !scraped.specs().get("Ordering Code").isEmpty()) {
                                target.setMPNs(Set.copyOf(scraped.specs().get("Ordering Code")));
                            }
                        },
                        "https://www.intel.com/content/www/us/en/ark.html#@" + INTEL_CORE_ULTRA_PROCESSORS,
                        "https://www.intel.com/content/www/us/en/ark.html#@" + INTEL_PROCESSORS,
                        "https://www.intel.com/content/www/us/en/ark.html#@" + INTEL_ATOM_PROCESSORS,
                        "https://www.intel.com/content/www/us/en/ark.html#@" + INTEL_CELERON_PROCESSORS,
                        "https://www.intel.com/content/www/us/en/ark.html#@" + INTEL_CORE_PROCESSORS,
                        "https://www.intel.com/content/www/us/en/ark.html#@" + INTEL_XEON_PROCESSORS,
                        "https://www.intel.com/content/www/us/en/ark.html#@" + INTEL_PENTIUM_PROCESSORS
                ));
    }
}
