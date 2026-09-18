package de.verdox.hwapi.pricing.sources.kleinanzeigen;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class KleinanzeigenScraperTest {

    @Test
    void parsesCurrentAstroResultCards() {
        var document = Jsoup.parse("""
                <ul id="srchrslt-adtable">
                  <li><article data-adid="3514443495" data-href="/s-anzeige/amd-ryzen-7-7800x3d/3514443495-225-2762">
                    <a href="/s-anzeige/amd-ryzen-7-7800x3d/3514443495-225-2762"><img src="/image.jpg"></a>
                    <h3><a href="/s-anzeige/amd-ryzen-7-7800x3d/3514443495-225-2762">AMD Ryzen 7 7800X3D Prozessor</a></h3>
                    <p>250 € VB</p>
                  </article></li>
                </ul>
                """, "https://www.kleinanzeigen.de");

        var ads = new KleinanzeigenScraper("test").parseAds(document);

        assertThat(ads).singleElement().satisfies(ad -> {
            assertThat(ad.adId()).isEqualTo("3514443495");
            assertThat(ad.title()).isEqualTo("AMD Ryzen 7 7800X3D Prozessor");
            assertThat(ad.price()).isEqualByComparingTo(new BigDecimal("250"));
            assertThat(ad.negotiable()).isTrue();
            assertThat(ad.url()).isEqualTo("https://www.kleinanzeigen.de/s-anzeige/amd-ryzen-7-7800x3d/3514443495-225-2762");
        });
    }
}
