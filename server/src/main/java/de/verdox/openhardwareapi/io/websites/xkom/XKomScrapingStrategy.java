package de.verdox.openhardwareapi.io.websites.xkom;

import de.verdox.openhardwareapi.io.api.WebsiteScrapingStrategy;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;

public class XKomScrapingStrategy implements WebsiteScrapingStrategy {
    @Override
    public void extractMultiPageURLs(String currentURL, Document page, Queue<String> multiPageURLs) {
        for (Element pagesItemNext : page.select("li.pages-item-next")) {
            var a = pagesItemNext.selectFirst("a");
            if (a != null) {
                multiPageURLs.add(a.attr("href"));
            }
        }
    }

    @Override
    public void extractSinglePagesURLs(String currentUrl, Document page, Set<String> singlePageURLs) {
        for (Element productItem : page.select("li.product.product-item")) {
            var a = productItem.selectFirst("a.product-item-photo");
            if (a != null) {
                singlePageURLs.add(a.attr("href"));
            }
        }
    }

    @Override
    public Map<String, List<String>> extractSpecMap(Document page) throws Throwable {
        Map<String, List<String>> specs = new HashMap<>();

        var header = page.selectFirst("div.page-title-wrapper.product");
        if (header != null) {
            specs.put("model", List.of(header.selectFirst("span.base").text()));
        }

        var addAttr = page.select("table.data.additional-attributes");
        for (Element element : addAttr) {
            for (Element tr : element.select("tr")) {
                var thLabel = tr.selectFirst("th");
                var thData = tr.selectFirst("td");

                if (thLabel != null && thData != null) {
                    specs.put(thLabel.text(), Arrays.stream(thData.text().split(",")).toList());
                }
            }
        }

        return specs;
    }
}
