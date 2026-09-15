package de.verdox.hwapi.catalog.ingestion.websites.pcpartpicker;

import de.verdox.hwapi.catalog.ingestion.api.WebsiteScrapingStrategy;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.JavascriptExecutor;

import java.time.Duration;
import java.util.*;

public class PCPartPickerStrategy implements WebsiteScrapingStrategy {
    @Override
    public Duration cacheTTLForMultiPages() {
        // Catalog pages are the sole live change detector. Product detail pages remain
        // permanently cache-first (configured by WebsiteCatalogScraper).
        return Duration.ofDays(1);
    }

    @Override
    public Duration productRevalidationInterval() {
        // Catalog pages find new URLs daily; known detail pages need only occasional parsing.
        return Duration.ofDays(30);
    }

    @Override
    public void extractMultiPageURLs(String currentURL, Document page, Queue<MultiPageCandidate> multiPageURLs) {
        // PCPartPicker uses hash routing (#page=N).  Do not infer the last page from
        // text: the pagination can contain ellipses and its visible range changes.
        // The hrefs are the authoritative list of pages available from this snapshot.
        for (Element link : page.select("#module-pagination ul.pagination a[href*='#page=']")) {
            String pageUrl = link.absUrl("href");
            if (pageUrl.isBlank()) {
                pageUrl = link.attr("href");
            }
            if (!pageUrl.isBlank()) {
                multiPageURLs.offer(new MultiPageCandidate(pageUrl));
            }
        }
    }

    @Override
    public void extractSinglePagesURLs(String currentUrl, Document page, Set<SinglePageCandidate> singlePageURLs) {
        var content = page.selectFirst("table.productList--detailed");
        if (content == null) return;
        var body = content.selectFirst("tbody");
        if (body == null) return;
        for (Element tr : body.select("> tr")) {
            var td = tr.selectFirst("td.td__name");
            if (td == null) continue;

            Element productLink = td.selectFirst("a[href*='/product/']");
            if (productLink == null) continue;

            String urlToSinglePage = productLink.absUrl("href");
            if (urlToSinglePage.isBlank()) {
                urlToSinglePage = productLink.attr("href");
            }
            Element name = td.selectFirst("div.td__nameWrapper > p");
            if (name == null || name.text().isBlank() || urlToSinglePage.isBlank()) continue;

            singlePageURLs.add(new SinglePageCandidate(urlToSinglePage,
                    Map.of("model", List.of(cleanText(name.text())))));
        }
    }

    @Override
    public void interactForMultiPage(String currentURL, org.openqa.selenium.WebDriver driver) {
        int fragmentStart = currentURL.indexOf("#page=");
        if (fragmentStart < 0 || !(driver instanceof JavascriptExecutor javascript)) return;

        String fragment = currentURL.substring(fragmentStart);
        javascript.executeScript("window.location.hash = arguments[0];", fragment);
    }

    @Override
    public Map<String, List<String>> extractSpecMap(Document document) throws Throwable {
        Map<String, List<String>> specMap = new LinkedHashMap<>();

        // Mobile-Spec-Block, wenn vorhanden – sonst auf dem ganzen Dokument suchen
        Element specsRoot = document.selectFirst("div.block.xs-block.md-hide.specs");
        if (specsRoot == null) {
            specsRoot = document;
        }

        // Jede einzelne Spec-Gruppe
        for (Element group : specsRoot.select("div.group.group--spec")) {
            Element titleElement = group.selectFirst("h3.group__title");
            if (titleElement == null) {
                continue;
            }

            String key = cleanText(titleElement.text());
            if (key.isEmpty()) {
                continue;
            }

            Element content = group.selectFirst("div.group__content");
            if (content == null) {
                continue;
            }

            List<String> values = new ArrayList<>();

            // 1) Alle <li> Einträge (z.B. CPU Socket mit vielen Werten)
            for (Element li : content.select("li")) {
                String value = cleanText(li.text());
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }

            // 2) Wenn keine <li> vorhanden waren, dann <p>-Einträge verwenden
            if (values.isEmpty()) {
                for (Element p : content.select("p")) {
                    String value = cleanText(p.text());
                    if (!value.isEmpty()) {
                        values.add(value);
                    }
                }
            }

            // 3) Fallback: gesamter Textinhalt
            if (values.isEmpty()) {
                String value = cleanText(content.text());
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }

            if (!values.isEmpty()) {
                specMap.put(key, values);
            }
        }

        // The ingestion API uses these normalized keys.  Keep the original page labels
        // as well, as they are useful for component-specific parsers.
        copyIfPresent(specMap, "Part #", "MPN");
        copyIfPresent(specMap, "Manufacturer", "manufacturer");
        copyIfPresent(specMap, "Model", "model");

        Element image = document.selectFirst("meta[property=og:image][content]");
        if (image != null && !image.attr("content").isBlank()) {
            specMap.put("imageUrl", List.of(image.attr("content").replaceFirst("^http:", "https:")));
        }

        return specMap;
    }

    private static void copyIfPresent(Map<String, List<String>> specs, String source, String target) {
        if (!specs.containsKey(target) && specs.containsKey(source)) {
            specs.put(target, List.copyOf(specs.get(source)));
        }
    }

    /**
     * Normalisiert Whitespace, entfernt \u00A0 etc.
     */
    private String cleanText(String raw) {
        if (raw == null) {
            return "";
        }
        // Non-breaking spaces und Co. ersetzen
        String cleaned = raw
                .replace('\u00A0', ' ')
                .replace('\u200B', ' ');

        // Mehrere Whitespaces auf eins zusammenziehen und trimmen
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }
}
