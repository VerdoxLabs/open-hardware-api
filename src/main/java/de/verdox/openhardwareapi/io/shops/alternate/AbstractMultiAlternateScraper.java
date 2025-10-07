package de.verdox.openhardwareapi.io.shops.alternate;

import de.verdox.openhardwareapi.io.api.MultiPageHardwareScraper;
import de.verdox.openhardwareapi.io.api.SinglePageHardwareScraper;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;
import java.util.function.BiConsumer;

public abstract class AbstractMultiAlternateScraper<HARDWARE extends HardwareSpec> extends MultiPageHardwareScraper<HARDWARE> {
    public AbstractMultiAlternateScraper(String baseUrl, SinglePageHardwareScraper<HARDWARE> singlePageHardwareScraper) {
        super(baseUrl, singlePageHardwareScraper);
        seleniumBasedWebScraper.setIsChallengePage(document -> {
            if (document == null) return true;
            document.body();
            return document.body().childrenSize() == 0 && document.body().text().trim().isEmpty();
        });
        singlePageHardwareScraper.getSeleniumBasedWebScraper().setIsChallengePage(document -> {
            if (document == null) return true;
            document.body();
            return document.body().childrenSize() == 0 && document.body().text().trim().isEmpty();
        });
    }

    @Override
    protected void extractMultiPageURLs(Document page, Queue<String> multiPageURLs) {
        for (Element mt2 : page.select("div.mt-2")) {
            for (Element a : mt2.select("a")) {
                if (a.attr("aria-label").equals("Nächste Seite")) {
                    String linkToNextPage = a.attr("href");
                    multiPageURLs.add("https://www.alternate.at" + linkToNextPage);
                    break;
                }
            }
        }
    }

    @Override
    protected void extractSinglePagesURLs(Document page, Set<String> singlePageURLs) {
        for (Element containerListing : page.select("div.grid-container listing")) {
            var a = containerListing.selectFirst("a");
            if (a == null) {
                continue;
            }
            String url = a.attr("href");
            if (url.isBlank()) {
                continue;
            }
            singlePageURLs.add(url);
        }
    }

    public static class Builder<HARDWARE extends HardwareSpec> {
        private String[] urls;
        private SinglePageHardwareScraper.HardwareQuery<HARDWARE> query;
        private BiConsumer<HARDWARE, Map<String, List<String>>> logic;

        public Builder<HARDWARE> withURLs(String... urls) {
            this.urls = urls;
            return this;
        }

        public Builder<HARDWARE> withQuery(SinglePageHardwareScraper.HardwareQuery<HARDWARE> query) {
            this.query = query;
            return this;
        }

        public Builder<HARDWARE> withSpecsTranslation(BiConsumer<HARDWARE, Map<String, List<String>>> logic) {
            this.logic = logic;
            return this;
        }

        public AbstractMultiAlternateScraper<HARDWARE> build() {

            if (urls == null || urls.length == 0) {
                throw new IllegalStateException("No urls defined");
            }

            if (query == null) {
                throw new IllegalStateException("No query defined");
            }

            if (logic == null) {
                throw new IllegalStateException("No spec translation logic defined");
            }

            return new AbstractMultiAlternateScraper<>("", new AbstractSingleAlternateScraper<>("", query) {
                @Override
                protected void translateSpecsToTarget(Map<String, List<String>> specs, HARDWARE target) {
                    super.translateSpecsToTarget(specs, target);
                    logic.accept(target, specs);
                }
            }) {
                @Override
                protected void setup(Queue<String> multiPageURLs) {
                    multiPageURLs.addAll(Arrays.asList(urls));
                }
            };
        }
    }
}
