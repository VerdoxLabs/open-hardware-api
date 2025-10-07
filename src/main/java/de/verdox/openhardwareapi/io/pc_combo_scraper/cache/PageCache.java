package de.verdox.openhardwareapi.io.pc_combo_scraper.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class PageCache {
    public record Meta(
            String url,
            String host,
            String pathHash,
            int status,
            String etag,
            String lastModified,
            long savedAtEpochMs
    ) {}

    private final Path root;
    private final ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public PageCache(Path root) { this.root = root; }

    public Path siteDir(String host) throws IOException {
        Path dir = root.resolve("cache/sites").resolve(host);
        Files.createDirectories(dir);
        return dir;
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public record CachedPage(String html, Meta meta) {}

    public Optional<CachedPage> read(String url) {
        try {
            var u = new java.net.URL(url);
            String host = u.getHost();
            String hash = sha256(url);
            Path dir = siteDir(host);
            Path htmlGz = dir.resolve(hash + ".html.gz");
            Path metaJson = dir.resolve(hash + ".meta.json");
            if (!Files.exists(htmlGz) || !Files.exists(metaJson)) return Optional.empty();

            Meta meta = om.readValue(Files.readString(metaJson), Meta.class);
            try (var in = new GZIPInputStream(Files.newInputStream(htmlGz))) {
                String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return Optional.of(new CachedPage(html, meta));
            }
        } catch (Exception ignore) {}
        return Optional.empty();
    }

    public void write(String url, int status, String etag, String lastModified, String html) {
        try {
            var u = new java.net.URL(url);
            String host = u.getHost();
            String hash = sha256(url);
            Path dir = siteDir(host);
            Path htmlGz = dir.resolve(hash + ".html.gz");
            Path metaJson = dir.resolve(hash + ".meta.json");

            try (var out = new GZIPOutputStream(Files.newOutputStream(htmlGz, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                out.write(html.getBytes(StandardCharsets.UTF_8));
            }
            Meta meta = new Meta(url, host, hash, status, etag, lastModified, System.currentTimeMillis());
            Files.writeString(metaJson, om.writeValueAsString(meta), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Logging optional
        }
    }

    public static boolean isFresh(Meta meta, Duration ttl) {
        return Instant.ofEpochMilli(meta.savedAtEpochMs()).plus(ttl).isAfter(Instant.now());
    }
}
