package de.verdox.openhardwareapi.io.api;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.component.service.ScrapingService;
import de.verdox.openhardwareapi.model.*;
import lombok.Getter;
import org.jsoup.nodes.Document;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public class WebsiteScraper {
    private final HardwareSpecService service;
    private final String domain;
    private WebsiteScrapingStrategy strategy;
    @Getter
    private final Set<SpecificScrape<?>> scrapes = new HashSet<>();
    private BiPredicate<String, Document> challengeDetection = null;
    private BiPredicate<String, Document> shouldSave = null;
    private BiConsumer<ComponentWebScraper.ScrapedSpecs, HardwareSpec<?>> baseLogic;

    public WebsiteScraper(HardwareSpecService service, String domain) {
        this.service = service;
        this.domain = domain;
    }

    public WebsiteScraper withStrategy(WebsiteScrapingStrategy strategy) {
        this.strategy = strategy;
        return this;
    }

    public List<? extends WebsiteCatalogScraper<?>> buildScrapers() {
        return getScrapes().stream().flatMap(specificScrape -> specificScrape.build().stream().peek(websiteCatalogScraper -> {
            if (challengeDetection != null) {
                websiteCatalogScraper.getSeleniumBasedWebScraper().setIsChallengePage(challengeDetection);
            }
            if (shouldSave != null) {
                websiteCatalogScraper.getSeleniumBasedWebScraper().setShouldSavePage(shouldSave);
            }
        })).toList();
    }

    public <HARDWARE extends HardwareSpec<HARDWARE>> WebsiteScraper withScrape(String id, Supplier<HARDWARE> constructor, Consumer<SpecificScrape<HARDWARE>> builder) {
        SpecificScrape<HARDWARE> specificScrape = new SpecificScrape<>(id, EAN -> {
            HARDWARE found = service.findByEAN(EAN);
            if (found == null) {
                return constructor.get();
            }
            return found;
        });
        builder.accept(specificScrape);
        scrapes.add(specificScrape);
        return this;
    }

    public WebsiteScraper withBaseLogic(BiConsumer<ComponentWebScraper.ScrapedSpecs, HardwareSpec<?>> baseLogic) {
        this.baseLogic = baseLogic;
        return this;
    }

    public WebsiteScraper withChallengePageDetection(BiPredicate<String, Document> detection) {
        this.challengeDetection = detection;
        return this;
    }

    public WebsiteScraper withShouldSavePredicate(BiPredicate<String, Document> shouldSave) {
        this.shouldSave = shouldSave;
        return this;
    }

    public WebsiteScraper withCPUScrape(String id, Consumer<SpecificScrape<CPU>> builder) {
        return withScrape(id, CPU::new, builder);
    }

    public WebsiteScraper withCPUScrape(Consumer<SpecificScrape<CPU>> builder) {
        return withScrape("CPU", CPU::new, builder);
    }

    public WebsiteScraper withCPUCoolerScrape(String id, Consumer<SpecificScrape<CPUCooler>> builder) {
        return withScrape(id, CPUCooler::new, builder);
    }

    public WebsiteScraper withCPUCoolerScrape(Consumer<SpecificScrape<CPUCooler>> builder) {
        return withScrape("CPUCooler", CPUCooler::new, builder);
    }

    public WebsiteScraper withDisplayScrape(String id, Consumer<SpecificScrape<Display>> builder) {
        return withScrape(id, Display::new, builder);
    }

    public WebsiteScraper withDisplayScrape(Consumer<SpecificScrape<Display>> builder) {
        return withScrape("Display", Display::new, builder);
    }

    public WebsiteScraper withGPUScrape(String id, Consumer<SpecificScrape<GPU>> builder) {
        return withScrape(id, GPU::new, builder);
    }

    public WebsiteScraper withGPUScrape(Consumer<SpecificScrape<GPU>> builder) {
        return withScrape("GPU", GPU::new, builder);
    }

    public WebsiteScraper withGPUChipScrape(String id, Consumer<SpecificScrape<GPUChip>> builder) {
        return withScrape(id, GPUChip::new, builder);
    }

    public WebsiteScraper withGPUChipScrape(Consumer<SpecificScrape<GPUChip>> builder) {
        return withScrape("GPU-Chip", GPUChip::new, builder);
    }

    public WebsiteScraper withMotherboardScrape(String id, Consumer<SpecificScrape<Motherboard>> builder) {
        return withScrape(id, Motherboard::new, builder);
    }

    public WebsiteScraper withMotherboardScrape(Consumer<SpecificScrape<Motherboard>> builder) {
        return withScrape("Motherboard", Motherboard::new, builder);
    }

    public WebsiteScraper withPCCaseScraper(String id, Consumer<SpecificScrape<PCCase>> builder) {
        return withScrape(id, PCCase::new, builder);
    }

    public WebsiteScraper withPCCaseScraper(Consumer<SpecificScrape<PCCase>> builder) {
        return withScrape("PCCase", PCCase::new, builder);
    }

    public WebsiteScraper withPSUScraper(String id, Consumer<SpecificScrape<PSU>> builder) {
        return withScrape(id, PSU::new, builder);
    }

    public WebsiteScraper withPSUScraper(Consumer<SpecificScrape<PSU>> builder) {
        return withScrape("PSU", PSU::new, builder);
    }

    public WebsiteScraper withRAMScraper(String id, Consumer<SpecificScrape<RAM>> builder) {
        return withScrape(id, RAM::new, builder);
    }

    public WebsiteScraper withRAMScraper(Consumer<SpecificScrape<RAM>> builder) {
        return withScrape("RAM", RAM::new, builder);
    }

    public WebsiteScraper withStorageScraper(String id, Consumer<SpecificScrape<Storage>> builder) {
        return withScrape(id, Storage::new, builder);
    }

    public WebsiteScraper withStorageScraper(Consumer<SpecificScrape<Storage>> builder) {
        return withScrape("Storage", Storage::new, builder);
    }

    public class SpecificScrape<HARDWARE extends HardwareSpec<HARDWARE>> {
        private final List<Entry<HARDWARE>> entries = new ArrayList<>();
        private final String id;
        private final HardwareQuery<HARDWARE> query;
        private Entry<HARDWARE> mainEntry;


        public SpecificScrape(String id, HardwareQuery<HARDWARE> query) {
            this.id = id;
            this.query = query;
        }

        public SpecificScrape<HARDWARE> addMainScrapeLogic(BiConsumer<ComponentWebScraper.ScrapedSpecs, HARDWARE> scrapeLogic, String... urls) {
            this.mainEntry = new Entry<>(id, scrapeLogic, urls);
            return this;
        }

        public SpecificScrape<HARDWARE> addVariant(String subId, BiConsumer<ComponentWebScraper.ScrapedSpecs, HARDWARE> scrapeLogic, String... urls) {
            entries.add(new Entry<>(subId, scrapeLogic, urls));
            return this;
        }

        public Set<WebsiteCatalogScraper<HARDWARE>> build() {
            Set<WebsiteCatalogScraper<HARDWARE>> set = new HashSet<>();

            if (mainEntry != null) {
                var main = new WebsiteCatalogScraper<HARDWARE>(domain, mainEntry.subId, mainEntry.urls) {
                    @Override
                    public Optional<HARDWARE> parse(ScrapedSpecs scrapedSpecs, ScrapeListener<HARDWARE> onScrape) throws Throwable {
                        HARDWARE hw = extractHardware(scrapedSpecs);
                        mainEntry.scrapeLogic().accept(scrapedSpecs, hw);
                        try {
                            if(HardwareSpecService.sanitizeBeforeSave(hw)) {
                                onScrape.onScrape(hw);
                                return Optional.of(hw);
                            }
                        } catch (Throwable e) {
                            ScrapingService.LOGGER.log(Level.SEVERE, "The " + mainEntry.subId + " scraper for " + domain + " produced an invalid hardware object [" + scrapedSpecs.url() + "]", e);

                        }
                        return Optional.empty();
                    }

                    @Override
                    public int getAmountTasks() {
                        return 1;
                    }
                };
                set.add(main);
            }

            for (Entry<HARDWARE> entry : entries) {
                set.add(new WebsiteCatalogScraper<>(domain, entry.subId, entry.urls) {
                    @Override
                    public Optional<HARDWARE> parse(ScrapedSpecs scrapedSpecs, ScrapeListener<HARDWARE> onScrape) throws Throwable {
                        HARDWARE hw = extractHardware(scrapedSpecs);

                        mainEntry.scrapeLogic().accept(scrapedSpecs, hw);
                        entry.scrapeLogic().accept(scrapedSpecs, hw);
                        try {
                            if(HardwareSpecService.sanitizeBeforeSave(hw)) {
                                onScrape.onScrape(hw);
                                return Optional.of(hw);
                            }
                        } catch (Throwable e) {
                            ScrapingService.LOGGER.log(Level.SEVERE, "The " + entry.subId + " scraper for " + domain + " produced an invalid hardware object", e);
                        }
                        return Optional.empty();
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

        private HARDWARE extractHardware(ComponentWebScraper.ScrapedSpecs scrapedSpecs) {
            String EAN = scrapedSpecs.specs().getOrDefault("EAN", List.of("---")).getFirst();
            String MPN = scrapedSpecs.specs().getOrDefault("MPN", List.of("---")).getFirst();

            HARDWARE hw = query.findHardwareOrCreate(EAN);

            if (baseLogic != null) {
                baseLogic.accept(scrapedSpecs, hw);
            }

            if (!EAN.equals("---")) {
                hw.setEAN(EAN);
            }

            if (!MPN.equals("---")) {
                hw.setMPN(MPN);
            }

            String model = scrapedSpecs.specs().getOrDefault("model", List.of("")).getFirst();
            String manufacturer = scrapedSpecs.specs().getOrDefault("manufacturer", List.of("")).getFirst();


            if (!model.isBlank()) {
                hw.setModel(model);
            }
            if (!manufacturer.isBlank()) {
                hw.setManufacturer(manufacturer);
            }
            return hw;
        }

        private record Entry<HARDWARE extends HardwareSpec<HARDWARE>>(
                String subId,
                BiConsumer<ComponentWebScraper.ScrapedSpecs, HARDWARE> scrapeLogic,
                String... urls
        ) {
        }
    }

    public interface HardwareQuery<HARDWARE extends HardwareSpec<HARDWARE>> {
        HARDWARE findHardwareOrCreate(String EAN);
    }
}
