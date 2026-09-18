package de.verdox.hwapi.catalog.ingestion.images;

import de.verdox.hwapi.infrastructure.storage.DataStorage;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@RestController
public class ProductImageController {
    private static final Path IMAGE_DIRECTORY = DataStorage.resolve("images/products");
    private static final String FILE_NAME = "[a-f0-9]{64}\\.(?:jpg|png|webp|gif|avif)";

    @GetMapping("/api/v1/images/products/{fileName:.+}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String fileName) {
        if (!fileName.matches(FILE_NAME)) return ResponseEntity.notFound().build();
        Path image = IMAGE_DIRECTORY.resolve(fileName).normalize();
        if (!image.startsWith(IMAGE_DIRECTORY) || !Files.isRegularFile(image)) return ResponseEntity.notFound().build();
        try {
            String contentType = Files.probeContentType(image);
            MediaType mediaType = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
            return ResponseEntity.ok().contentType(mediaType)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                    .body(new FileSystemResource(image));
        } catch (Exception ignored) {
            return ResponseEntity.notFound().build();
        }
    }
}
