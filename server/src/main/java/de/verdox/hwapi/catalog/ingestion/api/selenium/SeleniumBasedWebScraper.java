package de.verdox.hwapi.catalog.ingestion.api.selenium;

import de.verdox.hwapi.catalog.ingestion.ScrapingService;
import de.verdox.hwapi.catalog.ingestion.api.BasicWebScraper;
import de.verdox.hwapi.catalog.ingestion.api.webscraper.WebScraperApiClient;
import de.verdox.hwapi.catalog.ingestion.api.selenium.SeleniumUtil;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Selenium-basierter Scraper mit ID-Ebene:
 * /data/pages/&lt;domain&gt;/&lt;id&gt;/&lt;hash&gt;.html
 * <p>
 * ID ist Pflicht und darf nie null/leer sein.
 * Dateiname ist Hash der kanonisierten URL (keine täglichen Re-Scrapes notwendig).
 */
@Slf4j
public class SeleniumBasedWebScraper implements BasicWebScraper {
    private static volatile WebScraperApiClient webScraperApiClient;

    public static void useLegacySelenium() {
        webScraperApiClient = null;
    }

    public static void useWebScraperApi(WebScraperApiClient client) {
        webScraperApiClient = Objects.requireNonNull(client, "client");
    }

    /**
     * Ein globaler (geteilter) Treiber – bei Bedarf kannst du das auf einen Pool umstellen.
     */
    @Setter
    @Getter
    private WebDriver webDriver;

    private final String id;
    private final ScrapingCache cache;
    private final CookieJar cookieJar;

    /**
     * Erkennung von Bot-/Challenge-Seiten (z. B. Cloudflare), (urls, doc) -> true wenn Challenge.
     */
    @Setter
    private BiPredicate<String, Document> isChallengePage;
    /**
     * Steuerung, ob eine Seite persistiert werden soll, (urls, doc) -> true = speichern.
     */
    @Setter
    private BiPredicate<String, Document> shouldSavePage;
    /** Minimum interval between actual network requests for this domain. Cache hits never wait. */
    @Setter
    private Duration minLiveRequestInterval = Duration.ZERO;

    public SeleniumBasedWebScraper(String id, ScrapingCache cache,
                                   CookieJar cookieJar,
                                   BiPredicate<String, Document> isChallengePage,
                                   BiPredicate<String, Document> shouldSavePage) {
        this.id = id;
        this.cache = Objects.requireNonNull(cache, "cache");
        this.cookieJar = Objects.requireNonNull(cookieJar, "cookieJar");
        this.isChallengePage = isChallengePage;
        this.shouldSavePage = shouldSavePage;
    }

    public SeleniumBasedWebScraper(String id, ScrapingCache cache,
                                   CookieJar cookieJar) {
        this(id, cache, cookieJar, (a, b) -> false, (a, b) -> true);
    }


    public Document fetch(String domain, String id, String url, Duration ttl) throws MalformedURLException, ChallengeFoundException {
        return fetch(domain, id, url, new FetchOptions().setTtl(ttl));
    }

    public String getPathInCache(String domain, String url) {
        PageKey key = new PageKey(domain, id, url);
        return ScrapingPaths.fileFor(key).toAbsolutePath().toString();
    }

    public Document fetchWithInteraction(String domain,
                                         String id,
                                         String url,
                                         Duration ttl,
                                         Consumer<WebDriver> interaction)
            throws MalformedURLException, ChallengeFoundException {

        FetchOptions options = new FetchOptions().setTtl(ttl);
        return fetchWithInteraction(domain, id, url, options, interaction);
    }

