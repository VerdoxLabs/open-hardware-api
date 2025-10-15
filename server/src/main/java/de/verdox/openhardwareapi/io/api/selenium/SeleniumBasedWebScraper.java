package de.verdox.openhardwareapi.io.api.selenium;

import de.verdox.openhardwareapi.component.service.ScrapingService;
import de.verdox.openhardwareapi.io.api.BasicWebScraper;
import de.verdox.openhardwareapi.util.SeleniumUtil;
import jakarta.annotation.PreDestroy;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
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
    /**
     * Ein globaler (geteilter) Treiber – bei Bedarf kannst du das auf einen Pool umstellen.
     */
    @Setter
    private WebDriver webDriver;

    private String id;
    private final ScrapingCache cache;
    private final CookieJar cookieJar;

    /**
     * Erkennung von Bot-/Challenge-Seiten (z. B. Cloudflare), (url, doc) -> true wenn Challenge.
     */
    @Setter
    private BiPredicate<String, Document> isChallengePage;
    /**
     * Steuerung, ob eine Seite persistiert werden soll, (url, doc) -> true = speichern.
     */
    @Setter
    private BiPredicate<String, Document> shouldSavePage;

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


    /**
     * Haupteinstieg: Seite unter einer festen ID-Gruppe fetchen.
     *
     * @param domain z. B. "mindfactory.de"
     * @param id     Gruppierungs-ID (niemals null/leer)
     * @param url    Ziel-URL
     * @param ttl    Cache-Gültigkeit; {@code null} oder {@code Duration.ZERO} = Cache immer gültig
     */
    public Document fetch(String domain, String id, String url, Duration ttl) throws MalformedURLException, ChallengeFoundException {
        validateDomain(domain);
        validateId(id);
        String canonUrl = ScrapingPaths.urlCanonical(url);
        PageKey key = new PageKey(domain, id, canonUrl);

        // 1) Cache prüfen (TTL-Strategie kannst du bei Bedarf verfeinern – z. B. Sidecar-Timestamps im Cache)
        Optional<String> cached = cache.loadHtml(key)
                .flatMap(html -> isFreshEnough(key, ttl) ? Optional.of(html) : Optional.empty());

        if (cached.isPresent()) {
            Document cachedDocument = Jsoup.parse(cached.get(), baseUri(domain));

            if (isChallengePage != null && isChallengePage.test(canonUrl, cachedDocument)) {
                ScrapingService.LOGGER.log(Level.INFO, "Removing cached challenge page: " + canonUrl);

            }
            else if (shouldSavePage == null || !shouldSavePage.test(canonUrl, cachedDocument)) {
                ScrapingService.LOGGER.log(Level.INFO, "Removing page that should not be saved : " + canonUrl);
            }
            else {
                return cachedDocument;
            }
        }

        // 2) Live laden via Selenium
        ScrapingService.LOGGER.log(Level.FINER, "Cache miss → Selenium fetch: " + canonUrl + " [" + domain + ":" + id + "]");
        String html = fetchWithSelenium(canonUrl);
        html = HtmlSlimmer.slimHtml(html, canonUrl, new HtmlSlimmer.Options());

        Document doc = Jsoup.parse(html, baseUri(domain));

        if (isChallengePage != null && isChallengePage.test(canonUrl, doc)) {
            ScrapingService.LOGGER.log(Level.INFO, "Challenge page detected for URL: " + canonUrl);
            try {
                Thread.sleep(1000);
                HtmlSlimmer.slimHtml(webDriver.getPageSource(), canonUrl, new HtmlSlimmer.Options());
            } catch (InterruptedException e) {

            }
        }


        doc = Jsoup.parse(html, baseUri(domain));
        if (isChallengePage != null && isChallengePage.test(canonUrl, doc)) {
            ScrapingService.LOGGER.log(Level.INFO, "Challenge page detected for URL: " + canonUrl);
            throw new ChallengeFoundException();
        }

        if (shouldSavePage == null || shouldSavePage.test(canonUrl, doc)) {
            try {
                cache.saveHtml(key, html);
            } catch (UncheckedIOException e) {
                ScrapingService.LOGGER.log(Level.SEVERE, "Failed to persist HTML for: " + canonUrl + " [" + domain + ":" + id + "]", e);
            }
        }

        return doc;
    }

    /* ---------------------------------------------------------
       Driver-Handling
       --------------------------------------------------------- */

    private String fetchWithSelenium(String url) throws MalformedURLException {
        ensureDriver();
        // Cookies pro Domain wiederherstellen (falls CookieJar hinterlegt ist)
        tryRestoreCookies(url);

        webDriver.get(url);

        // Optional: auf DOM-Ready/Wartebedingungen warten (Explizit-Waits, JS readyState etc.)
        // new WebDriverWait(webDriver, Duration.ofSeconds(10)).until(...)

        String html = webDriver.getPageSource();

        // Cookies zurückspeichern
        tryStoreCookies(url);

        return html;
    }

    public synchronized WebDriver ensureDriver() throws MalformedURLException {
        if (webDriver != null) return webDriver;

        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        String[] args = new String[]{
                "--disable-blink-features=AutomationControlled",
/*                "--user-data-dir=" + "/home/seluser/profile",
                "--profile-directory=Default",*/
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--window-size=1920,1080",
                "--lang=de-DE",
                "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Safari/537.36"
        };
        options.addArguments(args);

        webDriver = SeleniumUtil.create(id,
                options
        );
        if (log.isInfoEnabled()) {
            if (webDriver instanceof RemoteWebDriver rwd) {
                log.info("Initialized WebDriver: {}", rwd.getCapabilities());
            } else {
                log.info("Initialized WebDriver: {}", webDriver);
            }
        }
        return webDriver;
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

    /**
     * Einfache TTL-Policy:
     * - null oder Duration.ZERO → immer frisch
     * - ansonsten: hier könntest du z. B. File-Zeitstempel im Cache prüfen.
     * Da {@link ScrapingCache} abstrakt ist, wird standardmäßig "true" geliefert.
     */
    private boolean isFreshEnough(PageKey key, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) return true;
        // TODO: Implementiere eine echte TTL-Prüfung (z. B. über Sidecar-Metadaten im Cache).
        return true;
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
}
