package de.verdox.hwapi.io.websites.pc_kombo;

import de.verdox.hwapi.io.api.WebsiteScrapingStrategy;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;

public class PCKomboStrategy implements WebsiteScrapingStrategy {
    @Override
    public void extractMultiPageURLs(String currentURL, Document page, Queue<MultiPageCandidate> multiPageURLs) {
        //TODO
    }

    @Override
    public void extractSinglePagesURLs(String currentUrl, Document page, Set<SinglePageCandidate> singlePageURLs) {
        for (Element columns : page.select("li.columns")) {
            var a = columns.selectFirst("a");
            if (a != null) {
                SinglePageCandidate singlePageCandidate = new SinglePageCandidate(a.attr("href"));

                var span = columns.selectFirst("span.series");
                if (span != null) {
                    singlePageCandidate.specMap().put("gpu-chip", List.of(span.text()));
                }
                singlePageURLs.add(singlePageCandidate);
            }
        }
    }

    @Override
    public Map<String, List<String>> extractSpecMap(Document page) throws Throwable {
        Map<String, List<String>> specs = new HashMap<>();

        var firstH1 = page.selectFirst("h1");
        if (firstH1 != null) {
            specs.put("model", List.of(firstH1.text()));
        }


        // Outer container (kann es mehrfach geben – dann alles zusammenführen)
        for (Element container : page.select("div#specs.column.col-12")) {
            // Alle Definition Lists innerhalb der Sections
            for (Element dl : container.select("section.card dl")) {
                Elements children = dl.children();

                for (int i = 0; i < children.size(); i++) {
                    Element el = children.get(i);
                    if (!"dt".equals(el.tagName())) {
                        continue;
                    }

                    final String key = el.text().trim();
                    if (key.isBlank()) {
                        continue;
                    }

                    List<String> values = new ArrayList<>();
                    int j = i + 1;

                    // Sammle alle aufeinanderfolgenden <dd> bis zum nächsten <dt>
                    while (j < children.size() && "dd".equals(children.get(j).tagName())) {
                        String val = children.get(j).text().trim();
                        if (!val.isBlank()) {
                            values.add(val);
                        }
                        j++;
                    }

                    if (!values.isEmpty()) {
                        // Merge: existierende Werte behalten und neue anhängen
                        specs.merge(key, values, (oldList, newList) -> {
                            oldList.addAll(newList);
                            return oldList;
                        });
                    }

                    // überspringe bereits verarbeitete <dd>-Elemente
                    i = j - 1;
                }
            }
        }

        if (specs.containsKey("Producer")) {
            specs.put("manufacturer", specs.get("Producer"));
        }
        return specs;
    }

}
