package de.verdox.hwapi.io.websites.pc_builder_io;

import de.verdox.hwapi.io.api.WebsiteScrapingStrategy;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;

public class PCBuilderIOStrategy implements WebsiteScrapingStrategy {
    @Override
    public void extractMultiPageURLs(String currentURL, Document page, Queue<MultiPageCandidate> multiPageURLs) {
        for (Element pagination : page.select("ul.pagination")) {
            for (Element page_item : pagination.select("li.page-item")) {

                var pageLink = page_item.select("a.page-link");

                if (pageLink.hasAttr("rel") && pageLink.hasAttr("href") && pageLink.attr("rel").equals("next")) {
                    multiPageURLs.offer(new MultiPageCandidate(pageLink.attr("href")));
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
        }
    }

    @Override
    public boolean supportsHeadlessScraping() {
        return true;
    }

    @Override
    public Map<String, List<String>> extractSpecMap(Document document) throws Throwable {
        Map<String, List<String>> specs = new HashMap<>();

        var productName = document.selectFirst("span.product-name");
        if (productName != null) {
            specs.put("model", List.of(productName.text()));
        }

        var header = document.selectFirst("div.product-wrapper.d-flex.align-items-center.justify-content-between");

        if(header != null) {
            for (Element img : header.select("img")) {
                if(img.attr("src").startsWith("http")) {
                    String attr = img.attr("src");
                    if(attr.endsWith(".jpg") && attr.contains("/images/")) {
                        specs.put("img", List.of(attr));
                        break;
                    }
                }
            }
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


                    text = text.trim();

                    if (key == null) {
                        key = text;
                    } else {
                        value = text;
                        if (!potentialValues.isEmpty()) {
                            potentialValues.reversed();
                            specs.put(key.toLowerCase(Locale.ROOT), potentialValues);
                        } else {
                            specs.put(key.toLowerCase(Locale.ROOT), List.of(value));
                        }
                        break;
                    }
                }
            });
            break;
        }

        if (specs.containsKey("upc codes")) {
            List<String> EANs = new ArrayList<>();
            for (String upcCode : specs.get("upc codes").getFirst().split(", ")) {
                EANs.add("0" + upcCode);
            }
            specs.put("EAN", EANs);
        }

        if (specs.containsKey("part #")) {
            specs.put("MPN", specs.get("part #"));
        }

        return specs;
    }
}
