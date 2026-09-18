package de.verdox.hwapi.catalog.ingestion.images;

import de.verdox.hwapi.catalog.domain.HardwareSpec;
import de.verdox.hwapi.catalog.domain.ProductImageAttribution;
import de.verdox.hwapi.catalog.ingestion.api.ComponentWebScraper;
import de.verdox.hwapi.infrastructure.storage.DataStorage;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Mirrors product images locally and retains their original URL and source-page attribution. */
public final class ProductImageStore {
    private static final Logger LOGGER = Logger.getLogger(ProductImageStore.class.getName());
    private static final Path IMAGE_DIRECTORY = DataStorage.resolve("images/products");
    private static final int MAX_BYTES = 15 * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private ProductImageStore() {
    }

    public static void store(ComponentWebScraper.ScrapedSpecs scraped, HardwareSpec<?> target) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.addAll(scraped.specs().getOrDefault(ProductImageCandidates.SPEC_KEY, List.of()));
        // Existing providers used these names before image handling became source-independent.
        candidates.addAll(scraped.specs().getOrDefault("imageUrl", List.of()));
        candidates.addAll(scraped.specs().getOrDefault("img", List.of()));
        String sourcePage = scraped.urls().stream().findFirst().orElse(null);
        if (sourcePage == null) return;

        for (String originalUrl : candidates) {
            storeExternal(originalUrl, target, sourcePage);
        }
    }

    /** Downloads an image from a feed/listing and attaches the stable local URL to a catalog item. */
    public static boolean storeExternal(String originalUrl, HardwareSpec<?> target, String sourcePage) {
        if (target == null || originalUrl == null || originalUrl.isBlank()
                || sourcePage == null || sourcePage.isBlank()) return false;
        StoredImage stored = download(originalUrl);
        if (stored == null) return false;
        target.getPictureUrls().add(stored.localUrl());
        target.addImageAttribution(new ProductImageAttribution(stored.localUrl(), originalUrl, sourcePage));
        return true;
    }

    private static StoredImage download(String rawUrl) {
        try {
            URI current = URI.create(rawUrl);
            for (int redirects = 0; redirects <= 3; redirects++) {
                validateExternalHttps(current);
                HttpResponse<byte[]> response = HTTP.send(HttpRequest.newBuilder(current)
                                .timeout(Duration.ofSeconds(30))
                                .header("User-Agent", "PC-Flipping product image cache")
                                .header("Accept", "image/avif,image/webp,image/png,image/jpeg,image/gif,*/*;q=0.5")
                                .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    String location = response.headers().firstValue("Location").orElse(null);
                    if (location == null) return null;
                    current = current.resolve(location);
                    continue;
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                if (response.statusCode() != 200 || !contentType.startsWith("image/")
                        || response.body().length == 0 || response.body().length > MAX_BYTES) {
                    return null;
                }
                String filename = sha256(rawUrl) + "." + extension(contentType, current.getPath());
                Path target = IMAGE_DIRECTORY.resolve(filename).normalize();
                if (!target.startsWith(IMAGE_DIRECTORY) || !target.getParent().equals(IMAGE_DIRECTORY)) return null;
                if (!Files.isRegularFile(target) || Files.size(target) == 0) writeAtomically(target, response.body(), filename);
                return new StoredImage(publicUrl(filename));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Image download interrupted: " + rawUrl, exception);
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.FINE, "Could not mirror product image " + rawUrl, exception);
        }
        return null;
    }

    private static void validateExternalHttps(URI uri) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IOException("Only external HTTPS image URLs are supported");
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new IOException("Private image host is not allowed");
            }
        }
    }

    private static void writeAtomically(Path target, byte[] body, String filename) throws IOException {
        Files.createDirectories(IMAGE_DIRECTORY);
        Path temporary = Files.createTempFile(IMAGE_DIRECTORY, filename, ".tmp");
        try {
            Files.write(temporary, body);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String extension(String contentType, String path) {
        if (contentType.contains("png")) return "png";
        if (contentType.contains("webp")) return "webp";
        if (contentType.contains("gif")) return "gif";
        if (contentType.contains("avif")) return "avif";
        if (contentType.contains("jpeg") || contentType.contains("jpg")) return "jpg";
        int dot = path == null ? -1 : path.lastIndexOf('.');
        return dot >= 0 && path.substring(dot + 1).matches("(?i)jpe?g|png|webp|gif|avif")
                ? path.substring(dot + 1).toLowerCase(Locale.ROOT).replace("jpeg", "jpg") : "jpg";
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String publicUrl(String filename) { return "/api/v1/images/products/" + filename; }

    private record StoredImage(String localUrl) { }
}
