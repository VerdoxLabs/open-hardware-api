package de.verdox.openhardwareapi.io.websites.computersalg;

import de.verdox.openhardwareapi.io.api.ComponentWebScraper;
import de.verdox.openhardwareapi.io.api.WebsiteScrapingStrategy;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ComputerSalgScrapingStrategy implements WebsiteScrapingStrategy {
    @Override
    public void extractMultiPageURLs(String currentURL, Document page, Queue<String> multiPageURLs) {
        var progress = page.selectFirst("progress.v-pagination__progress");
        if (progress == null) return;
        int value = Integer.parseInt(progress.attr("value"));
        int max = Integer.parseInt(progress.attr("max"));

        if (value <= 23) {
            int maxPage = (int) Math.ceil(1f * max / value);

            for (int i = 1; i <= maxPage; i++) {
                if (currentURL.contains("page=")) {
                    multiPageURLs.add(currentURL.split("page=")[0] + "page=" + i);
                } else {
                    multiPageURLs.add(currentURL + "?page=" + i);
                }
            }
        }
    }

    @Override
    public void extractSinglePagesURLs(String currentUrl, Document page, Set<String> singlePageURLs) {
        for (Element cardContent : page.select("div.m-product-card")) {
            var a = cardContent.selectFirst("a");
            if (a != null) {
                singlePageURLs.add("https://" + ComponentWebScraper.topLevelHost(currentUrl) + a.attr("href"));
            }
        }
    }

    private static final Pattern PRODUCER_PATTERN = Pattern.compile("Produzent:\\s*([^|]+)");
    private static final Pattern MODEL_PATTERN = Pattern.compile("Modell\\s*nummer:\\s*([^|]+)");
    private static final Pattern EAN_PATTERN = Pattern.compile("EAN:\\s*([^|]+)");

    @Override
    public Map<String, List<String>> extractSpecMap(Document page) throws Throwable {

        Map<String, List<String>> specs = new HashMap<>();

        var headline = page.selectFirst("div.v-product-headline");
        if (headline != null) {
            var h1 = headline.selectFirst("h1");
            if (h1 != null) {
                specs.put("model", Arrays.stream(h1.text().split(" - |, ")).toList());
            }

        }

        Element details = page.selectFirst("span.v-product-details__product-number");
        if (details != null) {

            String producer = extract(details.text(), PRODUCER_PATTERN);
            String mpn = extract(details.text(), MODEL_PATTERN);
            String ean = extract(details.text(), EAN_PATTERN);

            if(!producer.isBlank()) {
                specs.put("manufacturer", List.of(producer));
            }
            if(!mpn.isBlank()) {
                specs.put("MPN", List.of(mpn));
            }
            if(!ean.isBlank()) {
                specs.put("EAN", List.of(ean));
            }
        }
        return specs;
    }

    private static String extract(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }
}
