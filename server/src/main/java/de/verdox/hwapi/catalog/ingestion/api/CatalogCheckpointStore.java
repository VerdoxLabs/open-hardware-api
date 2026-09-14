package de.verdox.hwapi.catalog.ingestion.api;

import de.verdox.hwapi.catalog.ingestion.ScrapingService;
import de.verdox.hwapi.infrastructure.storage.DataStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.logging.Level;

/**
 * Durable per-product checkpoints. HTML remains in the scraping cache; markers only
 * answer whether a cached product needs to be parsed again.
 */
final class CatalogCheckpointStore {
    private final Path directory;

    CatalogCheckpointStore(String domain, String scraperId) {
        String safeDomain = domain.replaceAll("[^a-zA-Z0-9.-]", "_");
        String safeId = scraperId.replaceAll("[^a-zA-Z0-9._-]", "_");
        this.directory = DataStorage.resolve("scraping/checkpoints/" + safeDomain + "/" + safeId);
    }

    boolean isDue(Set<String> urls, Duration interval) {
        return urls.stream().anyMatch(url -> isDue(url, interval));
    }

    void markProcessed(Set<String> urls) {
        urls.forEach(this::markProcessed);
    }

    private boolean isDue(String url, Duration interval) {
        Path marker = markerFor(url);
        try {
            if (!Files.exists(marker)) return true;
            FileTime timestamp = Files.getLastModifiedTime(marker);
            return timestamp.toInstant().isBefore(Instant.now().minus(interval));
        } catch (IOException exception) {
            // If a marker cannot be read, favor correctness over speed and reprocess it.
            return true;
        }
    }

    private void markProcessed(String url) {
        Path marker = markerFor(url);
        try {
            Files.createDirectories(directory);
            Files.writeString(marker, url + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            ScrapingService.LOGGER.log(Level.WARNING, "Could not persist catalog checkpoint " + marker, exception);
        }
    }

    private Path markerFor(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(url.strip().getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(digest) + ".done");
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create catalog checkpoint key", exception);
        }
    }
}
