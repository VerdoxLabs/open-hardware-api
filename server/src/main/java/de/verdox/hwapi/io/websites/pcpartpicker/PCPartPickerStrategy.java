package de.verdox.hwapi.io.websites.pcpartpicker;

import de.verdox.hwapi.io.api.WebsiteScrapingStrategy;
import org.jsoup.nodes.Document;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class PCPartPickerStrategy implements WebsiteScrapingStrategy {
    @Override
    public void extractMultiPageURLs(String currentURL, Document page, Queue<MultiPageCandidate> multiPageURLs) {

    }

    @Override
    public void extractSinglePagesURLs(String currentUrl, Document page, Set<SinglePageCandidate> singlePageURLs) {

    }

    @Override
    public Map<String, List<String>> extractSpecMap(Document document) throws Throwable {
        return Map.of();
    }
}
