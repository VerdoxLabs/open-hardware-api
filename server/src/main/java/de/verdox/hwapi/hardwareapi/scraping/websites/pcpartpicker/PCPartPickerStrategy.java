package de.verdox.hwapi.hardwareapi.scraping.websites.pcpartpicker;

import de.verdox.hwapi.hardwareapi.scraping.api.WebsiteScrapingStrategy;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;

public class PCPartPickerStrategy implements WebsiteScrapingStrategy {
    @Override
    public void extractMultiPageURLs(String currentURL, Document page, Queue<MultiPageCandidate> multiPageURLs) {
        if (currentURL.contains("#pages")) {
            return;
        }

        var pagination = page.selectFirst("ul.pagination.list-unstyled.xs-text-center");
        if (pagination == null) return;
        var lastItemInPagination = pagination.select("li").getLast();
        int lastPage = Integer.parseInt(lastItemInPagination.text());

        for (int i = 1; i <= lastPage; i++) {
            multiPageURLs.offer(new MultiPageCandidate(currentURL + "#pages=" + i));
        }
    }

    @Override
    public void extractSinglePagesURLs(String currentUrl, Document page, Set<SinglePageCandidate> singlePageURLs) {
        var content = page.selectFirst("productList--detailed.xs-col-12.tablesorter.tablesorter-default");
        if (content == null) return;
        for (Element tr : content.selectFirst("tbody").select("tr")) {
            var td = tr.selectFirst("td.td__name");
            String urlToSinglePage = "https://pcpartpicker.com/" + td.selectFirst("a").attr("href");
            String nameOfHardware = td.selectFirst("div.td__nameWrapper").selectFirst("p").text();
            singlePageURLs.add(new SinglePageCandidate(urlToSinglePage, Map.of("model", List.of(nameOfHardware))));
        }
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

        return specMap;
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
