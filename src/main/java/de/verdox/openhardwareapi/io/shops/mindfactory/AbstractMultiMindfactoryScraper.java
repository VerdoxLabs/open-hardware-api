package de.verdox.openhardwareapi.io.shops.mindfactory;

import de.verdox.openhardwareapi.io.api.MultiPageHardwareScraper;
import de.verdox.openhardwareapi.io.api.SinglePageHardwareScraper;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;
import java.util.function.BiConsumer;

public abstract class AbstractMultiMindfactoryScraper<HARDWARE extends HardwareSpec> extends MultiPageHardwareScraper<HARDWARE> {
    public AbstractMultiMindfactoryScraper(String baseUrl, SinglePageHardwareScraper<HARDWARE> singlePageHardwareScraper) {
        super(baseUrl, singlePageHardwareScraper);
        seleniumBasedWebScraper.setIsChallengePage(document -> document.selectFirst("div.security-content") != null);
        singlePageHardwareScraper.getSeleniumBasedWebScraper().setIsChallengePage(document -> document.selectFirst("div.security-content") != null);
    }

    @Override
    protected void extractMultiPageURLs(Document page, Queue<String> multiPageURLs) {
        for (Element element : page.select("ul.pagination")) {
            for (Element a : element.select("a")) {
                if (a.attr("aria-label").equals("Nächste Seite")) {
                    multiPageURLs.offer(a.attr("href"));
                    break;
                }
            }
        }
    }

    @Override
    protected void extractSinglePagesURLs(Document page, Set<String> singlePageURLs) {
        for (Element pcontent : page.select("div.pcontent")) {
            var nextPage = pcontent.selectFirst("a.p-complete-link");
            if (nextPage != null) {
                singlePageURLs.add(nextPage.attr("href"));
            }
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

        public AbstractMultiMindfactoryScraper<HARDWARE> build() {
            if (urls == null || urls.length == 0) {
                throw new IllegalStateException("No urls defined");
            }

            if (query == null) {
                throw new IllegalStateException("No query defined");
            }

            if (logic == null) {
                throw new IllegalStateException("No spec translation logic defined");
            }

            return new AbstractMultiMindfactoryScraper<>("", new AbstractSingleMindfactoryScraper<>("", query) {
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
