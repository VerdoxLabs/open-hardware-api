package de.verdox.openhardwareapi.io.websites.mindfactory;

import de.verdox.openhardwareapi.io.api.MultiPageHardwareScraper;
import de.verdox.openhardwareapi.io.api.SinglePageHardwareScraper;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public abstract class AbstractMultiMindfactoryScraper<HARDWARE extends HardwareSpec<HARDWARE>> extends MultiPageHardwareScraper<HARDWARE> {

    public static final BiPredicate<String, Document> CHALLENGE_PREDICATE = (url, document) -> document.selectFirst("div.security-content") != null;

    public AbstractMultiMindfactoryScraper(String id, String baseUrl, SinglePageHardwareScraper<HARDWARE> singlePageHardwareScraper) {
        super(id, baseUrl, singlePageHardwareScraper);
        seleniumBasedWebScraper.setIsChallengePage(CHALLENGE_PREDICATE);
        singlePageHardwareScraper.getSeleniumBasedWebScraper().setIsChallengePage(CHALLENGE_PREDICATE);
    }

    @Override
    protected void extractMultiPageURLs(String currentUrl, Document page, Queue<String> multiPageURLs) {
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
    protected void extractSinglePagesURLs(String currentUrl, Document page, Set<String> singlePageURLs) {
        for (Element pcontent : page.select("div.pcontent")) {
            var nextPage = pcontent.selectFirst("a.p-complete-link");
            if (nextPage != null) {
                singlePageURLs.add(nextPage.attr("href"));
            }
        }
    }

    public static class Builder<HARDWARE extends HardwareSpec<HARDWARE>> {
        private String[] urls;
        private SinglePageHardwareScraper.HardwareQuery<HARDWARE> query;
        private BiConsumer<HARDWARE, Map<String, List<String>>> logic;
        private String id;

        public Builder<HARDWARE> withId(String id) {
            this.id = id;
            return this;
        }

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

            return new AbstractMultiMindfactoryScraper<>(id, "", new AbstractSingleMindfactoryScraper<>(id, "", query) {
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
