package de.verdox.hwapi.priceapi.component.service.kleinanzeigen;

import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.priceapi.component.service.RemoteActiveListingWriterService;
import de.verdox.hwapi.priceapi.component.service.kleinanzeigen.KleinanzeigenPriceService.KleinanzeigenScrapeReport;
import de.verdox.hwapi.priceapi.io.kleinanzeigen.KleinanzeigenAd;
import de.verdox.hwapi.priceapi.model.C2cMatchReview;
import de.verdox.hwapi.priceapi.model.RemoteActiveListing;
import de.verdox.hwapi.priceapi.repository.C2cMatchReviewRepository;
import de.verdox.hwapi.productid.ProductIdentity;
import de.verdox.hwapi.productidregistry.ProductRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KleinanzeigenPriceServiceTest {

    @Mock
    private ProductRegistryService productRegistryService;
    @Mock
    private RemoteActiveListingWriterService listingWriter;
    @Mock
    private C2cMatchReviewRepository reviewRepository;

    private KleinanzeigenPriceService service;

    @BeforeEach
    void setUp() {
        // Nach der @Mock-Injektion konstruieren – sonst wären die Felder noch null.
        service = new KleinanzeigenPriceService(productRegistryService, listingWriter, reviewRepository);
    }

    private ProductRegistryService.AggregatedSearchResult aggregated(double score, Set<String> eans, Set<String> mpns) {
        return new ProductRegistryService.AggregatedSearchResult(
                eans, Set.of(), Set.of(), mpns, Set.of(), Set.of(), Set.of("KLEINANZEIGEN"), score, List.of());
    }

    private KleinanzeigenAd ad(String id, String title, String price) {
        BigDecimal p = (price == null) ? null : new BigDecimal(price);
        return new KleinanzeigenAd(id, title, p, Currency.EURO, false, "https://kleinanzeigen.de/a" + id, null);
    }

    @Test
    void exactMatchIsTier1WithCodes() {
        when(productRegistryService.searchAggregatedC2C(anyString()))
                .thenReturn(Optional.of(aggregated(1.0, Set.of("4250351047322"), Set.of("RTX4070"))));

        var m = service.match("Grafikkarte RTX 4070 VB");
        assertThat(m.tier()).isEqualTo(1);
        assertThat(m.score()).isEqualTo(1.0);
        assertThat(m.ean()).isEqualTo("4250351047322");
        assertThat(m.mpn()).isEqualTo("RTX4070");
    }

    @Test
    void midScoreIsTier2() {
        when(productRegistryService.searchAggregatedC2C(anyString()))
                .thenReturn(Optional.of(aggregated(0.85, Set.of(), Set.of())));
        assertThat(service.match("etwas unklar 4070").tier()).isEqualTo(2);
    }

    @Test
    void lowScoreAndNoMatchAreTier3() {
        when(productRegistryService.searchAggregatedC2C(anyString()))
                .thenReturn(Optional.of(aggregated(0.70, Set.of(), Set.of())));
        assertThat(service.match("komisch 4070").tier()).isEqualTo(3);

        when(productRegistryService.searchAggregatedC2C(anyString()))
                .thenReturn(Optional.empty());
        var none = service.match("gar nichts dabei");
        assertThat(none.tier()).isEqualTo(3);
        assertThat(none.score()).isEqualTo(0.0);
    }

    @Test
    void tier1WithPriceIsUpserted() {
        when(productRegistryService.searchAggregatedC2C(anyString()))
                .thenReturn(Optional.of(aggregated(1.0, Set.of("4250351047322"), Set.of("RTX4070"))));
        // Der Writer liefert das gespeicherte Listing zurück (Provenienz wird darauf gesetzt).
        when(listingWriter.upsertIdentityAndDailyPrice(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new RemoteActiveListing());

        KleinanzeigenScrapeReport report = service.processAds(List.of(ad("100", "RTX 4070", "599.00")));

        verify(listingWriter, times(1)).upsertIdentityAndDailyPrice(
                any(), any(), any(), eq("100"), eq("4250351047322"), eq("RTX4070"),
                any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(report.tier1()).isEqualTo(1);
    }

    @Test
    void tier2IsNotUpsertedAndGoesToReviewQueue() {
        when(productRegistryService.searchAggregatedC2C(anyString()))
                .thenReturn(Optional.of(aggregated(0.85, Set.of(), Set.of())));
        when(reviewRepository.existsByMarketPlaceItemIdAndTitleNormalized(anyString(), anyString()))
                .thenReturn(false);

        KleinanzeigenScrapeReport report = service.processAds(List.of(ad("200", "RTX 4070 unklar", "599.00")));

        verify(listingWriter, never()).upsertIdentityAndDailyPrice(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(report.tier2()).isEqualTo(1);
        // Der unsichere Titel landet in der Review-Queue (PENDING).
        verify(reviewRepository, times(1)).save(any(C2cMatchReview.class));
    }

    @Test
    void tier1WithoutPriceIsNotUpsertedButGoesToReviewQueue() {
        // VB ohne Richtpreis → sauber gematcht, aber kein Snapshot möglich.
        when(productRegistryService.searchAggregatedC2C(anyString()))
                .thenReturn(Optional.of(aggregated(1.0, Set.of("4250351047322"), Set.of())));
        when(reviewRepository.existsByMarketPlaceItemIdAndTitleNormalized(anyString(), anyString()))
                .thenReturn(false);

        KleinanzeigenScrapeReport report = service.processAds(List.of(ad("300", "RTX 4070 VB", null)));

        verify(listingWriter, never()).upsertIdentityAndDailyPrice(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(report.noPrice()).isEqualTo(1);
        assertThat(report.tier1()).isZero();
        verify(reviewRepository, times(1)).save(any(C2cMatchReview.class));
    }

    @Test
    void existingReviewIsNotDuplicated() {
        when(productRegistryService.searchAggregatedC2C(anyString()))
                .thenReturn(Optional.of(aggregated(0.85, Set.of(), Set.of())));
        when(reviewRepository.existsByMarketPlaceItemIdAndTitleNormalized(anyString(), anyString()))
                .thenReturn(true);

        service.processAds(List.of(ad("200", "RTX 4070 unklar", "599.00")));

        verify(reviewRepository, never()).save(any(C2cMatchReview.class));
    }

    @Test
    void confirmC2cMatchRegistersTitleAndCodesAndMarksConfirmed() {
        C2cMatchReview review = new C2cMatchReview();
        review.setId(7L);
        review.setMarketPlaceItemId("200");
        review.setRawTitle("RTX 4070 unklar wie neu");
        review.setMatchedEan("4250351047322");
        review.setMatchedMpn("RTX4070");
        review.setStatus(C2cMatchReview.Status.PENDING);

        when(reviewRepository.findById(7L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(review)).thenReturn(review);
        ProductIdentity identityStub = new ProductIdentity();
        identityStub.setId(42L);
        when(productRegistryService.confirmC2cTitle(anyString(), any(), any(), anyString()))
                .thenReturn(identityStub);

        ProductIdentity identity = service.confirmC2cMatch(7L, "lukas");

        assertThat(identity).isNotNull();
        // Lern-Schleife: Titel + Codes werden in die Product-Identity übernommen.
        verify(productRegistryService, times(1)).confirmC2cTitle(
                "RTX 4070 unklar wie neu", List.of("4250351047322"), List.of("RTX4070"),
                KleinanzeigenPriceService.SOURCE);
        // Review wird bestätigt.
        assertThat(review.getStatus()).isEqualTo(C2cMatchReview.Status.CONFIRMED);
        assertThat(review.getConfirmedBy()).isEqualTo("lukas");
        // Kein Preis-Snapshot aus der Bestätigung (Preis läuft beim nächsten Scrape ein).
        verify(listingWriter, never()).upsertIdentityAndDailyPrice(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
