package de.verdox.openhardwareapi.io.api;

import com.google.common.net.InternetDomainName;
import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.component.service.ScrapingService;
import de.verdox.openhardwareapi.model.GPU;
import de.verdox.openhardwareapi.model.GPUChip;
import de.verdox.openhardwareapi.model.HardwareSpec;
import de.verdox.openhardwareapi.util.GpuRegexParser;
import org.jsoup.nodes.Document;

import java.net.URI;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.logging.Level;
import java.util.stream.Stream;

public interface ComponentWebScraper<HARDWARE extends HardwareSpec> {

    record ScrapedSpecPage(String url, Document page){}
    record ScrapedSpecs(String url, Map<String, List<String>> specs){}

    WebsiteScrapingStrategy getWebsiteScrapingStrategy();

    void setWebsiteScrapingStrategy(WebsiteScrapingStrategy strategy);

    //TODO: Step 1
    Stream<ScrapedSpecPage> downloadWebsites() throws Throwable;

    //TODO: Step 2
    ScrapedSpecs extract(ScrapedSpecPage scrapedPage) throws Throwable;

    //TODO: Step 3
    Optional<HARDWARE> parse(ScrapedSpecs scrapedSpecs, ScrapeListener<HARDWARE> onScrape) throws Throwable;

    int getAmountTasks();

    interface ScrapeListener<HARDWARE extends HardwareSpec> {
        void onScrape(HARDWARE scrapedHardware);
    }

    static boolean parseBoolean(String key, Map<String, List<String>> map) {
        List<String> values = map.getOrDefault(key, new ArrayList<>());
        if (values.isEmpty()) return false;
        String raw = values.getFirst();
        return Boolean.parseBoolean(raw);
    }

    static long parseFirstInt(String key, Map<String, List<String>> map) {
        List<String> values = map.getOrDefault(key, new ArrayList<>());
        if (values.isEmpty()) return 0;

        String raw = values.getFirst();
        String cleaned = raw.replaceAll("[^0-9-]", "");

        if (cleaned.isBlank()) return 0;
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static double parseFirstDouble(String key, Map<String, List<String>> map) {
        List<String> values = map.getOrDefault(key, new ArrayList<>());
        if (values.isEmpty()) return 0;

        String raw = values.getFirst();
        String cleaned = raw.replaceAll("[^0-9,.-]", "").replace(",", ".");

        if (cleaned.isBlank()) return 0;
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String extractFirstString(String key, Map<String, List<String>> map) {
        List<String> values = map.getOrDefault(key, new ArrayList<>());
        if (values == null || values.isEmpty()) return "";
        return values.getFirst();
    }

    static <ENUM extends Enum<ENUM>> Set<ENUM> extractEnumSet(Class<ENUM> enumClass, String key, Map<String, List<String>> map, BiPredicate<String, ENUM> isEqual) {
        Set<ENUM> set = new HashSet<>();
        map.getOrDefault(key, new ArrayList<>()).forEach(s -> Arrays.stream(enumClass.getEnumConstants()).filter(anEnum -> isEqual.test(s, anEnum)).forEach(set::add));
        return set;
    }

    static <ENUM extends Enum<ENUM>> ENUM extractFirstEnum(Class<ENUM> enumClass, String key, Map<String, List<String>> map, BiPredicate<String, ENUM> isEqual) {
        return map.getOrDefault(key, List.of()).stream().map(String::trim).flatMap(s -> Arrays.stream(enumClass.getEnumConstants()).filter(e -> isEqual.test(s, e))).findFirst().orElse(enumClass.getEnumConstants()[0]);
    }

    static GPUChip find(HardwareSpecService hardwareSpecService, GPU target, String gpuName) {
        GpuRegexParser.ParsedGpu parsedGpu = GpuRegexParser.parse(gpuName).orElse(null);
        if (parsedGpu != null) {
            GPUChip gpu;
            if (target.getVramType() != null && target.getVramGb() > 0) {
                gpu = hardwareSpecService.findGPUModel(parsedGpu, target.getVramType(), target.getVramGb()).orElse(null);
            } else if (target.getVramType() != null) {
                gpu = hardwareSpecService.findGPUModel(parsedGpu, target.getVramType()).orElse(null);
            } else {
                gpu = hardwareSpecService.findGPUModel(parsedGpu).orElse(null);
            }

            if (gpu != null) {
                target.setChip(gpu);

                if (target.getPcieVersion() == null) {
                    target.setPcieVersion(gpu.getPcieVersion());
                }

                if (target.getVramType() == null) {
                    target.setVramType(gpu.getVramType());
                }

                if (target.getVramGb() == 0) {
                    target.setVramGb(gpu.getVramGb());
                }

                if (target.getLengthMm() == 0) {
                    target.setLengthMm(gpu.getLengthMm());
                }

                if (target.getTdp() == 0) {
                    target.setTdp(gpu.getTdp());
                }

                if (target.getLaunchDate() == null) {
                    target.setLaunchDate(gpu.getLaunchDate());
                }

            } else {
                ScrapingService.LOGGER.log(Level.FINER, "GPU not in database: " + gpuName + " -> " + parsedGpu.canonical());
            }
        } else {
            ScrapingService.LOGGER.log(Level.FINER, "No canonical name found: " + gpuName);
        }
        return null;
    }

    static String topLevelHost(String url) {
        try {
            return toTopLevel(URI.create(url).getHost());
        } catch (Exception e) {
            return null;
        }
    }

    static String toTopLevel(String host) {
        if (host == null) return null;
        try {
            var idn = InternetDomainName.from(host);
            if (idn.isUnderPublicSuffix() || idn.hasPublicSuffix()) return idn.topPrivateDomain().toString();
        } catch (Exception ignored) {
        }
        String[] parts = host.split("\\.");
        if (parts.length >= 2) return parts[parts.length - 2] + "." + parts[parts.length - 1];
        return host;
    }
}
