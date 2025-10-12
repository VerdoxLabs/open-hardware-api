package de.verdox.openhardwareapi.io.api.selenium;

import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public interface ScrapingCache {
    void saveHtml(PageKey key, String html);

    Optional<String> loadHtml(PageKey key);

    Stream<Path> listHtmlFilesById(String domain, String id);
}
