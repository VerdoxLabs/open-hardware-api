package de.verdox.hwapi.admin;

import de.verdox.hwapi.catalog.ingestion.api.selenium.ScrapingPaths;
import de.verdox.hwapi.integration.client.admin.HardwareAdminDtos;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

@Service
public class CacheOverviewService {
    private static final Duration SNAPSHOT_TTL = Duration.ofSeconds(30);
    private HardwareAdminDtos.CacheOverview lastSnapshot;

    public synchronized HardwareAdminDtos.CacheOverview scan() {
        if (lastSnapshot != null && Duration.between(lastSnapshot.scannedAt(), Instant.now()).compareTo(SNAPSHOT_TTL) < 0) {
            return lastSnapshot;
        }
        Path root = ScrapingPaths.root();
        if (!Files.isDirectory(root)) {
            lastSnapshot = new HardwareAdminDtos.CacheOverview(Instant.now(), 0, 0, 0, 0, List.of());
            return lastSnapshot;
        }

        List<HardwareAdminDtos.CacheSource> sources = new ArrayList<>();
        try (var websites = Files.list(root)) {
            websites.filter(Files::isDirectory).sorted().forEach(website -> scanWebsite(website, sources));
        } catch (Exception ignored) {
            // The admin view should remain available even when a cache entry disappears during a scan.
        }

        long files = sources.stream().mapToLong(HardwareAdminDtos.CacheSource::cacheFiles).sum();
        long catalogs = sources.stream().mapToLong(HardwareAdminDtos.CacheSource::paginationPages).sum();
        long recognized = sources.stream().mapToLong(HardwareAdminDtos.CacheSource::recognizedProducts).sum();
        long details = sources.stream().mapToLong(HardwareAdminDtos.CacheSource::detailPages).sum();
        lastSnapshot = new HardwareAdminDtos.CacheOverview(Instant.now(), files, catalogs, recognized, details,
                sources.stream().sorted(Comparator.comparing(HardwareAdminDtos.CacheSource::website)
                        .thenComparing(HardwareAdminDtos.CacheSource::category)).toList());
        return lastSnapshot;
    }

    private void scanWebsite(Path website, List<HardwareAdminDtos.CacheSource> result) {
        try (var categories = Files.list(website)) {
            categories.filter(Files::isDirectory).forEach(category -> {
                long files = 0, catalogs = 0, details = 0;
                Set<String> recognizedProducts = new HashSet<>();
                Instant latest = null;
                try (var entries = Files.list(category)) {
                    for (Path file : entries.filter(this::isHtmlCache).toList()) {
                        files++;
                        Instant modified = Files.getLastModifiedTime(file).toInstant();
                        if (latest == null || modified.isAfter(latest)) latest = modified;
                        Document document = parse(file);
                        if (isCatalog(document)) {
                            catalogs++;
                            recognizedProducts.addAll(document.select("td.td__name a[href*='/product/']").stream()
                                    .map(element -> element.absUrl("href").isBlank() ? element.attr("href") : element.absUrl("href"))
                                    .filter(url -> !url.isBlank()).toList());
                        } else if (isDetail(document)) {
                            details++;
                        }
                    }
                } catch (Exception ignored) {
                    // Keep counts for entries that were readable.
                }
                if (files > 0) {
                    result.add(new HardwareAdminDtos.CacheSource(website.getFileName().toString(),
                            category.getFileName().toString(), files, catalogs, recognizedProducts.size(), details, latest));
                }
            });
        } catch (Exception ignored) {
        }
    }

    private boolean isHtmlCache(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".html") || name.endsWith(".html.gz");
    }

    private Document parse(Path path) throws Exception {
        try (InputStream file = Files.newInputStream(path);
             InputStream input = path.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(file) : file) {
            return Jsoup.parse(input, "UTF-8", "https://cached.invalid/");
        }
    }

    private boolean isCatalog(Document document) {
        return !document.select("table.productList--detailed").isEmpty()
                || !document.select("#category_content tr.tr__product").isEmpty()
                || document.title().toLowerCase().startsWith("choose ");
    }

    private boolean isDetail(Document document) {
        return !document.select("div.group.group--spec").isEmpty()
                || !document.select("meta[property=og:title]").isEmpty();
    }
}
