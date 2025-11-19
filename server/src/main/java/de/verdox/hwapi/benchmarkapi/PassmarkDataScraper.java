package de.verdox.hwapi.benchmarkapi;

import de.verdox.hwapi.configuration.DataStorage;
import de.verdox.hwapi.io.api.selenium.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.MalformedURLException;
import java.time.Duration;

public class PassmarkDataScraper {
    private final SeleniumBasedWebScraper scraper = new SeleniumBasedWebScraper("passmark", new FScrapingCache(), new CookieJar(DataStorage.resolve("scraping")));

    public void tryScrapeCPUData(CPUDataConsumer consumer) throws MalformedURLException, SeleniumBasedWebScraper.ChallengeFoundException {
        Document document = scraper.fetch("cpubenchmark.net", "CPU-Benchmark-Data-Scraper", "https://www.cpubenchmark.net/CPU_mega_page.html",
                new FetchOptions()
                        .setBeforeSaveOperation(preparePageBeforeSave()));

        var foundTable = document.selectFirst("table.dataTable-blue.dataTable.no-footer");
        if (foundTable == null) {
            return;
        }

        var tBody = foundTable.selectFirst("tbody");
        if (tBody == null) {
            return;
        }
        for (Element tr : tBody.select("tr")) {
            String modelName = tr.selectFirst("a").text();
            Elements elements = tr.select("td");

            double cpuMark = tryParseNumberSafe(elements.get(3).text());
            double threadMark = tryParseNumberSafe(elements.get(4).text());
            consumer.consume(modelName, cpuMark, threadMark);
        }
    }

    public void tryScrapeGPUData(GPUDataConsumer consumer) throws MalformedURLException, SeleniumBasedWebScraper.ChallengeFoundException {
        Document document = scraper.fetch("videocardbenchmark.net", "GPU-Benchmark-Data-Scraper", "https://www.videocardbenchmark.net/GPU_mega_page.html",
                new FetchOptions()
                        .setBeforeSaveOperation(preparePageBeforeSave()));

        var foundTable = document.selectFirst("table.dataTable-blue.dataTable.no-footer");
        if (foundTable == null) {
            return;
        }

        var tBody = foundTable.selectFirst("tbody");
        if (tBody == null) {
            return;
        }
        for (Element tr : tBody.select("tr")) {
            String modelName = tr.selectFirst("a").text();
            Elements elements = tr.select("td");
            double g3DMark = tryParseNumberSafe(elements.get(2).text());
            double g2DMark = tryParseNumberSafe(elements.get(3).text());
            consumer.consume(modelName, g3DMark, g2DMark);
        }
    }

    private static double tryParseNumberSafe(String text) {
        try {
            return Double.parseDouble(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static BeforeSaveOperation preparePageBeforeSave() {
        return driver -> {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            wait.until(webDriver -> ((JavascriptExecutor) webDriver).executeScript("return jQuery.active === 0;"));
            WebElement dropdown = driver.findElement(By.cssSelector("select.form-control.input-sm"));
            Select select = new Select(dropdown);
            select.selectByValue("-1");
            wait.until(webDriver -> ((JavascriptExecutor) webDriver).executeScript("return jQuery.active === 0;"));
        };
    }

    public interface CPUDataConsumer {
        void consume(String cpuModel, double cpuMarkScore, double threadMarkScore);
    }

    public interface GPUDataConsumer {
        void consume(String gpuChip, double g3DMark, double g2DMark);
    }
}
