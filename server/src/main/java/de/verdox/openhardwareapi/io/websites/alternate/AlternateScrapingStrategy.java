package de.verdox.openhardwareapi.io.websites.alternate;

import de.verdox.openhardwareapi.io.api.WebsiteScrapingStrategy;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;

public class AlternateScrapingStrategy implements WebsiteScrapingStrategy {
    @Override
    public void extractMultiPageURLs(String currentURL, Document page, Queue<String> multiPageURLs) {
        for (Element mt2 : page.select("div.mt-2")) {
            for (Element a : mt2.select("a")) {
                if (a.attr("aria-label").equals("Nächste Seite")) {
                    String linkToNextPage = a.attr("href");
                    multiPageURLs.add("https://www.alternate.at" + linkToNextPage);
                    break;
                }
            }
        }
    }

    @Override
    public void extractSinglePagesURLs(String currentUrl, Document page, Set<String> singlePageURLs) {
        for (Element containerListing : page.select("div.grid-container listing")) {
            var a = containerListing.selectFirst("a");
            if (a == null) {
                continue;
            }
            String url = a.attr("href");
            if (url.isBlank()) {
                continue;
            }
            singlePageURLs.add(url);
        }
    }

    @Override
    public Map<String, List<String>> extractSpecMap(Document page) throws Throwable {
        Map<String, List<String>> specs = new HashMap<>();

        var productNameDiv = page.selectFirst("div.product-name");
        if (productNameDiv == null) {
            return specs;
        }
        StringBuilder title = new StringBuilder();
        for (Element span : productNameDiv.select("span")) {
            title.append(" ").append(span.text());
        }
        specs.put("model", List.of(title.toString()));

        var productDetails = page.selectFirst("div.card nav-product-details");
        for (Element tr : productDetails.select("tr")) {
            var columnTitleTr = tr.selectFirst("td.c1");
            var columnSubTitleTr = tr.selectFirst("td.c2");
            var columnValueTr = tr.selectFirst("td.c4");

            String columnTitle = columnSubTitleTr != null ? columnSubTitleTr.text() : columnTitleTr != null ? columnTitleTr.text() : "";
            if (columnTitle.isBlank() || columnValueTr == null) {
                continue;
            }

            specs.put(columnTitle, List.of(columnValueTr.text()));

            if (columnTitle.equals("EAN")) {
                specs.put("EAN", List.of(columnValueTr.text()));
            }

            if (columnTitle.equals("Hersteller-Nr.")) {
                specs.put("MPN", List.of(columnValueTr.text()));
            }
        }
        return specs;
    }
}
