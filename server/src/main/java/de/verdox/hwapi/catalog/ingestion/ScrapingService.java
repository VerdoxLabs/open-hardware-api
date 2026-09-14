package de.verdox.hwapi.catalog.ingestion;

import de.verdox.hwapi.catalog.application.HardwareSpecService;
import de.verdox.hwapi.catalog.application.HardwareSyncService;
import de.verdox.hwapi.configuration.ScrapingEnabled;
import de.verdox.hwapi.catalog.ingestion.api.ComponentWebScraper;
import de.verdox.hwapi.catalog.ingestion.websites.amd.AmdCpuCsvImporter;
import de.verdox.hwapi.catalog.ingestion.websites.intel.IntelScraper;
import de.verdox.hwapi.catalog.ingestion.websites.pc_builder_io.PCBuilderIOScrapers;
import de.verdox.hwapi.catalog.ingestion.websites.pc_kombo.PCKomboScrapers;
import de.verdox.hwapi.catalog.ingestion.websites.pcpartpicker.PCPartPickerCpuScraper;
import de.verdox.hwapi.catalog.ingestion.websites.pcpartpicker.PCPartPickerScrapers;
import de.verdox.hwapi.catalog.domain.CPU;
import de.verdox.hwapi.catalog.domain.HardwareSpec;
import de.verdox.hwapi.identity.ProductIdentifier;
import de.verdox.hwapi.identity.dto.ProductSearchResultDTO;
import de.verdox.hwapi.identity.application.ProductRegistryService;
import de.verdox.hwapi.integration.client.admin.HardwareAdminDtos;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class ScrapingService {

    public static final Logger LOGGER = Logger.getLogger(ScrapingService.class.getSimpleName());

    private final AtomicReference<Instant> startedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastFinishedAt = new AtomicReference<>();

    private final AtomicReference<Double> progress01 = new AtomicReference<>(null);

    private final AtomicReference<String> statusMessage = new AtomicReference<>("Idle");
    private final AtomicReference<String> detailMessage = new AtomicReference<>("");

    private final AtomicInteger doneTasksWeighted = new AtomicInteger(0);
    private int totalTasksWeighted = 0;

    public Optional<Double> getProgress01() {
        return Optional.ofNullable(progress01.get());
    }

    public Optional<String> getStatusMessage() {
        return Optional.ofNullable(statusMessage.get());
    }

    public Optional<Instant> getStartedAt() {
        return Optional.ofNullable(startedAt.get());
    }

    public Optional<Instant> getLastFinishedAt() {
        return Optional.ofNullable(lastFinishedAt.get());
    }

    public boolean isRunning() {
        return currentlyRunning != null && !currentlyRunning.isDone();
    }


    /* ------------------------------------------------------------
     * Dependencies
     * ------------------------------------------------------------ */

    private final HardwareSpecService hardwareSpecService;
    private final HardwareSyncService hardwareSyncService;
    private final ProductRegistryService productRegistryService;
    private final TaskExecutor jobExecutor;
    private final ScrapingEnabled scrapingEnabled;

    private CompletableFuture<Void> currentlyRunning;

    private final List<ComponentWebScraper.ScrapeListener<HardwareSpec<?>>> scrapeListeners =
            new ArrayList<>();

    private final List<ComponentWebScraper<? extends HardwareSpec<?>>> scrapers;
    private final ConcurrentMap<String, ScraperProgress> scraperProgress = new ConcurrentHashMap<>();

    private static final int MAX_RETRIES = 3;

    /* ------------------------------------------------------------
     * Constructor / Setup
     * ------------------------------------------------------------ */

    public ScrapingService(
            HardwareSpecService hardwareSpecService,
            HardwareSyncService hardwareSyncService,
            ProductRegistryService productRegistryService,
            @Qualifier("jobExecutor") TaskExecutor jobExecutor,
            ScrapingEnabled scrapingEnabled
    ) {
        this.hardwareSpecService = hardwareSpecService;
        this.hardwareSyncService = hardwareSyncService;
        this.productRegistryService = productRegistryService;
        this.jobExecutor = jobExecutor;
        this.scrapingEnabled = scrapingEnabled;

        addListener(hardwareSpecService);
        this.scrapers = setupScrapers();
        this.scrapers.forEach(scraper -> scraperProgress.put(scraper.id(),
                new ScraperProgress(scraper.id(), scraper.baseURL(), safeTaskEstimate(scraper))));
    }

    private List<ComponentWebScraper<? extends HardwareSpec<?>>> setupScrapers() {
        List<ComponentWebScraper<? extends HardwareSpec<?>>> list = new ArrayList<>();
        list.addAll(IntelScraper.create(hardwareSpecService).buildScrapers());
        list.addAll(PCBuilderIOScrapers.create(hardwareSpecService).buildScrapers());
        list.addAll(PCKomboScrapers.create(hardwareSpecService).buildScrapers());
        list.addAll(PCPartPickerCpuScraper.create(hardwareSpecService).buildScrapers());
        list.addAll(PCPartPickerScrapers.create(hardwareSpecService).buildScrapers());
        return list;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startScrapingOnStart() {
        if (!scrapingEnabled.isEnabled()) {
            LOGGER.info("Catalog scraping is disabled by HWAPI_SCRAPING_ENABLED.");
            return;
        }
        executeJob(1);
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/Berlin")
    public void runDailyJob() {
        if (!scrapingEnabled.isEnabled()) return;
        executeJob(1);
    }

    private void executeJob(int attempt) {
        try {
            startScraping().join();
            LOGGER.info("Daily scraping job finished (attempt " + attempt + ")");
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Daily scraping failed (attempt " + attempt + ")", ex);
        }
    }

    /* ------------------------------------------------------------
     * Public API
     * ------------------------------------------------------------ */

    public synchronized CompletableFuture<Void> startScraping() {
        if (!scrapingEnabled.isEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Catalog scraping is disabled."));
        }
        if (isRunning()) {
            return currentlyRunning;
        }

        // Reset progress
        startedAt.set(Instant.now());
        progress01.set(0.0);
        statusMessage.set("Scraping startet…");
        doneTasksWeighted.set(0);
        lastFinishedAt.set(null);

        // Gesamtgewicht berechnen
        totalTasksWeighted = 1;

        for (ComponentWebScraper<?> scraper : scrapers) {
            try {
                int estimated = scraper.getAmountTasks();
                totalTasksWeighted += Math.max(1, estimated);
            } catch (Exception e) {
                totalTasksWeighted += 1;
            }
        }
        scraperProgress.values().forEach(ScraperProgress::resetForRun);

        currentlyRunning = CompletableFuture.runAsync(this::doScrape, jobExecutor)
                .whenComplete((v, ex) -> {
                    progress01.set(null);
                    statusMessage.set("Idle");
                    lastFinishedAt.set(Instant.now());
                });

        return currentlyRunning;
    }

    /* ------------------------------------------------------------
     * Core Logic
     * ------------------------------------------------------------ */

    private void doScrape() {

        // --- AMD CSV ---
        setStatus("AMD CPU CSV Import…");
        LOGGER.info("Starting AMD CPU csv scraper");

        try (Reader reader = new InputStreamReader(
                getClass().getResourceAsStream("/data-sheets/amd/cpu/specs.csv"))) {

            var scrapedCPUs = AmdCpuCsvImporter.importFrom(reader);
            hardwareSpecService.onScrapeMulti(scrapedCPUs);

            for (CPU cpu : scrapedCPUs) {
                register("amd.com", cpu.getModel(), Optional.of(cpu));
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not scrape AMD CPU list", e);
        } finally {
            stepDone(1);
        }

        // --- Web Scrapers ---
        // Each scraper owns its site-specific state (including its browser scraper), so the
        // independent site jobs can run concurrently. The injected executor provides the
        // global bound and backpressure; we deliberately do not create an unbounded pool here.
        List<CompletableFuture<Void>> scraperTasks = scrapers.stream()
                .map(scraper -> CompletableFuture.runAsync(() -> scrapeOne(scraper), jobExecutor))
                .toList();

        CompletableFuture.allOf(scraperTasks.toArray(CompletableFuture[]::new)).join();
    }

    public boolean isEnabled() {
        return scrapingEnabled.isEnabled();
    }

    private void scrapeOne(ComponentWebScraper<? extends HardwareSpec> scraper) {
        int weight = Math.max(1, scraper.getAmountTasks());
        ScraperProgress progress = scraperProgress.get(scraper.id());
        if (progress != null) progress.start();
        setStatus("Scraper: " + scraper.baseURL() + " / " + scraper.id());

        try {
            long start = System.currentTimeMillis();
            AtomicLong counter = new AtomicLong();

            Set scrapedSpecs = scraper.downloadWebsites()
                    .map(page -> {
                        updateCurrentPage(scraper, page.singlePageCandidate().urls());
                        try {
                            var map = scraper.extract(page);
                            if (map == null) return null;
                            var result = scraper.parse(map, this::callScrapeEvent);
                            result.ifPresent(hardwareSpec -> {
                                scraper.markProcessed(page);
                                setStatus("Scraper: " + hardwareSpec.getMpnsSorted().getFirst()
                                        + " / " + scraper.id() + " [" + counter.getAndIncrement() + "]");
                            });
                            return result.orElse(null);
                        } catch (Throwable t) {
                            progressError(scraper, t);
                            LOGGER.log(Level.SEVERE, "Page processing failed", t);
                            return null;
                        } finally {
                            if (progress != null) progress.pageFinished();
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(h -> !h.getModel().isBlank())
                    .collect(Collectors.toSet());

            hardwareSpecService.onScrapeMulti(scrapedSpecs);

            LOGGER.info("Scraper " + scraper.id() + " finished in "
                    + (System.currentTimeMillis() - start) + " ms");
        } catch (Throwable t) {
            progressError(scraper, t);
            LOGGER.log(Level.SEVERE, "Scraper crashed: " + scraper.id(), t);
        } finally {
            if (progress != null) progress.finish();
            stepDone(weight);
        }
    }

    /* ------------------------------------------------------------
     * Helpers
     * ------------------------------------------------------------ */

    private void stepDone(int weight) {
        int done = doneTasksWeighted.addAndGet(weight);
        double p = Math.max(0, Math.min(1, done / (double) totalTasksWeighted));
        progress01.set(p);
    }

    private void setStatus(String msg) {
        statusMessage.set(msg);
    }

    public List<HardwareAdminDtos.ScraperStatus> getScraperStatuses() {
        return scraperProgress.values().stream()
                .sorted(Comparator.comparing(ScraperProgress::id))
                .map(ScraperProgress::snapshot)
                .toList();
    }

    private int safeTaskEstimate(ComponentWebScraper<?> scraper) {
        try {
            return Math.max(1, scraper.getAmountTasks());
        } catch (Exception ignored) {
            return 1;
        }
    }

    private void updateCurrentPage(ComponentWebScraper<?> scraper, Set<String> urls) {
        ScraperProgress progress = scraperProgress.get(scraper.id());
        String url = urls == null ? null : urls.stream().findFirst().orElse(null);
        if (progress != null) progress.pageStarted(url);
        setStatus("Scraper: " + scraper.baseURL() + " / " + scraper.id()
                + (url == null ? "" : " → " + url));
    }

    private void progressError(ComponentWebScraper<?> scraper, Throwable error) {
        ScraperProgress progress = scraperProgress.get(scraper.id());
        if (progress != null) progress.error(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
    }

    private static final class ScraperProgress {
        private final String id;
        private final String baseUrl;
        private final int estimatedPages;
        private volatile boolean running;
        private volatile String currentUrl;
        private volatile int processedPages;
        private volatile String message = "Wartet auf nächsten Lauf";
        private volatile String lastError;
        private volatile Instant startedAt;
        private volatile Instant finishedAt;

        private ScraperProgress(String id, String baseUrl, int estimatedPages) {
            this.id = id;
            this.baseUrl = baseUrl;
            this.estimatedPages = estimatedPages;
        }

        String id() { return id; }
        void resetForRun() { running = false; currentUrl = null; processedPages = 0; lastError = null; finishedAt = null; message = "Wartet auf nächsten Lauf"; }
        void start() { running = true; startedAt = Instant.now(); message = "Scraper gestartet"; }
        void pageStarted(String url) { currentUrl = url; message = url == null ? "Seite wird geladen" : "Lade " + url; }
        void pageFinished() { processedPages++; }
        void error(String error) { lastError = error; message = "Fehler beim Verarbeiten der aktuellen Seite"; }
        void finish() { running = false; currentUrl = null; finishedAt = Instant.now(); message = lastError == null ? "Abgeschlossen" : "Mit Fehlern abgeschlossen"; }
        HardwareAdminDtos.ScraperStatus snapshot() { return new HardwareAdminDtos.ScraperStatus(id, baseUrl, running, currentUrl, processedPages, estimatedPages, message, lastError, startedAt, finishedAt); }
    }

    private <H extends HardwareSpec<H>> void callScrapeEvent(H hardwareSpec) {
        for (ComponentWebScraper.ScrapeListener<HardwareSpec<?>> l : scrapeListeners) {
            try {
                l.onScrape(hardwareSpec);
                hardwareSyncService.addToSyncQueue(hardwareSpec);
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "ScrapeListener failed", t);
            }
        }
    }

    private String register(String source, String model, Optional<? extends HardwareSpec<?>> spec) {
        String safe = model.replaceAll("[\\\\/:*?\"<>|]", "_");

        productRegistryService.registerProductFromSearchResult(
                ProductSearchResultDTO.builder()
                        .title(safe)
                        .eans(spec.map(HardwareSpec::getEANs).orElse(Set.of()))
                        .mpns(spec.map(HardwareSpec::getMPNs).orElse(Set.of()))
                        .build(),
                source
        );

        productRegistryService.register(ProductIdentifier.IdentifierType.TITLE, source);
        return safe;
    }

    public void addListener(ComponentWebScraper.ScrapeListener<HardwareSpec<?>> l) {
        scrapeListeners.add(l);
    }
}
