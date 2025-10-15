package de.verdox.openhardwareapi.io.gpu;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.io.api.ComponentWebScraper;
import de.verdox.openhardwareapi.io.api.WebsiteScrapingStrategy;
import de.verdox.openhardwareapi.model.GPUChip;
import de.verdox.openhardwareapi.model.HardwareTypes;
import de.verdox.openhardwareapi.util.GpuRegexParser;
import lombok.Getter;
import lombok.Setter;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class DPGPUScraper implements ComponentWebScraper<GPUChip> {

    private static final Logger log = Logger.getLogger(DPGPUScraper.class.getName());
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/painebenjamin/dbgpu/releases/latest";
    @Getter @Setter
    private WebsiteScrapingStrategy websiteScrapingStrategy;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper om = new ObjectMapper();

    public DPGPUScraper(HardwareSpecService hardwareSpecService) {
    }

    public Set<GPUChip> scrape(Set<Document> pages, ScrapeListener<GPUChip> scrapeListener) throws Throwable {
        URI jsonAsset = resolveLatestJsonAssetUrl();

        // 2) Download
        String json = download(jsonAsset);

        // 3) Parse
        List<Map<String, Object>> rows = parseJsonList(json);

        // 4) Mapping
        LinkedHashSet<GPUChip> result = new LinkedHashSet<>();
        int total = rows.size();
        int idx = 0;

        for (Map<String, Object> r : rows) {
            try {
                GPUChip gpu = mapRowToGpu(r);
                if (gpu != null) {
                    result.add(gpu);
                    scrapeListener.onScrape(gpu);
                }
            } catch (Exception ex) {
                log.log(Level.WARNING, "Mapping error at line " + idx + ": " + ex.getMessage(), ex);
            }
            idx++;
        }
        return result;
    }

    @Override
    public Stream<ScrapedSpecPage> downloadWebsites() throws Throwable {
        return Stream.empty();
    }

    @Override
    public ScrapedSpecs extract(ScrapedSpecPage scrapedPage) throws Throwable {
        return null;
    }

    @Override
    public Optional<GPUChip> parse(ScrapedSpecs scrapedSpecs, ScrapeListener<GPUChip> onScrape) throws Throwable {
        return Optional.empty();
    }

    @Override
    public int getAmountTasks() {
        return 5;
    }

    /* ============================ Core Steps ============================ */

    private URI resolveLatestJsonAssetUrl() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_API)).header("Accept", "application/vnd.github+json").header("User-Agent", "pc-lager-software/1.0").build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("GitHub API error: " + resp.statusCode());
            }

            JsonNode root = om.readTree(resp.body());
            JsonNode assets = root.path("assets");
            if (!assets.isArray() || assets.isEmpty()) {
                throw new IllegalStateException("No asset found in release");
            }

            // Heuristik: größtes .json Asset = vollständige DB
            JsonNode best = StreamSupport.stream(assets.spliterator(), false).filter(a -> a.path("name").asText("").toLowerCase(Locale.ROOT).endsWith(".json")).max(Comparator.comparingLong(a -> a.path("size").asLong(0))).orElseThrow(() -> new IllegalStateException("Kein JSON-Asset im Release"));

            String url = best.path("browser_download_url").asText(null);
            if (url == null) throw new IllegalStateException("browser_download_url not found");
            return URI.create(url);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Could not find latest json asset", e);
        }
    }

    private String download(URI url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(url).header("User-Agent", "pc-lager-software/1.0").header("Accept", "application/octet-stream") // <- hilft bei Asset-Redirects
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("Download error: " + resp.statusCode());
            }
            return resp.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Download error: " + url, e);
        }
    }

    private List<Map<String, Object>> parseJsonList(String json) {
        try {
            // Erwartet: Array von Objekten (ein Objekt pro GPU)
            return om.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("JSON-Parsing fehlgeschlagen", e);
        }
    }

    /* ============================ Mapping ============================ */

    @SuppressWarnings("unchecked")
    private GPUChip mapRowToGpu(Map<String, Object> r) {
        // Häufige Keys im dbgpu-Datensatz (robust durch Alternativen)
        String manufacturer = str(r, "manufacturer", "vendor", "brand");
        String model = str(r, "name", "product_name", "model", "full_name");
        String busInterface = str(r, "bus_interface", "bus", "pcie");
        String memoryType = str(r, "memory_type", "vram_type");
        Double memoryGb = doubleLike(r, "memory_size_gb", "memory_gb", "vram_gb");
        Integer lengthMm = intLike(r, "board_length_mm", "length_mm", "length");
        String power = str(r, "power_connectors", "power_connector", "connectors");
        String release = str(r, "release_date", "launch", "launched");

        if (manufacturer == null && model == null) {
            // unbrauchbarer Datensatz
            return null;
        }

        GPUChip gpu = new GPUChip();
        GpuRegexParser.parse(model).ifPresent(parsedGpu -> gpu.setCanonicalModel(parsedGpu.canonical()));

        // Grunddaten
        gpu.setManufacturer(safe(manufacturer));
        gpu.setModel(safe(model));
        gpu.setLaunchDate(parseDateFlexible(release)); // kann null sein

        // PCIe
        gpu.setPcieVersion(parsePcieVersion(busInterface));

        // VRAM
        gpu.setVramType(parseVramType(memoryType));
        gpu.setVramGb(memoryGb);

        // Maße
        gpu.setLengthMm(lengthMm != null ? lengthMm : 0);

        // Power-Connectoren
        //gpu.setPowerConnectors(parsePowerConnectors(power));

        // Optional: Tags/Attributes aus Restfeldern (falls HardwareSpec das hat)
        // Alles, was nicht gemappt wurde, in attributes ablegen

        // Einfache Tag-Heuristik
        Set<String> tags = new LinkedHashSet<>();
        if (busInterface != null) tags.add(busInterface);
        if (memoryType != null) tags.add(memoryType);
        gpu.setTags(tags);

        return gpu;
    }

    /* ============================ Helpers ============================ */

    private String str(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
        }
        return null;
    }

    private Integer intLike(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v == null) continue;
            try {
                if (v instanceof Number) return ((Number) v).intValue();
                String s = String.valueOf(v).replaceAll("[^0-9.\\-]", "");
                if (s.isEmpty()) continue;
                // Manche Werte sind float (z.B. "24.0")
                double d = Double.parseDouble(s);
                return (int) d;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Double doubleLike(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v == null) continue;
            try {
                if (v instanceof Number) return ((Number) v).doubleValue();
                String s = String.valueOf(v);
                if (s.isEmpty()) continue;
                // Manche Werte sind float (z.B. "24.0")
                return Double.valueOf(s);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean isAny(String key, String... options) {
        for (String o : options) if (o.equalsIgnoreCase(key)) return true;
        return false;
    }

    private String safe(String s) {
        return s == null ? null : s.trim();
    }

    private LocalDate parseDateFlexible(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        // Versuche diverse Formate: "2022-09-20", "Sep 2022", "2022", "2022-09", "September 2022"
        String[] patterns = {"yyyy-MM-dd", "yyyy-MM", "yyyy", "MMM yyyy", "MMMM yyyy", "dd MMM yyyy", "dd MMMM yyyy"};
        for (String p : patterns) {
            try {
                DateTimeFormatter f = DateTimeFormatter.ofPattern(p, Locale.ENGLISH);
                return LocalDate.parse(s, f);
            } catch (Exception ignored) {
            }
        }
        // Letzter Versuch: nur Jahr extrahieren
        Matcher m = Pattern.compile("(\\d{4})").matcher(s);
        if (m.find()) {
            try {
                return LocalDate.of(Integer.parseInt(m.group(1)), 1, 1);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private HardwareTypes.PcieVersion parsePcieVersion(String busInterface) {
        if (busInterface == null) return null;
        String s = busInterface.toLowerCase(Locale.ROOT);
        if (s.contains("6")) return HardwareTypes.PcieVersion.GEN6;
        if (s.contains("5")) return HardwareTypes.PcieVersion.GEN5;
        if (s.contains("4")) return HardwareTypes.PcieVersion.GEN4;
        if (s.contains("3")) return HardwareTypes.PcieVersion.GEN3;
        if (s.contains("2")) return HardwareTypes.PcieVersion.GEN2;
        if (s.contains("1")) return HardwareTypes.PcieVersion.GEN1;
        return null;
    }

    private HardwareTypes.VRAM_TYPE parseVramType(String memoryType) {
        if (memoryType == null) return HardwareTypes.VRAM_TYPE.UNKNOWN;
        String s = memoryType.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (s.contains("GDDR6X")) return HardwareTypes.VRAM_TYPE.GDDR6X;
        if (s.contains("GDDR6")) return HardwareTypes.VRAM_TYPE.GDDR6;
        if (s.contains("GDDR5X")) return HardwareTypes.VRAM_TYPE.GDDR5X;
        if (s.contains("GDDR5")) return HardwareTypes.VRAM_TYPE.GDDR5;
        if (s.contains("GDDR4")) return HardwareTypes.VRAM_TYPE.GDDR4;
        if (s.contains("GDDR3")) return HardwareTypes.VRAM_TYPE.GDDR3;
        if (s.contains("HBM3")) return HardwareTypes.VRAM_TYPE.HBM3;
        if (s.contains("HBM2E")) return HardwareTypes.VRAM_TYPE.HBM2E;
        if (s.contains("HBM2")) return HardwareTypes.VRAM_TYPE.HBM2;
        if (s.contains("HBM")) return HardwareTypes.VRAM_TYPE.HBM1;
        if (s.contains("LPDDR5")) return HardwareTypes.VRAM_TYPE.LPDDR5;
        if (s.contains("DDR5")) return HardwareTypes.VRAM_TYPE.DDR5;
        if (s.contains("DDR4")) return HardwareTypes.VRAM_TYPE.DDR4;
        return HardwareTypes.VRAM_TYPE.UNKNOWN;
    }
}

