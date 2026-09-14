package de.verdox.hwapi.catalog.ingestion.api.selenium;

import de.verdox.hwapi.catalog.ingestion.ScrapingService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class FScrapingCache implements ScrapingCache {

    /**
     * Controls:
     *  -Dscraping.cache.gzip=true|false   (default true)
     *  -Dscraping.cache.maxBytes=...      (default 5_000_000)
     *  -Dscraping.cache.dedupe=true|false (default true)
     */
    private static final boolean USE_GZIP =
            Boolean.parseBoolean(System.getProperty("scraping.cache.gzip", "true"));

    private static final boolean DEDUPE_BY_HASH =
            Boolean.parseBoolean(System.getProperty("scraping.cache.dedupe", "true"));

    private static final int MAX_BYTES =
            Integer.parseInt(System.getProperty("scraping.cache.maxBytes", "5000000"));

    @Override
    public void saveHtml(PageKey key, String html) {
        if (html == null) return;

        byte[] raw = html.getBytes(StandardCharsets.UTF_8);

        // Guardrail: avoid caching huge pages that explode IO / storage
        if (raw.length > MAX_BYTES) {
            ScrapingService.LOGGER.log(Level.FINER,
                    () -> "Skipping cache for " + key + " (html size " + raw.length + " > " + MAX_BYTES + " bytes)");
            return;
        }

        Path baseFile = ScrapingPaths.fileFor(key);
        Path file = USE_GZIP ? withGzExtension(baseFile) : baseFile;

        try {
            Files.createDirectories(file.getParent());

            // If legacy .html exists and gzip is enabled, migrate it once to avoid keeping duplicates around.
            if (USE_GZIP) {
                try {
                    migratePlainHtmlToGzipIfNeeded(baseFile);
                } catch (IOException e) {
                    // Migration failure should not block saving; fall through to normal save.
                    ScrapingService.LOGGER.log(Level.FINER,
                            () -> "Cache migration failed for " + baseFile + ": " + e.getMessage());
                }
            }

            // If we can avoid writing at all, do it (major IO reduction)
            if (DEDUPE_BY_HASH && Files.exists(file)) {
                String existingHash = hashOfExistingFile(file);
                String newHash = sha256Hex(raw);
                if (existingHash != null && existingHash.equals(newHash)) {
                    ScrapingService.LOGGER.log(Level.FINER,
                            () -> "Cache unchanged, skipping write for " + key + " -> " + file.toAbsolutePath());
                    return;
                }
            }

            // Atomic write: write to temp file, then move/replace
            Path tmp = Files.createTempFile(file.getParent(),
                    file.getFileName().toString(),
                    ".tmp");

            try {
                if (USE_GZIP) {
                    writeGzip(tmp, raw);
                } else {
                    Files.write(tmp, raw, StandardOpenOption.TRUNCATE_EXISTING);
                }

                // Replace atomically if supported by FS
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                // Best effort cleanup if something failed mid-way
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }

            // If gzip is enabled, ensure we don't keep a parallel plain .html around
            if (USE_GZIP) {
                try { Files.deleteIfExists(baseFile); } catch (IOException ignored) {}
            }

            ScrapingService.LOGGER.log(Level.FINER,
                    () -> "Saved " + key + " to " + file.toAbsolutePath() + (USE_GZIP ? " (gz)" : ""));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<String> loadHtml(PageKey key) {
        Path baseFile = ScrapingPaths.fileFor(key.domain(), key.id(), key.url());

        try {
            // If gzip is enabled and we only have a legacy .html, migrate it once to .gz
            Path resolved = migratePlainHtmlToGzipIfNeeded(baseFile);

            if (!Files.exists(resolved)) return Optional.empty();

            if (resolved.getFileName().toString().toLowerCase().endsWith(".gz")) {
                return Optional.of(readGzipToString(resolved));
            }

            // Fallback (when gzip disabled)
            return Optional.of(Files.readString(resolved, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path withGzExtension(Path base) {
        String name = base.getFileName().toString();
        if (name.toLowerCase().endsWith(".gz")) return base;
        return base.resolveSibling(name + ".gz");
    }

    /**
     * If gzip is enabled:
     *  - If .html.gz exists -> return it
     *  - Else if plain .html exists -> convert it to .html.gz (atomic), delete plain, return .html.gz
     *  - Else -> return .html.gz path (non-existing)
     *
     * If gzip is disabled:
     *  - return base (.html) path (no migration)
     */
    private static Path migratePlainHtmlToGzipIfNeeded(Path baseFile) throws IOException {
        if (!USE_GZIP) return baseFile;

        Path gz = withGzExtension(baseFile);
        if (Files.exists(gz)) return gz;
        if (!Files.exists(baseFile)) return gz;

        // Read old html
        byte[] raw = Files.readAllBytes(baseFile);

        // Write gz atomically
        Files.createDirectories(gz.getParent());
        Path tmp = Files.createTempFile(gz.getParent(), gz.getFileName().toString(), ".tmp");

        try {
            writeGzip(tmp, raw);
            try {
                Files.move(tmp, gz, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, gz, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }

        // Delete old plain html
        Files.deleteIfExists(baseFile);

        ScrapingService.LOGGER.log(Level.FINER,
                () -> "Migrated cache file to gzip: " + baseFile + " -> " + gz);

        return gz;
    }

    private static void writeGzip(Path file, byte[] raw) throws IOException {
        try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.TRUNCATE_EXISTING);
             BufferedOutputStream bos = new BufferedOutputStream(out);
             GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(raw);
        }
    }

    private static String readGzipToString(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(in);
             GZIPInputStream gz = new GZIPInputStream(bis);
             InputStreamReader isr = new InputStreamReader(gz, StandardCharsets.UTF_8);
             StringWriter sw = new StringWriter()) {

            char[] buf = new char[8192];
            int r;
            while ((r = isr.read(buf)) != -1) {
                sw.write(buf, 0, r);
            }
            return sw.toString();
        }
    }

    // Hashing helpers (fast enough; avoids rewrite storms)
    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Should never happen on a normal JVM
            throw new IllegalStateException(e);
        }
    }

    private static String hashOfExistingFile(Path file) {
        try {
            if (file.getFileName().toString().toLowerCase().endsWith(".gz")) {
                // Hash the decompressed content so comparisons stay valid
                String s = readGzipToString(file);
                return sha256Hex(s.getBytes(StandardCharsets.UTF_8));
            } else {
                byte[] bytes = Files.readAllBytes(file);
                return sha256Hex(bytes);
            }
        } catch (IOException e) {
            // If we can't read it, just fall back to writing
            return null;
        }
    }
}
