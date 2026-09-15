package de.verdox.hwapi.catalog.ingestion.websites.pcpartpicker;

import de.verdox.hwapi.catalog.domain.CPU;
import de.verdox.hwapi.catalog.domain.HardwareTypes;
import de.verdox.hwapi.catalog.ingestion.api.WebsiteScrapingStrategy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PCPartPickerCpuScraperTest {
    // Surefire executes tests from the server module; fixtures live in the repository root.
    private static final Path EXAMPLE_DIRECTORY = Path.of("..", "scrape", "example").normalize();
    private final PCPartPickerStrategy strategy = new PCPartPickerStrategy();

    @Test
    void findsTheProductLinksAndAllPaginationLinksFromTheSavedCpuCatalog() throws IOException {
        Document catalog = parse("Choose A CPU - PCPartPicker.html", PCPartPickerCpuScraper.CATALOG_URL);
        var pages = new ArrayDeque<WebsiteScrapingStrategy.MultiPageCandidate>();
        var products = new LinkedHashSet<WebsiteScrapingStrategy.SinglePageCandidate>();

        strategy.extractMultiPageURLs(PCPartPickerCpuScraper.CATALOG_URL, catalog, pages);
        strategy.extractSinglePagesURLs(PCPartPickerCpuScraper.CATALOG_URL, catalog, products);

        Set<String> pageUrls = pages.stream().map(WebsiteScrapingStrategy.MultiPageCandidate::url).collect(java.util.stream.Collectors.toSet());
        Set<String> productUrls = products.stream()
                .flatMap(candidate -> candidate.urls().stream())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(pageUrls).contains(
                "https://pcpartpicker.com/products/cpu/#page=1",
                "https://pcpartpicker.com/products/cpu/#page=15");
        assertThat(productUrls).contains("https://pcpartpicker.com/product/fPyH99/amd-ryzen-7-9800x3d-47-ghz-8-core-processor-100-1000001084wof");
        assertThat(products).hasSize(100);
    }

    @Test
    void parsesTheSaved9800x3dProductPageIntoCpuFields() throws Throwable {
        Document product = parse("AMD Ryzen 7 9800X3D 4.7 GHz 8-Core Processor (100-1000001084WOF) - PCPartPicker.html",
                "https://pcpartpicker.com/product/fPyH99/amd-ryzen-7-9800x3d-47-ghz-8-core-processor-100-1000001084wof");

        var specs = strategy.extractSpecMap(product);
        CPU cpu = new CPU();
        PCPartPickerCpuScraper.applySpecs(specs, cpu);

        assertThat(specs.get("MPN")).contains("100-1000001084WOF");
        assertThat(cpu.getManufacturer()).isEqualTo("AMD");
        assertThat(cpu.getSocket()).isEqualTo(HardwareTypes.CpuSocket.AM5);
        assertThat(cpu.getCores()).isEqualTo(8);
        assertThat(cpu.getThreads()).isEqualTo(16);
        assertThat(cpu.getBaseClockMhz()).isEqualTo(4700d);
        assertThat(cpu.getBoostClockMhz()).isEqualTo(5200d);
        assertThat(cpu.getL3CacheMb()).isEqualTo(96);
        assertThat(cpu.getTdpWatts()).isEqualTo(120);
    }

    private static Document parse(String fileName, String baseUri) throws IOException {
        Path fixture = EXAMPLE_DIRECTORY.resolve(fileName);
        if (!java.nio.file.Files.exists(fixture)) {
            fixture = Path.of("..", "scrape", fileName).normalize();
        }
        return Jsoup.parse(fixture.toFile(), "UTF-8", baseUri);
    }
}
