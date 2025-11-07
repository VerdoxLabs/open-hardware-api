package de.verdox.openhardwareapi.io.websites.pc_builder_io;

import de.verdox.openhardwareapi.io.api.WebsiteScrapingStrategy;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;

public class PCBuilderIOStrategy implements WebsiteScrapingStrategy {
    @Override
    public void extractMultiPageURLs(String currentURL, Document page, Queue<MultiPageCandidate> multiPageURLs) {
        for (Element pagination : page.select("ul.pagination")) {
            for (Element page_item : pagination.select("li.page-item")) {
                if (page_item.hasAttr("rel") && page_item.hasAttr("href") && page_item.attr("rel").equals("next")) {
                    multiPageURLs.offer(new MultiPageCandidate(page_item.attr("href")));
                    break;
                }
            }
        }
    }

    @Override
    public void extractSinglePagesURLs(String currentUrl, Document page, Set<SinglePageCandidate> singlePageURLs) {
        for (Element element : page.select("div.card.h-100")) {
            var a = element.selectFirst("a");
            if (a == null) {
                continue;
            }
            SinglePageCandidate singlePageCandidate = new SinglePageCandidate(a.attr("href"));
            singlePageURLs.add(singlePageCandidate);
            var img = element.selectFirst("img");
            if (img != null) {
                singlePageCandidate.specMap().put("img", List.of(img.attr("src")));
            }
        }
    }

    @Override
    public Map<String, List<String>> extractSpecMap(Document document) throws Throwable {
        Map<String, List<String>> specs = new HashMap<>();

        var productName = document.selectFirst("span.product-name");
        if (productName != null) {
            specs.put("model", List.of(productName.text()));
        }

        for (Element element : document.select("div.spec-variant-boxes.spec-table")) {
            element.select("div.spec-variant-box").forEach(specBox -> {
                String key = null;
                String value = null;
                for (Element span : specBox.select("span")) {

                    String text = span.text();
                    var potential = span.select("p.mb-0");

                    List<String> potentialValues = new ArrayList<>();
                    for (Element values : potential) {
                        potentialValues.add(values.text());
                    }


                    text = text
                            .replace("-NA", "")
                            .replace("-UK", "")
                            .replace("-US", "")
                            .replace("-CH", "")
                            .replace("-EU", "")
                            .replace("-AU", "")
                            .trim();

                    if (key == null) {
                        key = text;
                    } else {
                        if (value == null) {
                            value = text;
                        } else {
                            if (!potentialValues.isEmpty()) {
                                potentialValues.reversed();
                                specs.put(key.toLowerCase(Locale.ROOT), potentialValues);
                            } else {
                                specs.put(key.toLowerCase(Locale.ROOT), List.of(value));
                            }
                            break;
                        }
                    }
                }
            });
            break;
        }

        if (specs.containsKey("UPC Codes")) {
            List<String> EANs = new ArrayList<>();
            for (String upcCode : specs.get("UPC Codes").getFirst().split(", ")) {
                EANs.add("0" + upcCode);
            }
            specs.put("EAN", EANs);
        }

        if (specs.containsKey("Part #")) {
            specs.put("MPN", specs.get("Part #"));
        }

        if (specs.containsKey("Model")) {
            specs.put("model", specs.get("Model"));
            specs.remove("Model");
        }

        return specs;
    }
}
