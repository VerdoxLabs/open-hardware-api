package de.verdox.openhardwareapi.component.service;


import com.google.gson.GsonBuilder;
import de.verdox.openhardwareapi.configuration.DataStorage;
import de.verdox.openhardwareapi.io.api.ComponentWebScraper;
import de.verdox.openhardwareapi.io.gpu.DPGPUScraper;
import de.verdox.openhardwareapi.io.websites.alternate.AlternateScrapers;
import de.verdox.openhardwareapi.io.websites.caseking.CaseKingScrapers;
import de.verdox.openhardwareapi.io.websites.computersalg.ComputerSalgScrapers;
import de.verdox.openhardwareapi.io.websites.mindfactory.MindfactoryScrapers;
import de.verdox.openhardwareapi.io.websites.pc_builder_io.PCBuilderIOScrapers;
import de.verdox.openhardwareapi.io.websites.pc_kombo.PCKomboScrapers;
import de.verdox.openhardwareapi.io.websites.xkom.XKomScrapers;
import de.verdox.openhardwareapi.model.HardwareSpec;
import jakarta.annotation.PostConstruct;
import org.apache.commons.io.FileUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@Component
public class ScrapingService {
    public static final Logger LOGGER = Logger.getLogger(ScrapingService.class.getSimpleName());

