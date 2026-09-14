package de.verdox.hwapi.identity.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class C2CTitleCleanerTest {

    @Test
    void removesConditionPhrases() {
        assertThat(C2CTitleCleaner.clean("Grafikkarte RTX 4070 wie neu"))
                .isEqualTo("grafikkarte rtx 4070");
        assertThat(C2CTitleCleaner.clean("Ryzen 7 5800X kaum benutzt"))
                .isEqualTo("ryzen 7 5800x");
        assertThat(C2CTitleCleaner.clean("SSD 1TB in sehr gutem Zustand"))
                .isEqualTo("ssd 1tb");
    }

    @Test
    void removesSaleAndShippingNoise() {
        assertThat(C2CTitleCleaner.clean("Festpreis! DDR4 RAM 16GB, VB"))
                .isEqualTo("ddr4 ram 16gb");
        assertThat(C2CTitleCleaner.clean("Netzteil 850W zzgl. Versand, Abzugeben"))
                .isEqualTo("netzteil 850w");
        assertThat(C2CTitleCleaner.clean("Biete Monitor 27 Inch top Preis"))
                .isEqualTo("monitor 27 inch");
    }

    @Test
    void removesMarketingFiller() {
        assertThat(C2CTitleCleaner.clean("Gaming PC i7 + RTX 4070 super Angebot einmalig"))
                // "super" bleibt: es ist hier ein Modell-Suffix, kein Marketing-Wort.
                .isEqualTo("gaming pc i7 rtx 4070 super");
        assertThat(C2CTitleCleaner.clean("Mainboard B550 AM4 neuware OVP"))
                .isEqualTo("mainboard b550 am4");
    }

    @Test
    void preservesModelNumbersAndSpecs() {
        String cleaned = C2CTitleCleaner.clean("GeForce RTX 4070 Super (12GB) wie neu VB");
        // Klammern-Inhalt wird entfernt, Modellnummer + "super" bleiben stehen.
        assertThat(cleaned).isEqualTo("geforce rtx 4070 super");
        // "tray" ist ein Packaging-Begriff und gehört bewusst zur kanonischen
        // normalizeTitle, NICHT zum C2C-Cleaner – die beiden Schichten komplementieren sich.
        assertThat(C2CTitleCleaner.clean("Core i9 13900K Tray"))
                .isEqualTo("core i9 13900k tray");
        assertThat(C2CTitleCleaner.clean("Radeon RX 9060 XT 16GB"))
                .isEqualTo("radeon rx 9060 xt 16gb");
    }

    @Test
    void lowercasesAndNormalizesWhitespace() {
        assertThat(C2CTitleCleaner.clean("  SSD   1TB  NVMe  "))
                .isEqualTo("ssd 1tb nvme");
    }

    @Test
    void isIdempotent() {
        String raw = "RTX 4070 Super wie neu VB Festpreis";
        String once = C2CTitleCleaner.clean(raw);
        assertThat(C2CTitleCleaner.clean(once)).isEqualTo(once);
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(C2CTitleCleaner.clean(null)).isNull();
        assertThat(C2CTitleCleaner.clean("   ")).isEmpty();
    }
}
