package de.verdox.hwapi.pricing.sources.kleinanzeigen;

import de.verdox.hwapi.catalog.domain.values.Currency;

import java.math.BigDecimal;

/**
 * Rohes Kleinanzeigen-Inserat, so wie es aus dem Listing-HTML geparst wird.
 *
 * <p>Nur Rohtatsachen – bewusst <b>ohne</b> Matching-/Business-Logik. Die
 * Zuordnung zur Product-Identity und die Persistenz passieren in der
 * {@code KleinanzeigenPriceService}.
 *
 * @param adId         Kleinanzeigen-Ad-ID (aus {@code data-adid}); eindeutiger Schlüssel
 * @param title        roher Inseratstitel (C2C-Rauschen enthalten)
 * @param price        Preis; <b>null</b> bei "VB" (Verhandlungsbasis) ohne Richtpreis
 * @param currency     Währung (DE: üblicherweise EUR)
 * @param negotiable   true, wenn der Preis "VB" (Verhandlungsbasis) ist
 * @param url          absolute URL der Anzeige
 * @param imageUrl     absolute URL des Vorschaubilds (kann null sein)
 */
public record KleinanzeigenAd(
        String adId,
        String title,
        BigDecimal price,
        Currency currency,
        boolean negotiable,
        String url,
        String imageUrl
) {
}