    /**
     * Variante von fetch(...), die es erlaubt, zur Laufzeit mit dem WebDriver zu interagieren
     * (z.B. Buttons klicken, scrollen, "Load more" usw.), bevor der HTML-Snapshot
     * erstellt und durch die normale fetch-Logik (Challenge-Detection, Caching, HtmlSlimmer, etc.)
     * verarbeitet wird.
     *
     * Sämtliche bestehende Logik aus {@link #fetch(String, String, String, FetchOptions)}
     * bleibt hierbei vollständig erhalten.
     */
    public Document fetchWithInteraction(String domain,
                                         String id,
                                         String url,
                                         FetchOptions fetchOptions,
                                         Consumer<WebDriver> interaction)
            throws MalformedURLException, ChallengeFoundException {

        Objects.requireNonNull(fetchOptions, "fetchOptions must not be null");

        // Bisherige beforeSaveOperation merken (kann null sein)
        var originalBeforeSave = fetchOptions.getBeforeSaveOperation();

        // Neues beforeSaveOperation, das zuerst das alte, dann die Interaktion ausführt
        fetchOptions.setBeforeSaveOperation(driver -> {
            // 1) ursprüngliche Operation ausführen (falls vorhanden)
            if (originalBeforeSave != null) {
                try {
                    originalBeforeSave.beforeSave(driver);
                } catch (Throwable ex) {
                    log.warn("Error in original beforeSaveOperation for url {}: {}", url, ex.toString(), ex);
                }
            }

            // 2) zusätzliche Interaktion (z.B. Pagination-Buttons klicken)
            if (interaction != null) {
                try {
                    interaction.accept(driver);
                } catch (Throwable ex) {
                    log.warn("Error in interaction callback for url {}: {}", url, ex.toString(), ex);
                }
            }
        });

        // Jetzt ganz normal die bestehende fetch-Logik verwenden
        return fetch(domain, id, url, fetchOptions);
    }

