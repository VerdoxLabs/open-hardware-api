package de.verdox.openhardwareapi.io.websites.alternate;

import de.verdox.openhardwareapi.io.api.MultiPageHardwareScraper;
import de.verdox.openhardwareapi.io.api.SinglePageHardwareScraper;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public abstract class AbstractMultiAlternateScraper<HARDWARE extends HardwareSpec<HARDWARE>> extends MultiPageHardwareScraper<HARDWARE> {
    public AbstractMultiAlternateScraper(String id, String baseUrl, SinglePageHardwareScraper<HARDWARE> singlePageHardwareScraper) {
        super(id, baseUrl, singlePageHardwareScraper);
        seleniumBasedWebScraper.setIsChallengePage(CHALLENGE_DETECTOR());
        singlePageHardwareScraper.getSeleniumBasedWebScraper().setIsChallengePage(CHALLENGE_DETECTOR());
    }

    private static BiPredicate<String, Document> CHALLENGE_DETECTOR() {
        return (url, document) -> {
            if (document == null) return true;
            document.body();
            return document.body().childrenSize() == 0 && document.body().text().trim().isEmpty();
        };
    }

    @Override
    protected void extractMultiPageURLs(String currentUrl, Document page, Queue<String> multiPageURLs) {

    }

    @Override
    protected void extractSinglePagesURLs(String currentUrl, Document page, Set<String> singlePageURLs) {

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

            return new AbstractMultiAlternateScraper<>(id, "", new AbstractSingleAlternateScraper<>(id, "", query) {
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
