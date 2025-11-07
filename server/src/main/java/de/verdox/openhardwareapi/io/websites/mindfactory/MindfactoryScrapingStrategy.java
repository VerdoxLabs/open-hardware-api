package de.verdox.openhardwareapi.io.websites.mindfactory;

import de.verdox.openhardwareapi.io.api.WebsiteScrapingStrategy;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;

public class MindfactoryScrapingStrategy implements WebsiteScrapingStrategy {
    @Override
    public void extractMultiPageURLs(String currentURL, Document page, Queue<MultiPageCandidate> multiPageURLs) {
        for (Element element : page.select("ul.pagination")) {
            for (Element a : element.select("a")) {
                if (a.attr("aria-label").equals("Nächste Seite")) {
                    multiPageURLs.offer(new MultiPageCandidate(a.attr("href")));
                    break;
                }
            }
        }
    }

    @Override
    public void extractSinglePagesURLs(String currentUrl, Document page, Set<SinglePageCandidate> singlePageURLs) {
        for (Element pcontent : page.select("div.pcontent")) {
            var nextPage = pcontent.selectFirst("a.p-complete-link");
            if (nextPage != null) {
                singlePageURLs.add(new SinglePageCandidate(nextPage.attr("href")));
            }
        }
    }

    @Override
    public Map<String, List<String>> extractSpecMap(Document page) throws Throwable {
        Map<String, List<String>> specs = new HashMap<>();
        specs.put("EAN", List.of(page.select("span.product-ean").text()));
        specs.put("MPN", List.of(page.select("span.sku-model").text()));

        for (Element element : page.select("div.row.no-gutters.flex-md.flex-lg")) {
            var h1 = element.selectFirst("h1");
            if (h1 != null) {
                specs.put("model", List.of(h1.text()));
                break;
            }
        }

        if (!specs.containsKey("model")) {
            var pageTitle = page.selectFirst("title");
            if (pageTitle != null) {
                specs.put("model", List.of(pageTitle.text()));
            }
        }

        var manufacturer = page.selectFirst("li.hidden-xs.hidden-sm.pull-right.manufacturerImage");
        if (manufacturer != null) {
            var a = manufacturer.selectFirst("a");
            if (a != null) {
                specs.put("manufacturer", List.of(a.attr("title")));
            }
        }

        int counter = 0;
        String nextKey = null;
        List<String> nextValue = null;
        for (Element td : page.select("td")) {
            if (counter % 2 == 0) {
                nextKey = td.text();
            } else {
                nextValue = Arrays.stream(td.text().replace("nicht vorhanden", "false").split(", ")).toList();
            }

            if (nextKey != null && nextValue != null) {
                specs.put(nextKey, nextValue);
                nextKey = null;
                nextValue = null;
            }
            counter++;
        }
        return specs;
    }
}
