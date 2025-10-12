package de.verdox.openhardwareapi.io.api.selenium;

import de.verdox.openhardwareapi.component.service.ScrapingService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Stream;

public class FScrapingCache implements ScrapingCache {
    @Override
    public void saveHtml(PageKey key, String html) {
        Path file = ScrapingPaths.fileFor(key.domain(), key.id(), key.url());
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, html, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            ScrapingService.LOGGER.log(Level.FINER, "Saving " + key + " to " + file.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<String> loadHtml(PageKey key) {
        Path file = ScrapingPaths.fileFor(key.domain(), key.id(), key.url());
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Stream<Path> listHtmlFilesById(String domain, String id) {
        Path dir = ScrapingPaths.idFolder(domain, id);
        if (!Files.isDirectory(dir)) return Stream.empty();
        try {
            return Files.walk(dir)
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".html") || n.endsWith(".html.gz") || n.endsWith(".htm") || n.endsWith(".xhtml");
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
