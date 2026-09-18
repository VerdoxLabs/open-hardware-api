package de.verdox.hwapi.catalog.ingestion.images;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductImageCandidatesTest {

    @Test
    void prefersAndCollectsProductImagesWithoutCollectingTheSiteLogo() {
        Map<String, List<String>> specs = new HashMap<>();
        ProductImageCandidates.addTo(specs, Jsoup.parse("""
                <html><head><meta property='og:image' content='https://cdn.example.test/og.jpg'></head>
                <body><img src='https://example.test/logo.png'><main>
                <img itemprop='image' src='https://cdn.example.test/product.jpg'></main></body></html>
                """, "https://example.test/product"));

        assertThat(specs.get(ProductImageCandidates.SPEC_KEY))
                .containsExactly("https://cdn.example.test/og.jpg", "https://cdn.example.test/product.jpg");
    }
}
