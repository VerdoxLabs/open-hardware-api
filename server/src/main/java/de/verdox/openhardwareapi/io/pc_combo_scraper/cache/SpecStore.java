package de.verdox.openhardwareapi.io.pc_combo_scraper.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.verdox.openhardwareapi.component.service.ScrapingService;
import de.verdox.openhardwareapi.io.pc_combo_scraper.AbstractPCKomboScraper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;

public class SpecStore {

    public record Meta(
            String prefix,           // z.B. "Cases", "GPUs"
            String url,              // Detail-URL
            String urlHash,          // SHA-256(url)
            String name,             // Anzeigename (z.B. Listenname)
            String model,            // erkannter Model-String (falls verfügbar)
            String manufacturer,     // erkannter Hersteller (falls verfügbar)
            String ean,              // erkannte EAN (falls verfügbar)
            long savedAtEpochMs,     // Zeitstempel
            String parserVersion     // Version des Parsers
    ) {
    }

    public record StoredSpec(
            Map<String, List<String>> map,
            List<AbstractPCKomboScraper.SpecEntry> list,
            String rawHtml, // optional: gz weglassen, wenn du es nicht brauchst
            Meta meta
    ) {
    }

    private final Path root;
    private final ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final String parserVersion;

    public SpecStore(Path root, String parserVersion) {
        this.root = root;
        this.parserVersion = parserVersion;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String safe(String s) {
        if (s == null) return "";
        s = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return s.isBlank() ? "unknown" : s;
    }

    /**
     * Ordner: specs/{prefix}/{hash}--{hint}/
     */
    private Path dir(String prefix, String url, String hint) throws IOException {
        String hash = sha256(url);
        Path d = root.resolve("specs")
                .resolve(prefix)
                .resolve(hash + "--" + safe(hint));
        Files.createDirectories(d);
        return d;
    }

    public Optional<StoredSpec> readByUrl(String prefix, String url) {
        try {
            // Wir kennen den genauen Ordnernamen (mit hint) nicht → suche nach hash--*
            String hash = sha256(url);
            Path pfx = root.resolve("specs").resolve(prefix);
            if (!Files.exists(pfx)) return Optional.empty();
            try (var stream = Files.list(pfx)) {
                Optional<Path> folder = stream
                        .filter(p -> p.getFileName().toString().startsWith(hash + "--"))
                        .findFirst();
                if (folder.isEmpty()) return Optional.empty();

                Path d = folder.get();
                Path mapJson = d.resolve("specs.map.json");
                Path listJson = d.resolve("specs.list.json");
                Path metaJson = d.resolve("meta.json");
                Path htmlFile = d.resolve("raw.html"); // nur wenn gespeichert

                if (!Files.exists(mapJson) || !Files.exists(listJson) || !Files.exists(metaJson))
                    return Optional.empty();

                Map<String, List<String>> map = om.readValue(Files.readString(mapJson), new TypeReference<>() {
                });
                List<AbstractPCKomboScraper.SpecEntry> list = om.readValue(Files.readString(listJson), new TypeReference<>() {
                });
                Meta meta = om.readValue(Files.readString(metaJson), Meta.class);
                String html = Files.exists(htmlFile) ? Files.readString(htmlFile) : null;

                return Optional.of(new StoredSpec(map, list, html, meta));
            }
        } catch (Exception ignore) {
            ScrapingService.LOGGER.log(Level.SEVERE, "Error while searching for url in spec store.", ignore);
        }
        return Optional.empty();
    }

    public boolean isFresh(StoredSpec s, Duration ttl) {
        return Instant.ofEpochMilli(s.meta.savedAtEpochMs()).plus(ttl).isAfter(Instant.now());
    }

    // in SpecStore
    public List<Meta> listMetaByPrefix(String prefix) {
        try {
            Path pfx = root.resolve("specs").resolve(prefix);
            if (!Files.exists(pfx)) return List.of();

            try (var stream = Files.list(pfx)) {
                return stream
                        .filter(Files::isDirectory)
                        .map(dir -> dir.resolve("meta.json"))
                        .filter(Files::exists)
                        .map(p -> {
                            try {
                                return om.readValue(Files.readString(p), Meta.class);
                            } catch (IOException e) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        // jüngste zuerst
                        .sorted(Comparator.comparingLong(Meta::savedAtEpochMs).reversed())
                        .toList();
            }
        } catch (IOException e) {
            ScrapingService.LOGGER.log(Level.SEVERE, "Could not list meta by prefix.", e);
            return List.of();
        }
    }

    public void writeByUrl(String prefix, String url,
                           String displayName,
                           String manufacturer,
                           String model,
                           String ean,
                           Map<String, List<String>> map,
                           List<AbstractPCKomboScraper.SpecEntry> list,
                           String rawHtml // optional; null, wenn du kein HTML behalten willst
    ) {
        try {
            String hint = (manufacturer == null || manufacturer.isBlank() ? "" : manufacturer + "-") + (model == null || model.isBlank() ? displayName : model);
            Path d = dir(prefix, url, hint);

            Files.writeString(d.resolve("specs.map.json"), om.writeValueAsString(map), StandardCharsets.UTF_8);
            Files.writeString(d.resolve("specs.list.json"), om.writeValueAsString(list), StandardCharsets.UTF_8);

            Meta meta = new Meta(
                    prefix, url, sha256(url), displayName,
                    model == null ? "" : model,
                    manufacturer == null ? "" : manufacturer,
                    (ean == null ? "" : ean),
                    System.currentTimeMillis(),
                    parserVersion
            );
            Files.writeString(d.resolve("meta.json"), om.writeValueAsString(meta), StandardCharsets.UTF_8);

            if (rawHtml != null) {
                Files.writeString(d.resolve("raw.html"), rawHtml, StandardCharsets.UTF_8);
            }
            ScrapingService.LOGGER.log(Level.FINE, "["+prefix+"] cached the page "+url+" to "+ d.toAbsolutePath());
        } catch (IOException e) {
            ScrapingService.LOGGER.log(Level.SEVERE, "Error caching specs page for " + model + " [" + url + "]", e);
        }
    }
}
