package de.verdox.hwapi.catalog.ingestion.images;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Extracts likely product-image URLs without relying on a particular catalog provider. */
public final class ProductImageCandidates {
    public static final String SPEC_KEY = "_productImageUrls";
    private static final int MAX_IMAGES = 8;

    private ProductImageCandidates() {
    }

    public static void addTo(Map<String, List<String>> specs, Document page) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        add(urls, page.select("meta[property=og:image][content], meta[name=twitter:image][content]"), "content");
        add(urls, page.select("img[itemprop=image], .product img, .product-image img, .product-gallery img, .gallery img, main img"), "data-zoom-image");
        add(urls, page.select("img[itemprop=image], .product img, .product-image img, .product-gallery img, .gallery img, main img"), "data-large-image");
        add(urls, page.select("img[itemprop=image], .product img, .product-image img, .product-gallery img, .gallery img, main img"), "data-original");
        add(urls, page.select("img[itemprop=image], .product img, .product-image img, .product-gallery img, .gallery img, main img"), "data-src");
        add(urls, page.select("img[itemprop=image], .product img, .product-image img, .product-gallery img, .gallery img, main img"), "src");
        if (!urls.isEmpty()) specs.put(SPEC_KEY, List.copyOf(urls));
    }

    private static void add(LinkedHashSet<String> urls, Iterable<Element> elements, String attribute) {
        for (Element element : elements) {
            if (urls.size() >= MAX_IMAGES) return;
            String url = element.absUrl(attribute);
            if (!url.isBlank() && (url.startsWith("https://") || url.startsWith("http://"))) urls.add(url);
        }
    }
}
