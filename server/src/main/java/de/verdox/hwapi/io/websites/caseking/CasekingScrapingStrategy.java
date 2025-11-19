package de.verdox.hwapi.io.websites.caseking;

import de.verdox.hwapi.io.api.WebsiteScrapingStrategy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CasekingScrapingStrategy implements WebsiteScrapingStrategy {
    @Override
    public void extractMultiPageURLs(String currentURL, Document page, Queue<MultiPageCandidate> multiPageURLs) {
        for (Element element : page.select("li.page-item")) {
            if (element.text().equals(">")) {
                var a = element.selectFirst("a");
                if (a != null) {
                    multiPageURLs.offer(new MultiPageCandidate(a.attr("href")));
                    break;
                }
            }
        }
    }

    @Override
    public void extractSinglePagesURLs(String currentUrl, Document page, Set<SinglePageCandidate> singlePageURLs) {
        for (Element element : page.select("div.product-tiles")) {
            var nextPage = element.selectFirst("a.badge-group-wrapper");
            if (nextPage != null) {
                String nextUrl = nextPage.attr("href");
                if (nextUrl.contains("https://www.caseking.de")) {
                    singlePageURLs.add(new SinglePageCandidate(nextPage.attr("href")));
                } else {
                    singlePageURLs.add(new SinglePageCandidate("https://www.caseking.de" + nextPage.attr("href")));
                }
            }
        }
    }

    @Override
    public Map<String, List<String>> extractSpecMap(Document page) throws Throwable {
        Map<String, List<String>> specs = new HashMap<>();
        extractBaseInformation(page, specs);
        parseSpecificationsIfPossible(page, specs);

        var manufacturer = page.selectFirst("product-detail-cta-box-brand-logo.w-auto.h-auto.mb-3");
        if (manufacturer != null) {
            specs.put("manufacturer", List.of(manufacturer.attr("alt")));
        }

        if (specs.containsKey("Typ") && !specs.get("Typ").isEmpty()) {
            specs.put("model", specs.get("Typ"));
        } else {
            var productName = page.selectFirst("h1.product-name");
            if (productName != null) {
                String model = productName.text().split(",")[0];
                specs.put("model", List.of(model));
            }
        }

        var responsiveTable = page.selectFirst("div.table-responsive");
        if (responsiveTable != null) {

            for (Element specLv1 : responsiveTable.select("tr.spec-lvl-1")) {
                var elements = specLv1.select("td");
                specs.put(elements.get(0).text(), List.of(elements.get(1).text().replace("Ja", "true").replace("Nein", "false")));
            }
            for (Element specLv2 : responsiveTable.select("tr.spec-lvl-2")) {
                var elements = specLv2.select("td");
                specs.put(elements.get(0).text(), List.of(elements.get(1).text().replace("Ja", "true").replace("Nein", "false")));
            }
            for (Element specLv3 : responsiveTable.select("tr.spec-lvl-3")) {
                var elements = specLv3.select("td");
                specs.put(elements.get(0).text(), List.of(elements.get(1).text().replace("Ja", "true").replace("Nein", "false")));
            }
        }
        return specs;
    }


    private static void extractBaseInformation(Document page, Map<String, List<String>> specs) {
        Elements productAttributes = page.select("div.product-attributes");
        for (Element productAttribute : productAttributes) {
            for (Element element : productAttribute.select("div.d-inline-block")) {
                String label = element.select("span.product-attributes-label").text();
                String value = element.select("span.product-attributes-value").text();

                if (label.equalsIgnoreCase("EAN")) {
                    specs.put("EAN", List.of(value));
                }

                if (label.equalsIgnoreCase("MPN")) {
                    specs.put("MPN", List.of(value));
                }

                if (label.equalsIgnoreCase("HERSTELLER")) {
                    specs.put("manufacturer", List.of(value));
                }
            }
        }
    }

    private static void parseSpecificationsIfPossible(Document page, Map<String, List<String>> specs) {
        Pattern KV = Pattern.compile("^\\s*([^:]+)\\s*:\\s*(.+)\\s*$");

        for (Element specificationDiv : page.select("div.specification")) {
            for (Element li : specificationDiv.select("li")) {
                Element b = li.selectFirst("> b");
                if (b == null) continue;

                String mainKey = b.text().replaceFirst(":\\s*$", "").trim();

                // --- Zeilen nach dem <b> sammeln (mit oder ohne <br>) ---
                List<String> lines = new ArrayList<>();
                StringBuilder cur = new StringBuilder();

                for (Node n : li.childNodes()) {
                    if (n == b) continue;                  // Label überspringen
                    if (n.nodeName().equalsIgnoreCase("br")) {
                        String s = Jsoup.parse(cur.toString()).text().trim();
                        if (!s.isEmpty()) lines.add(s);
                        cur.setLength(0);
                    } else {
                        cur.append(n.outerHtml());         // Text/Inline-Tags anhängen
                    }
                }
                String tail = Jsoup.parse(cur.toString()).text().trim();
                if (!tail.isEmpty()) lines.add(tail);

                // --- Zeilen in Map einsortieren ---
                List<String> mainValues = new ArrayList<>();
                for (String t : lines) {
                    Matcher m = KV.matcher(t);
                    if (m.matches()) {
                        String k = m.group(1).trim();
                        String v = m.group(2).trim();
                        specs.computeIfAbsent(k, __ -> new ArrayList<>()).add(v);
                    } else {
                        mainValues.add(t);
                    }
                }
                if (!mainValues.isEmpty()) {
                    specs.computeIfAbsent(mainKey, __ -> new ArrayList<>()).addAll(mainValues);
                }
            }
        }
    }
}
