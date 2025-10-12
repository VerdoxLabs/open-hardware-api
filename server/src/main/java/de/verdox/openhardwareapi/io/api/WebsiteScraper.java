package de.verdox.openhardwareapi.io.api;

import de.verdox.openhardwareapi.model.HardwareSpec;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class WebsiteScraper {
    private WebsiteScrapingStrategy strategy;
    private final Set<SpecificScrape<?>> scrapes = new HashSet<>();

    public WebsiteScraper withStrategy(WebsiteScrapingStrategy strategy) {
        this.strategy = strategy;
        return this;
    }

    public <HARDWARE extends HardwareSpec<HARDWARE>> WebsiteScraper withScrape(String id, Class<HARDWARE> clazz, HardwareQuery<HARDWARE> query, Consumer<SpecificScrape<HARDWARE>> builder) {
        SpecificScrape<HARDWARE> specificScrape = new SpecificScrape<>(id, query);
        builder.accept(specificScrape);
        scrapes.add(specificScrape);
        return this;
    }

    public class SpecificScrape<HARDWARE extends HardwareSpec<HARDWARE>> {
        private final List<Entry<HARDWARE>> entries = new ArrayList<>();
        private final String id;
        private final HardwareQuery<HARDWARE> query;
        Entry<HARDWARE> mainEntry;

        public SpecificScrape(String id, HardwareQuery<HARDWARE> query) {
            this.id = id;
            this.query = query;
        }

        public SpecificScrape<HARDWARE> addMainScrapeLogic(BiConsumer<ComponentWebScraper.ScrapedSpecs, HARDWARE> scrapeLogic, String... urls) {
            new Entry<>(id, scrapeLogic, urls);
            return this;
        }

        public SpecificScrape<HARDWARE> addVariant(String subId, BiConsumer<ComponentWebScraper.ScrapedSpecs, HARDWARE> scrapeLogic, String... urls) {
            entries.add(new Entry<>(subId, scrapeLogic, urls));
            return this;
        }

        public Set<WebsiteCatalogScraper<HARDWARE>> build() {
            Set<WebsiteCatalogScraper<HARDWARE>> set = new HashSet<>();

            if (mainEntry != null) {
                set.add(new WebsiteCatalogScraper<>(mainEntry.subId, mainEntry.urls) {
                    @Override
                    public Optional<HARDWARE> parse(ScrapedSpecs scrapedSpecs, ScrapeListener<HARDWARE> onScrape) throws Throwable {
                        String EAN = scrapedSpecs.specs().getOrDefault("EAN", List.of("---")).getFirst();
                        String UPC = scrapedSpecs.specs().getOrDefault("UPC", List.of("---")).getFirst();
                        String MPN = scrapedSpecs.specs().getOrDefault("MPN", List.of("---")).getFirst();

                        HARDWARE hw = query.findHardwareOrCreate(EAN, UPC, MPN);
                        mainEntry.scrapeLogic().accept(scrapedSpecs, hw);
                        return Optional.of(hw);
                    }

                    @Override
                    public int getAmountTasks() {
                        return 1;
                    }
                });
            }

            for (Entry<HARDWARE> entry : entries) {
                set.add(new WebsiteCatalogScraper<>(entry.subId, entry.urls) {
                    @Override
                    public Optional<HARDWARE> parse(ScrapedSpecs scrapedSpecs, ScrapeListener<HARDWARE> onScrape) throws Throwable {
                        String EAN = scrapedSpecs.specs().getOrDefault("EAN", List.of("---")).getFirst();
                        String UPC = scrapedSpecs.specs().getOrDefault("UPC", List.of("---")).getFirst();
                        String MPN = scrapedSpecs.specs().getOrDefault("MPN", List.of("---")).getFirst();

                        HARDWARE hw = query.findHardwareOrCreate(EAN, UPC, MPN);
                        mainEntry.scrapeLogic().accept(scrapedSpecs, hw);
                        entry.scrapeLogic().accept(scrapedSpecs, hw);
                        return Optional.of(hw);
                    }

                    @Override
                    public int getAmountTasks() {
                        return 1;
                    }
                });
            }

            for (WebsiteCatalogScraper<HARDWARE> hardwareWebsiteCatalogScraper : set) {
                hardwareWebsiteCatalogScraper.setWebsiteScrapingStrategy(strategy);
            }

            return set;
        }

        private record Entry<HARDWARE extends HardwareSpec<HARDWARE>>(
                String subId,
                BiConsumer<ComponentWebScraper.ScrapedSpecs, HARDWARE> scrapeLogic,
                String... urls
        ) {
        }
    }

    public interface HardwareQuery<HARDWARE extends HardwareSpec<HARDWARE>> {
        HARDWARE findHardwareOrCreate(String EAN, String UPC, String MPN);
    }
}
