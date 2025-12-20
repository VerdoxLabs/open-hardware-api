package de.verdox.hwapi.priceapi.component.service.amazon;

import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.priceapi.component.service.RemoteActiveListingWriterService;
/*import de.verdox.hwapi.priceapi.component.service.amazon.AmazonPriceService.AmazonPriceResult;*/
import de.verdox.hwapi.priceapi.model.RemoteActiveListing;
import de.verdox.hwapi.productid.ProductIdentifier;
import de.verdox.hwapi.productidregistry.ProductRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class AmazonTrackActiveListingsService {
/*
    private static final Logger LOGGER = Logger.getLogger(AmazonTrackActiveListingsService.class.getSimpleName());
    private static final String SOURCE_AMAZON = "AMAZON";

    private final AmazonPriceService amazonPriceService;
    private final ProductRegistryService productRegistryService;

    // ✅ zentraler Writer (macht Listing + Daily-Price)
    private final RemoteActiveListingWriterService listingWriter;

    @Transactional
    public RemoteActiveListing enrichWithAmazonPrice(AmazonMarketplace marketplace,
                                                     String ean,
                                                     String mpn,
                                                     String title) {
        try {
            if (!amazonPriceService.isMarketplaceConfigured(marketplace)) {
                LOGGER.log(Level.FINE, "Marketplace {0} nicht konfiguriert", marketplace);
                return null;
            }

            if ((ean == null || ean.isBlank()) && (mpn == null || mpn.isBlank())) {
                LOGGER.log(Level.FINER, "Weder EAN noch MPN vorhanden – keine Amazon-Abfrage möglich.");
                return null;
            }

            Optional<AmazonPriceResult> resultOpt = amazonPriceService.findBestOffer(marketplace, ean, mpn);
            if (resultOpt.isEmpty()) {
                LOGGER.log(Level.FINE, "Kein Amazon-Angebot gefunden für marketplace={0}, ean={1}, mpn={2}",
                        new Object[]{marketplace, ean, mpn});
                return null;
            }

            AmazonPriceResult result = resultOpt.get();

            // Registry
            registerIdentifiers(ean, mpn, title, result);

            // Identität
            String marketPlaceDomain = normalizeLower(result.marketplaceDomain()); // z.B. amazon.de
            String marketPlaceItemId = normalize(result.asin());                  // ASIN als ID
            if (marketPlaceDomain == null || marketPlaceItemId == null) {
                return null;
            }

            String itemUrl = firstNonBlank(result.affiliateUrl(), result.detailPageUrl());
            BigDecimal price = result.price();
            Currency currency = result.currency();

            // ✅ Schreiben: Identität + Daily price
            return listingWriter.upsertIdentityAndDailyPrice(
                    marketPlaceDomain,
                    marketPlaceItemId,
                    ean,
                    mpn,
                    firstNonBlank(title, result.title()),
                    itemUrl,
                    null,          // merchantImageUrl (wenn du eins hast, hier rein)
                    price,
                    currency,
                    null,          // shippingPrice
                    null           // availableQuantity
            );
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error while enriching Amazon price", ex);
            return null;
        }
    }

    private void registerIdentifiers(String ean, String mpn, String title, AmazonPriceResult result) {
        try {
            if (ean != null && !ean.isBlank()) {
                productRegistryService.register(ProductIdentifier.IdentifierType.EAN, ean, SOURCE_AMAZON);
            }
            if (mpn != null && !mpn.isBlank()) {
                productRegistryService.register(ProductIdentifier.IdentifierType.MPN, mpn, SOURCE_AMAZON);
            }
            if (result.asin() != null && !result.asin().isBlank()) {
                productRegistryService.register(ProductIdentifier.IdentifierType.ASIN, result.asin(), SOURCE_AMAZON);
            }
            String effectiveTitle = firstNonBlank(title, result.title());
            if (effectiveTitle != null && !effectiveTitle.isBlank()) {
                productRegistryService.register(ProductIdentifier.IdentifierType.TITLE, effectiveTitle, SOURCE_AMAZON);
                productRegistryService.register(ProductIdentifier.IdentifierType.TITLE_NORMALIZED, effectiveTitle, SOURCE_AMAZON);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error while registering identifiers for Amazon result: " + result, e);
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private static String normalize(String s) {
        return s == null ? null : s.trim();
    }

    private static String normalizeLower(String s) {
        return s == null ? null : s.trim().toLowerCase();
    }*/
}
