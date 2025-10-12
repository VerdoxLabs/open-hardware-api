package de.verdox.openhardwareapi.component.service;


import de.verdox.openhardwareapi.io.api.ComponentWebScraper;
import de.verdox.openhardwareapi.io.gpu.DPGPUScraper;
import de.verdox.openhardwareapi.io.pc_combo_scraper.*;
import de.verdox.openhardwareapi.io.websites.alternate.AlternateScrapers;
import de.verdox.openhardwareapi.io.websites.caseking.CaseKingScrapers;
import de.verdox.openhardwareapi.io.websites.computersalg.ComputerSalgScrapers;
import de.verdox.openhardwareapi.io.websites.mindfactory.MindfactoryScrapers;
import de.verdox.openhardwareapi.io.websites.xkom.XKomScrapers;
import de.verdox.openhardwareapi.model.HardwareSpec;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

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
    private final SynchronizationService synchronizationService;
    private CompletableFuture<Void> currentlyRunning;
    private final List<ComponentWebScraper.ScrapeListener<HardwareSpec>> scrapeListeners = new ArrayList<>();
    private final List<ComponentWebScraper<? extends HardwareSpec>> scrapers;
    private final int amountTasksTotal;

    private static final int MAX_RETRIES = 3; // z.B. 3 Versuche (1 initial + 2 Retries)
    private final TaskScheduler taskScheduler;

    public ScrapingService(HardwareSpecService hardwareSpecService, SynchronizationService synchronizationService, TaskScheduler taskScheduler) {
        this.hardwareSpecService = hardwareSpecService;
        this.synchronizationService = synchronizationService;
        addListener(hardwareSpecService);

        this.scrapers = setupScrapers();
        this.amountTasksTotal = scrapers.stream().mapToInt(ComponentWebScraper::getAmountTasks).sum();


        this.taskScheduler = taskScheduler;
    }

    private List<ComponentWebScraper<? extends HardwareSpec>> setupScrapers() {
        return List.of(
                ComputerSalgScrapers.forCPU(hardwareSpecService),
                ComputerSalgScrapers.forMotherboard(hardwareSpecService),
                ComputerSalgScrapers.forPSU(hardwareSpecService),
                ComputerSalgScrapers.forCase(hardwareSpecService),
                ComputerSalgScrapers.forGPU(hardwareSpecService),
                ComputerSalgScrapers.forRAMDDR5(hardwareSpecService),
                ComputerSalgScrapers.forRAMDDR4(hardwareSpecService),
                ComputerSalgScrapers.forRAMDDR3(hardwareSpecService),
                ComputerSalgScrapers.forRAMDDR2(hardwareSpecService),
                ComputerSalgScrapers.forRAMDDR(hardwareSpecService),
                ComputerSalgScrapers.forSSD(hardwareSpecService),
                ComputerSalgScrapers.forHDD(hardwareSpecService),

                XKomScrapers.forMotherboard(hardwareSpecService),
                XKomScrapers.forCPU(hardwareSpecService),
                XKomScrapers.forGPU(hardwareSpecService),
                XKomScrapers.forCase(hardwareSpecService),
                XKomScrapers.forHDD(hardwareSpecService),
                XKomScrapers.forSSD(hardwareSpecService),
                XKomScrapers.forPSU(hardwareSpecService),
                XKomScrapers.forCPUCooler(hardwareSpecService),

                MindfactoryScrapers.forMotherboard(hardwareSpecService),
                MindfactoryScrapers.forCPU(hardwareSpecService),
                MindfactoryScrapers.forGPU(hardwareSpecService),
                MindfactoryScrapers.forCase(hardwareSpecService),
                MindfactoryScrapers.forHDD(hardwareSpecService),
                MindfactoryScrapers.forSSD(hardwareSpecService),
                MindfactoryScrapers.forPSU(hardwareSpecService),
                MindfactoryScrapers.forCPUCooler(hardwareSpecService),
                MindfactoryScrapers.forCPULiquidCooler(hardwareSpecService),

                AlternateScrapers.forCPU(hardwareSpecService),
                AlternateScrapers.forGPU(hardwareSpecService),
                AlternateScrapers.forRAM(hardwareSpecService),
                AlternateScrapers.forCase(hardwareSpecService),
                AlternateScrapers.forHDD(hardwareSpecService),
                AlternateScrapers.forCPUCooler(hardwareSpecService),
                AlternateScrapers.forCPULiquidCooler(hardwareSpecService),
                AlternateScrapers.forSataSSD(hardwareSpecService),
                AlternateScrapers.forM2SSD(hardwareSpecService),
                AlternateScrapers.forPSU(hardwareSpecService),
                AlternateScrapers.forMotherboard(hardwareSpecService),

                CaseKingScrapers.forCPU(hardwareSpecService),
                CaseKingScrapers.forGPU(hardwareSpecService),
                CaseKingScrapers.forRAM(hardwareSpecService),
                CaseKingScrapers.forCase(hardwareSpecService),
                CaseKingScrapers.forHDD(hardwareSpecService),
                CaseKingScrapers.forSSD(hardwareSpecService),
                CaseKingScrapers.forPSU(hardwareSpecService),
                CaseKingScrapers.forMotherboard(hardwareSpecService),


                new DPGPUScraper(hardwareSpecService),
                new CPUKomboScraper(hardwareSpecService),
                new GPUKomboScraper(hardwareSpecService),
                new RAMKomboScraper(hardwareSpecService),
                new CPUCoolerKomboScraper(hardwareSpecService),
                new CaseKomboScraper(hardwareSpecService),
                new PSUKomboScraper(hardwareSpecService),
                new MotherboardKomboScraper(hardwareSpecService),
                new HDDKomboScraper(hardwareSpecService),
                new SSDKomboScraper(hardwareSpecService),
                new DisplayKomboScraper(hardwareSpecService)
        );
    }

    public int getAmountTasks() {
        return amountTasksTotal;
    }

    public void addListener(ComponentWebScraper.ScrapeListener<HardwareSpec> listener) {
        scrapeListeners.add(listener);
    }

    public void removeListener(ComponentWebScraper.ScrapeListener<HardwareSpec> listener) {
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
        for (ComponentWebScraper<? extends HardwareSpec> scraper : scrapers) {
            try {
                scraper.downloadWebsites()
                        .map(document -> {
                            try {
                                return scraper.extract(document);
                            } catch (Throwable e) {
                                ScrapingService.LOGGER.log(Level.SEVERE, "Scraper produced an exception while extracting data from the document", e);
                                return null;
                            }
                        }).filter(Objects::nonNull)
                        .forEach(stringListMap -> {
                            try {
                                scraper.parse(stringListMap, this::callScrapeEvent);
                            } catch (Throwable e) {
                                ScrapingService.LOGGER.log(Level.SEVERE, "Scraper produced an exception while translating specs data to a target", e);
                            }
                        });
            } catch (Throwable e) {
                ScrapingService.LOGGER.log(Level.SEVERE, "Scraper produced an exception while downloading specs pages", e);
            }
        }
    }

    private void callScrapeEvent(HardwareSpec hardwareSpec) {
        for (ComponentWebScraper.ScrapeListener<HardwareSpec> scrapeListener : scrapeListeners) {
            try {
                scrapeListener.onScrape(hardwareSpec);
                synchronizationService.addToSyncQueue(hardwareSpec);
            } catch (Throwable ex) {
                LOGGER.log(Level.SEVERE, "Scraping listener " + scrapeListener.getClass().getSimpleName() + " produced an exception", ex);
            }
        }
    }
}