    /**
     * Haupteinstieg: Seite unter einer festen ID-Gruppe fetchen.
     *
     * @param domain z. B. "mindfactory.de"
     * @param id     Gruppierungs-ID (niemals null/leer)
     * @param url    Ziel-URL
     * @param fetchOptions Fetch options
     */
    public Document fetch(String domain, String id, String url, FetchOptions fetchOptions)
            throws MalformedURLException, ChallengeFoundException {

        validateDomain(domain);
        validateId(id);

        String canonUrl = ScrapingPaths.urlCanonical(url);
        PageKey key = new PageKey(domain, id, canonUrl);

        // 1) Cache lesen – roh und geprüft
        Optional<String> cachedRaw = cache.loadHtml(key);

        Optional<String> cachedFresh = cachedRaw.flatMap(html ->
                isFreshEnough(key, fetchOptions.getTtl()) ? Optional.of(html) : Optional.empty()
        );

        String staleHtml = cachedRaw.orElse(null);

        // 1a) Frischer Cache vorhanden → nur WENN "gut", dann zurückgeben
        if (cachedFresh.isPresent()) {
            Document cachedDocument = Jsoup.parse(cachedFresh.get(), baseUri(domain));

            if (isChallengePage != null && isChallengePage.test(canonUrl, cachedDocument)) {
                ScrapingService.LOGGER.log(Level.FINE,
                        "Cached page is a challenge page, ignoring but KEEPING cache: " + canonUrl);
            }
            else if (shouldSavePage != null && !shouldSavePage.test(canonUrl, cachedDocument)) {
                ScrapingService.LOGGER.log(Level.FINE,
                        "Cached page should not be used (cookie/login), ignoring but KEEPING cache: " + canonUrl);
            }
            else {
                // Cache ist gut → direkt zurück
                return cachedDocument;
            }
        }

        // 1b) Nur Cache-Modus
        if (fetchOptions.isSkipIfNotCache()) {
            if (staleHtml != null) {
                ScrapingService.LOGGER.log(Level.FINE,
                        "skipIfNotCache=true → using stale cache for: " + canonUrl);
                return Jsoup.parse(staleHtml, baseUri(domain));
            }
            ScrapingService.LOGGER.log(Level.INFO,
                    "skipIfNotCache=true, but no cache present → returning shell: " + canonUrl);
            return Document.createShell(url);
        }

        // 1c) Domain aktuell als OFFLINE geflagged?
        if (isDomainOffline(domain)) {
            //ScrapingService.LOGGER.log(Level.INFO, "Domain currently flagged OFFLINE (TTL) → skipping live fetch: " + domain);

            if (staleHtml != null) {
                return Jsoup.parse(staleHtml, baseUri(domain));
            }
            else {
                ScrapingService.LOGGER.log(Level.INFO, "No cache entry found for "+url);
            }
            return Document.createShell(url);
        }

        waitForLiveRequestSlot(domain);

        // 2) Live-Laden – Headless oder Selenium – mit Fallback
        Document doc;
        String html;

        if (webScraperApiClient != null) {
            try {
                ScrapingService.LOGGER.log(Level.FINE,
                        "Cache miss → WEBSCRAPER API fetch: " + canonUrl + " [" + domain + ":" + id + "]");
                html = webScraperApiClient.fetchHtml(canonUrl);
                html = HtmlSlimmer.slimHtml(html, canonUrl, new HtmlSlimmer.Options());
                doc = Jsoup.parse(html, baseUri(domain));
            } catch (RuntimeException ex) {
                ScrapingService.LOGGER.log(Level.WARNING,
                        "Webscraper API fetch failed for " + canonUrl + " – using stale cache (if any)", ex);
                if (isOfflineException(ex)) {
                    markDomainOffline(domain);
                }
                if (staleHtml != null) {
                    return Jsoup.parse(staleHtml, baseUri(domain));
                }
                throw ex;
            }
        }
        else if (fetchOptions.isTryHeadlessFirst()) {
            try {
                ScrapingService.LOGGER.log(Level.FINE,
                        "Cache miss → HEADLESS fetch: " + canonUrl + " [" + domain + ":" + id + "]");
                doc = fetchHeadless(canonUrl);
                html = doc.html();
            } catch (IOException e) {
                // Headless fehlgeschlagen → einmalig in normalen Selenium-Flow wechseln
                return fetch(domain, id, url, fetchOptions.setTryHeadlessFirst(false));
            }
        }
        else {
            ScrapingService.LOGGER.log(Level.FINE,
                    "Cache miss → SELENIUM fetch: " + canonUrl + " [" + domain + ":" + id + "]");

            try {
                html = fetchWithSelenium(canonUrl, fetchOptions);
                html = HtmlSlimmer.slimHtml(html, canonUrl, new HtmlSlimmer.Options());
                doc = Jsoup.parse(html, baseUri(domain));
            }
            catch (RuntimeException ex) {
                ScrapingService.LOGGER.log(Level.WARNING,
                        "Live Selenium fetch failed for " + canonUrl + " – using stale cache (if any)", ex);

                // Domain als OFFLINE markieren (mit TTL 1h), wenn "offline-artige" Exception
                if (isOfflineException(ex)) {
                    markDomainOffline(domain);
                }

                if (staleHtml != null) {
                    return Jsoup.parse(staleHtml, baseUri(domain));
                }

                // Kein Cache → Fehler weiterwerfen
                throw ex;
            }
        }

        // 3) Challenge-Erkennung nach dem Laden
        if (isChallengePage != null && isChallengePage.test(canonUrl, doc)) {

            ScrapingService.LOGGER.log(Level.FINE,
                    "Challenge detected after load for: " + canonUrl);

            if (fetchOptions.isTryHeadlessFirst()) {
                return fetch(domain, id, url, fetchOptions.setTryHeadlessFirst(false));
            }

            // WICHTIG: Challenge → NICHT löschen! Alten Cache BEHALTEN.
            throw new ChallengeFoundException();
        }

        // 4) Prüfen ob wir die Seite speichern dürfen (z.B. keine Cookie-Wall)
        boolean isGoodPage = (shouldSavePage == null || shouldSavePage.test(canonUrl, doc));

        if (isGoodPage) {
            try {
                cache.saveHtml(key, html); // überschreibt alten Cache
            } catch (UncheckedIOException e) {
                ScrapingService.LOGGER.log(Level.SEVERE,
                        "Failed to persist HTML for: " + canonUrl + " [" + domain + ":" + id + "]", e);
            }
        }
        else {
            ScrapingService.LOGGER.log(Level.FINE,
                    "Page is not saveable (cookie/login) → keeping old cache for: " + canonUrl);
        }

        return doc;
    }



    /* ---------------------------------------------------------
       Driver-Handling
       --------------------------------------------------------- */

    private Document fetchHeadless(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Safari/537.36")
                .timeout(10_000)
                .get();
    }

