package de.verdox.openhardwareapi.io.api;

import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.nodes.Document;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public interface WebsiteScrapingStrategy {
    /**
     * Used to extract the next multi page from the current page (used to traverse pagination in catalogs)disco
     */
    void extractMultiPageURLs(String currentURL, Document page, Queue<String> multiPageURLs);

    /**
     * Used to extract all single pages from the current multi page
     */
    void extractSinglePagesURLs(String currentUrl, Document page, Set<String> singlePageURLs);

    /**
     * Extracts the spec map from the provided page
     */
    Map<String, List<String>> extractSpecMap(Document document) throws Throwable;

    default void parseBasicInfo(String currentUrl, Document page, Map<String, List<String>> specs, HardwareSpec<?> target) throws Throwable {
        if (specs.containsKey("model") && !specs.get("model").isEmpty()) {
            target.setModel(specs.get("model").getFirst());
        }
        if (specs.containsKey("manufacturer") && !specs.get("manufacturer").isEmpty()) {
            target.setManufacturer(specs.get("manufacturer").getFirst());
        }
        if (specs.containsKey("EAN") && !specs.get("EAN").isEmpty()) {
            target.setEAN(specs.get("EAN").getFirst());
        }
        if (specs.containsKey("MPN") && !specs.get("MPN").isEmpty()) {
            target.setMPN(specs.get("MPN").getFirst());
        }
    }
}
