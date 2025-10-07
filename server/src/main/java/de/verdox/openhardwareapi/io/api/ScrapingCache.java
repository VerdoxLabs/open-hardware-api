package de.verdox.openhardwareapi.io.api;

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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ScrapingCache {
    private static final Logger LOGGER = Logger.getLogger(ScrapingCache.class.getName());

    public record Meta(
            String url,
            String urlHash,
            long savedAtEpochMs,
            String note // optional, frei nutzbar
    ) {
    }

    public record CachedPage(
            String rawHtml,
            Meta meta
    ) {
    }

    private final Path root;
    private final ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * @param root Basisordner, unter dem der Cache abgelegt wird (z.B. data/)
     */
    public ScrapingCache(Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Path dirForUrl(String url) throws IOException {
        String hash = sha256(url);
        Path d = root.resolve("scrape-cache").resolve(hash);
        Files.createDirectories(d);
        return d;
    }

    /**
     * Liest Cacheeintrag (falls vorhanden), egal ob „frisch“.
     */
    public Optional<CachedPage> read(String url) {
        try {
            Path d = dirForUrl(url);
            Path metaJson = d.resolve("meta.json");
            Path htmlFile = d.resolve("raw.html");
            if (!Files.exists(metaJson) || !Files.exists(htmlFile)) return Optional.empty();

            Meta meta = om.readValue(Files.readString(metaJson), Meta.class);
            String html = Files.readString(htmlFile);
            return Optional.of(new CachedPage(html, meta));
        } catch (Exception ignore) {
            return Optional.empty();
        }
    }

    /**
     * true, wenn savedAt + ttl noch in der Zukunft liegt.
     */
    public boolean isFresh(CachedPage page, Duration ttl) {
        if (page == null) return false;
        return Instant.ofEpochMilli(page.meta.savedAtEpochMs())
                .plus(ttl)
                .isAfter(Instant.now());
    }

    /**
     * Schreibt/überschreibt Cacheeintrag für URL.
     */
    public void write(String url, String rawHtml, String note) {
        try {
            Path d = dirForUrl(url);
            Files.writeString(d.resolve("raw.html"), rawHtml == null ? "" : rawHtml, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Meta meta = new Meta(url, sha256(url), System.currentTimeMillis(), note == null ? "" : note);
            Files.writeString(d.resolve("meta.json"), om.writeValueAsString(meta), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            LOGGER.log(Level.INFO, "Cached " + url + " [" + sha256(url) + "]");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not cache " + url + " [" + sha256(url) + "]", e);
            // bewusst „best effort“ – Scrape soll nicht daran scheitern
        }
    }

    /**
     * Liefert den Cache-Ordner für die URL, ohne ihn anzulegen.
     */
    private Path dirForUrlNoCreate(String url) {
        String hash = sha256(url);
        return root.resolve("scrape-cache").resolve(hash);
    }

    /**
     * Löscht alle gecachten Daten zu einer URL (Ordner rekursiv).
     *
     * @return true, wenn etwas gelöscht wurde; false, wenn kein Eintrag existierte oder beim Löschen ein Fehler auftrat.
     */
    public boolean delete(String url) {
        Path d = dirForUrlNoCreate(Objects.requireNonNull(url, "url"));
        if (!Files.exists(d)) {
            return false; // nichts zu löschen
        }
        try (var walk = Files.walk(d)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            LOGGER.log(Level.INFO, "Deleted cache for " + url + " [" + sha256(url) + "]");
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not delete cache for " + url + " [" + sha256(url) + "]", e);
            return false;
        }
    }

    /**
     * Convenience: Wenn frisch im Cache → zurückgeben; sonst via fetcher holen und speichern.
     *
     * @param fetcher Liefert Raw-HTML (z.B. von Selenium) – sollte keine Exceptions werfen oder sie in Runtime verpacken.
     */
    public CachedPage fetchOrCache(String url, Duration ttl, java.util.function.Supplier<String> fetcher) {
        var cached = read(url);
        if (cached.isPresent() && isFresh(cached.get(), ttl)) {
            return cached.get();
        }
        String html = fetcher.get();
        if (html == null) html = "";
        write(url, html, "fetched");
        return new CachedPage(html, new Meta(url, sha256(url), System.currentTimeMillis(), "fetched"));
    }
}
