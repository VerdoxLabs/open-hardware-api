package de.verdox.openhardwareapi.io.api;


import de.verdox.openhardwareapi.component.service.ScrapingService;
import de.verdox.openhardwareapi.util.SeleniumUtil;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;

public class SeleniumBasedWebScraper implements BasicWebScraper {
    private static WebDriver webDriver;

    public static void cleanup() {
        if (webDriver != null) {
            ScrapingService.LOGGER.log(Level.INFO, "SeleniumBasedWebScraper.cleanup");
            webDriver.quit();
        }
    }

    private final ScrapingCache cache;
    private final CookieJar cookieJar;
    @Setter
    private Predicate<Document> isChallengePage;
    private boolean needsBuildup = true;


    public SeleniumBasedWebScraper(ScrapingCache scrapingCache, CookieJar cookieJar, Predicate<Document> isChallengePage) {
        this.cache = scrapingCache;
        this.cookieJar = cookieJar;
        this.isChallengePage = isChallengePage;

        if (webDriver == null) {
            WebDriverManager.chromedriver().setup();
            ChromeOptions opts = new ChromeOptions();
            opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
            opts.setExperimentalOption("useAutomationExtension", false);
            opts.addArguments(arguments());
            try {
                webDriver = SeleniumUtil.create(opts);
                if (webDriver instanceof ChromeDriver chromeDriver) {
                    chromeDriver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", Map.of(
                            "source", "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});"
                    ));
                }
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public SeleniumBasedWebScraper(ScrapingCache scrapingCache, CookieJar cookieJar) {
        this(scrapingCache, cookieJar, document -> false);
    }

    @Override
    public final Document scrape(String url) {
        return scrapeLive(url);
    }

    /**
     * Holt Dokument aus Cache (wenn frisch), sonst via Selenium und cached es.
     */
    public final Document scrapeWithCache(String url, Duration ttl) {
        var cached = cache.fetchOrCache(url, ttl, () -> scrapeLiveHtml(url));
        Document document = Jsoup.parse(cached.rawHtml(), url);
        if (isChallengePage.test(document)) {
            cache.delete(url);
            ScrapingService.LOGGER.log(Level.WARNING, "Challenge page found. Deleting from cache [" + url + "]");
            return Document.createShell(url);
        }
        return document;
    }

    /**
     * Nur HTML via Selenium (mit Cookie-Unterstützung)
     */
    private String scrapeLiveHtml(String url) {
        URI uri = URI.create(url);
        String base = uri.toASCIIString();

        // 1) Base aufrufen, Cookies einspielen
        webDriver.navigate().to(base);
        Set<Cookie> cookies = cookieJar.load(uri);
        for (Cookie c : cookies) {
            try {
                webDriver.manage().addCookie(c);
            } catch (Exception ignored) {
            }
        }

        if (url.isBlank()) {
            throw new RuntimeException("Scraping URL cannot be blank!");
        }

        // 2) Zielseite laden
        webDriver.navigate().to(url);
        waitReady(webDriver);

        String html = webDriver.getPageSource();
        if (html == null) html = "";

        // 3) Cookies zurückspeichern
        cookieJar.save(uri, new LinkedHashSet<>(webDriver.manage().getCookies()));
        return html;
    }

    /**
     * Für legacy scrape()
     */
    private Document scrapeLive(String url) {
        String html = scrapeLiveHtml(url);
        return Jsoup.parse(html, url);
    }

    private void waitReady(WebDriver driver) {
        long end = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
        JavascriptExecutor js = (JavascriptExecutor) driver;
        while (System.currentTimeMillis() < end) {
            try {
                Object s = js.executeScript("return document.readyState");
                if ("complete".equals(String.valueOf(s))) return;
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static String[] arguments() {
        return new String[]{
                "--disable-blink-features=AutomationControlled",
/*                "--user-data-dir=" + "/home/seluser/profile",
                "--profile-directory=Default",*/
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--window-size=1920,1080",
                "--lang=de-DE",
                "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Safari/537.36"
        };
    }

    public void closeSession() {
        if (webDriver != null) {
            try {
                //webDriver.close();
            } catch (Exception ignored) {

            } finally {
                needsBuildup = true;
            }
        }
    }
}