    static {
        try {
            // Verzeichnis anlegen
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("/data/logs"));

            // Optional: nur EINMAL FileHandler anhängen (nicht bei Hot-Reload doppeln)
            if (java.util.Arrays.stream(LOGGER.getHandlers()).noneMatch(h -> h instanceof java.util.logging.FileHandler)) {

                var fh = new java.util.logging.FileHandler("/data/logs/scraper-%u-%g.log",          // Pattern (Rolling)
                        10 * 1024 * 1024,                  // 10 MB pro Datei
                        5,                                  // 5 Dateien rotierend
                        true                                // append
                );
                fh.setEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
                fh.setFormatter(new java.util.logging.SimpleFormatter());
                fh.setLevel(Level.FINE);  // Handler-Level

                LOGGER.setUseParentHandlers(true);          // lässt ConsoleHandler des Root-Loggers aktiv
                LOGGER.addHandler(fh);
                LOGGER.setLevel(Level.INFO); // Logger-Level
            }
        } catch (Exception e) {
            e.printStackTrace(); // als letzter Auswegfig
        }
    }

    @PostConstruct
    public void onStartup() {
        startScraping();
    }

    private final HardwareSpecService hardwareSpecService;
    private final HardwareSyncService hardwareSyncService;
    private final RemoteSoldItemService remoteSoldItemService;
    private final EbayAPIService ebayAPIService;
    private CompletableFuture<Void> currentlyRunning;
    private final List<ComponentWebScraper.ScrapeListener<HardwareSpec<?>>> scrapeListeners = new ArrayList<>();
    private final List<ComponentWebScraper<? extends HardwareSpec<?>>> scrapers;
    private final int amountTasksTotal;

    private static final int MAX_RETRIES = 3; // z.B. 3 Versuche (1 initial + 2 Retries)
    private final TaskScheduler taskScheduler;

    public ScrapingService(HardwareSpecService hardwareSpecService, HardwareSyncService hardwareSyncService, RemoteSoldItemService remoteSoldItemService, EbayAPIService ebayAPIService, TaskScheduler taskScheduler) {
        this.hardwareSpecService = hardwareSpecService;
        this.hardwareSyncService = hardwareSyncService;
        this.remoteSoldItemService = remoteSoldItemService;
        this.ebayAPIService = ebayAPIService;
        addListener(hardwareSpecService);

        this.scrapers = setupScrapers();
        LOGGER.info("Registered " + scrapers.size() + " scrapers");
        this.amountTasksTotal = scrapers.stream().mapToInt(ComponentWebScraper::getAmountTasks).sum();


        this.taskScheduler = taskScheduler;
    }

    private List<ComponentWebScraper<? extends HardwareSpec<?>>> setupScrapers() {
        List<ComponentWebScraper<? extends HardwareSpec<?>>> scrapers = new ArrayList<>();

        scrapers.addAll(PCBuilderIOScrapers.create(hardwareSpecService).buildScrapers());
        scrapers.addAll(XKomScrapers.create(hardwareSpecService).buildScrapers());
        scrapers.addAll(PCKomboScrapers.create(hardwareSpecService).buildScrapers());
        scrapers.addAll(ComputerSalgScrapers.create(hardwareSpecService).buildScrapers());
        scrapers.addAll(MindfactoryScrapers.create(hardwareSpecService).buildScrapers());
        scrapers.addAll(CaseKingScrapers.create(hardwareSpecService).buildScrapers());
        scrapers.addAll(AlternateScrapers.create(hardwareSpecService).buildScrapers());
        return scrapers;
    }

    public int getAmountTasks() {
        return amountTasksTotal;
    }

    public void addListener(ComponentWebScraper.ScrapeListener<HardwareSpec<?>> listener) {
        scrapeListeners.add(listener);
    }

    public void removeListener(ComponentWebScraper.ScrapeListener<HardwareSpec<?>> listener) {
        scrapeListeners.add(listener);
    }

    // Täglich 02:00 Europe/Berlin
    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/Berlin")
    public void runDailyJob() {
        executeJob(1);
    }

    // Kernlogik + Retry-Planung
    private void executeJob(int attempt) {
        long started = System.currentTimeMillis();
        try {
            // --- Deine eigentliche Logik ---
            doWork();
            // -------------------------------
            long dur = System.currentTimeMillis() - started;
            LOGGER.info("Daily job OK (attempt " + attempt + ", " + dur + "ms).");
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Daily job FAILED (attempt " + attempt + ")", ex);
            if (attempt < MAX_RETRIES) {
                Instant when = Instant.now().plus(Duration.ofHours(1)); // Retry in 1h
                taskScheduler.schedule(() -> executeJob(attempt + 1), when);
                LOGGER.info("Retry #" + (attempt + 1) + " planned for " + when + ".");
            } else {
                // endgültig gescheitert -> Alarm, Metrik, Ticket, ...
                LOGGER.log(Level.SEVERE, "Daily job FAILED (attempt " + attempt + ")", ex);
            }
        }
    }

    private void doWork() {
        startScraping();
    }

    public CompletableFuture<Void> startScraping() {
        if (currentlyRunning != null) {
            return currentlyRunning;
        }

        currentlyRunning = CompletableFuture.runAsync(this::doScrape);
        return currentlyRunning;
    }

    private void doScrape() {

        try {
            new DPGPUScraper().scrape(this::callScrapeEvent);
        } catch (Throwable ex) {
            ScrapingService.LOGGER.log(Level.SEVERE, "Scraper produced an exception while extracting gpu chip data", ex);
        }

        for (ComponentWebScraper<? extends HardwareSpec> scraper : scrapers) {
            ScrapingService.LOGGER.log(Level.INFO, "Starting scraper " + scraper.baseURL() + "[" + scraper.id() + "]");
            try {
                long count = scraper.downloadWebsites()
                        .map(document -> {
                            try {
                                return scraper.extract(document);
                            } catch (Throwable e) {
                                ScrapingService.LOGGER.log(Level.SEVERE, "Scraper produced an exception while extracting data from [" + document.singlePageCandidate().url() + "]", e);
                                return null;
                            }
                        }).filter(Objects::nonNull)
                        .peek(stringListMap -> {
                            try {
                                var result = scraper.parse(stringListMap, this::callScrapeEvent);
                                if (result.isPresent() && !stringListMap.specs().isEmpty()) {
                                    String json = new GsonBuilder().setPrettyPrinting().create().toJson(stringListMap);
                                    String model = result.get().getModel().trim();

                                    String safeModel = model.replaceAll("[\\\\/:*?\"<>|]", "_");

                                    Path path = DataStorage.resolve("scraping/specs/" + scraper.baseURL() + "/" + scraper.id() + "/" + safeModel + ".json");
                                    FileUtils.writeStringToFile(path.toFile(), json, StandardCharsets.UTF_8);
                                }
                            } catch (Throwable e) {
                                ScrapingService.LOGGER.log(Level.SEVERE, "Scraper produced an exception while translating specs data to a target", e);
                            }
                        }).count();
                ScrapingService.LOGGER.log(Level.INFO, "Scraper scraped " + count + " products");
            } catch (Throwable e) {
                ScrapingService.LOGGER.log(Level.SEVERE, "Scraper produced an exception while downloading specs pages", e);
            }
        }
    }

    private <HARDWARE extends HardwareSpec<HARDWARE>> void callScrapeEvent(HARDWARE hardwareSpec) {
        for (ComponentWebScraper.ScrapeListener<HardwareSpec<?>> scrapeListener : scrapeListeners) {
            try {
                scrapeListener.onScrape(hardwareSpec);
                hardwareSyncService.addToSyncQueue(hardwareSpec);
                for (String ean : hardwareSpec.getEANs()) {
                    ebayAPIService.trackEAN(ean);
                    remoteSoldItemService.queueForPriceFetch(ean);
                }
            } catch (Throwable ex) {
                LOGGER.log(Level.SEVERE, "Scraping listener " + scrapeListener.getClass().getSimpleName() + " produced an exception", ex);
            }
        }
    }
}