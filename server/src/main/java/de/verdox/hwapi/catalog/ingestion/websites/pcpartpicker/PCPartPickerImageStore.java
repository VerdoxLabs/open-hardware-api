package de.verdox.hwapi.catalog.ingestion.websites.pcpartpicker;

import de.verdox.hwapi.catalog.domain.HardwareSpec;
import de.verdox.hwapi.catalog.ingestion.api.selenium.DomainRateLimiter;
import de.verdox.hwapi.infrastructure.storage.DataStorage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Stores PCPartPicker CDN product images locally and exposes a stable API-relative URL. */
final class PCPartPickerImageStore {
    private static final Logger LOGGER = Logger.getLogger(PCPartPickerImageStore.class.getName());
    private static final Path IMAGE_DIRECTORY = DataStorage.resolve("images/pcpartpicker");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private PCPartPickerImageStore() {
    }

    static void storeFirstProductImage(java.util.Map<String, List<String>> specs, HardwareSpec<?> target) {
        List<String> images = specs.get("imageUrl");
        if (images == null || images.isEmpty()) return;

        String localUrl = download(images.getFirst());
        if (localUrl != null) {
            target.setPictureUrls(java.util.Set.of(localUrl));
        }
    }

    private static String download(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !host.toLowerCase(Locale.ROOT).endsWith("pcpartpicker.com")) {
                LOGGER.warning("Ignoring non-PCPartPicker image URL: " + rawUrl);
                return null;
            }

            String extension = extension(uri.getPath());
            String filename = sha256(rawUrl) + "." + extension;
            Path target = IMAGE_DIRECTORY.resolve(filename).normalize();
            if (!target.startsWith(IMAGE_DIRECTORY) || !target.getParent().equals(IMAGE_DIRECTORY)) {
                return null;
            }
            if (Files.isRegularFile(target) && Files.size(target) > 0) {
                return publicUrl(filename);
            }

            // cdna.pcpartpicker.com is part of the same provider. Keep image downloads
            // inside the exact same 60-second request budget as HTML pages.
            HttpResponse<byte[]> response;
            try (DomainRateLimiter.Permit ignored = DomainRateLimiter.acquire("pcpartpicker.com", Duration.ofSeconds(60))) {
                response = HTTP.send(HttpRequest.newBuilder(uri)
                                .timeout(Duration.ofSeconds(30))
                                .header("User-Agent", "PC-Flipping catalog image cache")
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofByteArray());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (response.statusCode() != 200 || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")
                    || response.body().length == 0 || response.body().length > 15 * 1024 * 1024) {
                LOGGER.warning("Rejected PCPartPicker image response for " + rawUrl + " (HTTP " + response.statusCode() + ")");
                return null;
            }

            Files.createDirectories(IMAGE_DIRECTORY);
            Path temporary = Files.createTempFile(IMAGE_DIRECTORY, filename, ".tmp");
            try {
                Files.write(temporary, response.body());
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return publicUrl(filename);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Could not cache PCPartPicker image " + rawUrl, exception);
            return null;
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Invalid PCPartPicker image URL " + rawUrl, exception);
            return null;
        }
    }

    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        String extension = dot >= 0 ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "jpg";
        return switch (extension) {
            case "jpg", "jpeg", "png", "webp", "gif", "avif" -> extension;
            default -> "jpg";
        };
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String publicUrl(String filename) {
        return "/api/v1/images/pcpartpicker/" + filename;
    }
}
