package de.verdox.hwapi.hardwareapi.scraping.websites.intel;

import de.verdox.hwapi.hardwareapi.scraping.api.WebsiteScrapingStrategy;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;

public class IntelScrapingStrategy implements WebsiteScrapingStrategy {

    @Override
    public void extractMultiPageURLs(String currentURL, Document page, Queue<MultiPageCandidate> multiPageURLs) {
        if (!currentURL.contains("#@")) {
            return;
        }
        String currentLabel = currentURL.split("#@")[1];

        var foundElement = page.selectFirst("div.products.processors[data-parent-panel-key='" + currentLabel + "']");
        if (foundElement == null) {
            return;
        }
        for (Element a : foundElement.select("a")) {
            String url = a.attr("href");
            if (url.isBlank()) {
                continue;
            }
            multiPageURLs.add(new MultiPageCandidate(url));
        }
    }

    @Override
    public void extractSinglePagesURLs(String currentUrl, Document page, Set<SinglePageCandidate> singlePageURLs) {
        var table = page.selectFirst("table.table.table-sorter.sorting.tablesorter.tablesorter-default");
        if (table == null) {
            return;
        }
        for (Element a : table.select("a")) {
            String url = a.attr("href");
            singlePageURLs.add(new SinglePageCandidate(url, url.replace("specifications.html", "ordering.html")));
        }
    }

    @Override
    public Map<String, List<String>> extractSpecMap(Document document) throws Throwable {
        Map<String, List<String>> specs = new HashMap<>();

        for (Element techSection : document.select("div.tech-section")) {
            for (Element sectionRow : techSection.select("div.row.tech-section-row")) {
                var label = sectionRow.selectFirst("div.col-6.col-lg-6.tech-label");
                if(label == null) {
                    continue;
                }
                String key = sectionRow.selectFirst("div.col-6.col-lg-6.tech-label").selectFirst("span").text();

                var data = sectionRow.selectFirst("div.col-6.col-lg-6.tech-data");
                if(data == null) {
                    continue;
                }
                String value = "";
                if(data.selectFirst("span") != null) {
                    value = data.selectFirst("span").text();
                }
                else if(data.selectFirst("a") != null) {
                    value = data.selectFirst("a").text();
                }
                else {
                    continue;
                }
                String[] values = value.split(", ");

                if(specs.containsKey(key)) {
                    specs.get(key).addAll(Arrays.asList(values));
                }
                else {
                    specs.put(key, new ArrayList<>(List.of(values)));
                }
            }
        }
        return specs;
    }
}
