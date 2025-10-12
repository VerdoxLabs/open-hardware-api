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

        var header = page.selectFirst("div.desc_section.head_desc");
        if (header != null) {
            specs.put("model", List.of(header.selectFirst("h2").text()));
        }

        var addAttr = page.select("div.additional-attributes");
        for (Element element : addAttr) {
            for (Element tr : element.select("tr")) {
                var label = tr.selectFirst("th.col.label").text();
                var data = tr.selectFirst("th.col.data").text();

                specs.put(label, Arrays.stream(data.split(",")).toList());
            }
        }

        return specs;
    }
}
