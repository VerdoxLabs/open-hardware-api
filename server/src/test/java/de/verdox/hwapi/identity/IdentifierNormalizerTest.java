package de.verdox.hwapi.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifierNormalizerTest {

    @Test
    void normalizesFormattedGtinAndValidatesCheckDigit() {
        assertThat(IdentifierNormalizer.gtin("40-063813-33931"))
                .isEqualTo("4006381333931");
        assertThat(IdentifierNormalizer.gtin("4006381333932"))
                .isNull();
    }

    @Test
    void convertsUpcToEanCompatibleForm() {
        assertThat(IdentifierNormalizer.gtin("036000291452"))
                .isEqualTo("0036000291452");
    }

    @Test
    void normalizesMpnWithoutChangingItsMeaningfulCharacters() {
        assertThat(IdentifierNormalizer.mpn("  Ryzen\u00a07  5800X "))
                .isEqualTo("RYZEN75800X");
    }
}