    private String fetchWithSelenium(String url, FetchOptions fetchOptions) throws MalformedURLException {
        ensureDriver();
        tryRestoreCookies(url);

        WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));

        try {
            webDriver.get(url);
        } catch (org.openqa.selenium.NoSuchSessionException ex) {
            // Session war tot – neu aufsetzen und einmal wiederholen
            restartDriver();
            webDriver.get(url);
        }
        try {
            fetchOptions.getBeforeSaveOperation().beforeSave(webDriver);
        }
        catch (Throwable ex) {
            ex.printStackTrace();
        }
        wait.until(driver -> {
            try {
                return ((JavascriptExecutor)driver).executeScript("return jQuery.active === 0;");
            }
            catch (Exception ignored) {
                return new Object();
            }
        });
        String html = webDriver.getPageSource();
        tryStoreCookies(url);
        return html;
    }

    // Neu: zentraler Options-Builder (damit restart/ensure gleich sind)
    private ChromeOptions buildChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments(
                "--disable-blink-features=AutomationControlled",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--window-size=1920,1080",
                "--lang=de-DE",
                "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Safari/537.36"
        );
        return options;
    }

    // Neu: Session-Alive-Check
    private static boolean isSessionAlive(WebDriver d) {
        if (d == null) return false;
        try {
            if (d instanceof org.openqa.selenium.remote.RemoteWebDriver rwd) {
                return rwd.getSessionId() != null;
            }
            // Fallback-Heuristik: ein harmloser Befehl
            d.getTitle(); // kann NoSuchSessionException werfen
            return true;
        } catch (org.openqa.selenium.NoSuchSessionException ex) {
            return false;
        } catch (Throwable t) {
            // andere Fehler nicht als "tot" interpretieren
            return true;
        }
    }

    public synchronized WebDriver ensureDriver() throws MalformedURLException {
        if (webScraperApiClient != null) {
            throw new IllegalStateException("A Selenium WebDriver is unavailable while the Webscraper API backend is active");
        }
        if (!isSessionAlive(webDriver)) {
            try {
                if (webDriver != null) webDriver.quit();
            } catch (Exception ignore) {
            }
            webDriver = null;
            try {
                SeleniumUtil.cleanup(id);
            } catch (Exception ignore) {
            }
            webDriver = SeleniumUtil.create(id, buildChromeOptions());

            // ⬇️ HIER: globale Timeouts setzen
            try {
                webDriver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
                webDriver.manage().timeouts().scriptTimeout(Duration.ofSeconds(60));
            } catch (Exception e) {
                log.warn("Could not configure timeouts on WebDriver", e);
            }

            if (log.isInfoEnabled()) {
                if (webDriver instanceof RemoteWebDriver rwd) {
                    log.info("Initialized WebDriver: {}", rwd.getCapabilities());
                } else {
                    log.info("Initialized WebDriver: {}", webDriver);
                }
            }
        }
        return webDriver;
    }


    public synchronized void restartDriver() throws MalformedURLException {
        if (webScraperApiClient != null) {
            return;
        }
        ScrapingService.LOGGER.log(Level.INFO, "Restarting WebDriver for id={0}", id);
        try {
            if (webDriver != null) webDriver.quit();
        } catch (Exception ignore) {
        }
        webDriver = null;
        try {
            SeleniumUtil.cleanup(id);
        } catch (Exception ignore) {
        }
        webDriver = SeleniumUtil.create(id, buildChromeOptions());
        if (log.isInfoEnabled()) {
            if (webDriver instanceof RemoteWebDriver rwd) {
                log.info("Reinitialized WebDriver: {}", rwd.getCapabilities());
            } else {
                log.info("Reinitialized WebDriver: {}", webDriver);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        cleanup();
    }

    public static void cleanup() {
        SeleniumUtil.cleanUp();
    }

    /* ---------------------------------------------------------
       Cookies
       --------------------------------------------------------- */

    private void tryRestoreCookies(String url) {
        if (cookieJar == null) return;
        String domain = domainFromUrl(url);
        try {
            //cookieJar.applyTo(webDriver, domain);
        } catch (Exception e) {
            log.debug("Cookie restore failed for domain {}", domain, e);
        }
    }

    private void tryStoreCookies(String url) {
        if (cookieJar == null) return;
        String domain = domainFromUrl(url);
        try {
            //cookieJar.captureFrom(webDriver, domain);
        } catch (Exception e) {
            log.debug("Cookie capture failed for domain {}", domain, e);
        }
    }

    private static String domainFromUrl(String url) {
        try {
            URI u = new URI(url);
            return u.getHost() != null ? u.getHost() : url;
        } catch (URISyntaxException e) {
            return url;
        }
    }

    /* ---------------------------------------------------------
       Helpers / Policies
       --------------------------------------------------------- */

    /**
     * Einfache TTL-Policy:
     * - null oder Duration.ZERO → immer frisch
     * - ansonsten: hier könntest du z. B. File-Zeitstempel im Cache prüfen.
     * Da {@link ScrapingCache} abstrakt ist, wird standardmäßig "true" geliefert.
     */
    /**
     * TTL-Policy:
     * - null, ZERO oder negativ ⇒ Cache gilt immer als frisch (kein Re-Fetch).
     * - sonst: prüfe mtime der Cache-Datei gegen now()-ttl.
     */
    private boolean isFreshEnough(PageKey key, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) return true;
        try {
            Path file = fileFor(key);
            Path cachedFile = Files.exists(file)
                    ? file
                    : file.resolveSibling(file.getFileName() + ".gz");
            if (!Files.exists(cachedFile)) return false;
            FileTime lastModified = Files.getLastModifiedTime(cachedFile);
            Instant cutoff = Instant.now().minus(ttl);
            return lastModified.toInstant().isAfter(cutoff);
        } catch (Exception e) {
            // Defensive: bei Fehler lieber als „nicht frisch“ behandeln → neu laden
            return false;
        }
    }

    private void waitForLiveRequestSlot(String domain) {
        DomainRateLimiter.await(domain, minLiveRequestInterval);
    }

    /**
     * Pfad der Cache-Datei (zentral, falls sich die Logik in ScrapingPaths mal ändert).
     */
    private static Path fileFor(PageKey key) {
        return ScrapingPaths.fileFor(key.domain(), key.id(), key.url());
    }

    /**
     * Alte/ungültige Cache-Datei entfernen (ohne harte Fehler).
     */
    private void deleteCached(PageKey key) {
        try {
            Files.deleteIfExists(fileFor(key));
        } catch (Exception ignored) {
        }
    }

    private static String baseUri(String domain) {
        return "https://" + domain + "/";
    }

    private static void validateDomain(String domain) {
        if (domain == null || domain.isBlank())
            throw new IllegalArgumentException("domain must not be null/blank");
        if (domain.contains("/") || domain.contains(".."))
            throw new IllegalArgumentException("invalid domain path segment: " + domain);
    }

    private static void validateId(String id) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (id.trim().isEmpty()) throw new IllegalArgumentException("id must not be empty");
    }

    @Override
    public Document scrape(String url) {
        return null;
    }

    public static class ChallengeFoundException extends IOException {
        public ChallengeFoundException() {
        }

        public ChallengeFoundException(String message) {
            super(message);
        }

        public ChallengeFoundException(String message, Throwable cause) {
            super(message, cause);
        }

        public ChallengeFoundException(Throwable cause) {
            super(cause);
        }
    }

    // TTL-Cache für "offline" Domains (Top-Level-Domain / Hostname)
    private static final java.util.Map<String, Instant> OFFLINE_DOMAINS = new java.util.concurrent.ConcurrentHashMap<>();

    private static final Duration OFFLINE_TTL = Duration.ofHours(1);

    /**
     * Prüft, ob eine Domain aktuell als OFFLINE geflagged ist.
     * Abgelaufene Einträge werden dabei entfernt.
     */
    private boolean isDomainOffline(String domain) {
        Instant until = OFFLINE_DOMAINS.get(domain);
        if (until == null) {
            return false;
        }
        if (Instant.now().isAfter(until)) {
            // TTL abgelaufen → Flag löschen
            OFFLINE_DOMAINS.remove(domain);
            return false;
        }
        return true;
    }

    /**
     * Markiert eine Domain für OFFLINE (für OFFLINE_TTL Dauer).
     */
    private void markDomainOffline(String domain) {
        Instant until = Instant.now().plus(OFFLINE_TTL);
        OFFLINE_DOMAINS.put(domain, until);
        ScrapingService.LOGGER.log(Level.INFO,
                "Marking domain as OFFLINE for " + OFFLINE_TTL.toMinutes() + " minutes: " + domain);
    }

    /**
     * Heuristik, ob eine Exception "offline-artig" ist.
     * Kannst du nach Bedarf verfeinern.
     */
    private boolean isOfflineException(Throwable ex) {
        if (ex instanceof WebScraperApiClient.WebScraperApiException) {
            return true;
        }
        if (ex instanceof org.openqa.selenium.TimeoutException) {
            return true;
        }
        if (ex instanceof org.openqa.selenium.WebDriverException) {
            // z.B. netzwerk-/verbindungsbedingte Fehler
            return true;
        }
        return false;
    }
}
