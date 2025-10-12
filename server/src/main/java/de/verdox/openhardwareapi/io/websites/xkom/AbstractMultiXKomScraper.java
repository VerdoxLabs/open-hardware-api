package de.verdox.openhardwareapi.io.websites.xkom;

import de.verdox.openhardwareapi.io.api.MultiPageHardwareScraper;
import de.verdox.openhardwareapi.io.api.SinglePageHardwareScraper;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public abstract class AbstractMultiXKomScraper<HARDWARE extends HardwareSpec<HARDWARE>> extends MultiPageHardwareScraper<HARDWARE> {
    public AbstractMultiXKomScraper(String id, String baseUrl, SinglePageHardwareScraper<HARDWARE> singlePageHardwareScraper) {
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
        for (Element pagesItemNext : page.select("li.pages-item-next")) {
            var a = pagesItemNext.selectFirst("a");
            if(a != null) {
                multiPageURLs.add(a.attr("href"));
            }
        }
    }

    @Override
    protected void extractSinglePagesURLs(String currentUrl, Document page, Set<String> singlePageURLs) {
        for (Element productItem : page.select("li.product.product-item")) {
            var a = productItem.selectFirst("a.product-item-photo");
            if(a != null) {
                singlePageURLs.add(a.attr("href"));
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

        public AbstractMultiXKomScraper<HARDWARE> build() {

            if (urls == null || urls.length == 0) {
                throw new IllegalStateException("No urls defined");
            }

            if (query == null) {
                throw new IllegalStateException("No query defined");
            }

            if (logic == null) {
                throw new IllegalStateException("No spec translation logic defined");
            }

            return new AbstractMultiXKomScraper<>(id, "", new AbstractSingleXKomScraper<>(id, "", query) {
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
