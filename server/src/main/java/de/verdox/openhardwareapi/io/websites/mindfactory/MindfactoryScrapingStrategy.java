package de.verdox.openhardwareapi.io.websites.mindfactory;

import de.verdox.openhardwareapi.io.api.WebsiteScrapingStrategy;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;

public class MindfactoryScrapingStrategy implements WebsiteScrapingStrategy {
    @Override
    public void extractMultiPageURLs(String currentURL, Document page, Queue<String> multiPageURLs) {
        for (Element element : page.select("ul.pagination")) {
            for (Element a : element.select("a")) {
                if (a.attr("aria-label").equals("Nächste Seite")) {
                    multiPageURLs.offer(a.attr("href"));
                    break;
                }
            }
        }
    }

    @Override
    public void extractSinglePagesURLs(String currentUrl, Document page, Set<String> singlePageURLs) {
        for (Element pcontent : page.select("div.pcontent")) {
            var nextPage = pcontent.selectFirst("a.p-complete-link");
            if (nextPage != null) {
                singlePageURLs.add(nextPage.attr("href"));
            }
        }
    }

    @Override
    public Map<String, List<String>> extractSpecMap(Document page) throws Throwable {
        Map<String, List<String>> specs = new HashMap<>();
        specs.put("EAN", List.of(page.select("span.product-ean").text()));

        for (Element visibleXs : page.select("div.visible-xs")) {
            var h1 = visibleXs.selectFirst("h1");
            if (h1 != null && h1.attr("data-original-font-size").equals("18.2")) {
                specs.put("model", List.of(visibleXs.text()));
                break;
            }
        }

        for (Element mat10 : page.select("div.mat10")) {
            for (Element img : mat10.select("img")) {
                String alt = img.attr("alt");
                String title = img.attr("title");
                if (alt.equalsIgnoreCase(title)) {
                    specs.put("manufacturer", List.of(title));
                }
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
