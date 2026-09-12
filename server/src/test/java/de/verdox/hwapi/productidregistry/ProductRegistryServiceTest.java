package de.verdox.hwapi.productidregistry;

import de.verdox.hwapi.productid.ProductIdentifier;
import de.verdox.hwapi.productid.ProductIdentifier.IdentifierType;
import de.verdox.hwapi.productid.ProductIdentifierRepository;
import de.verdox.hwapi.productid.ProductIdentity;
import de.verdox.hwapi.productid.ProductIdentityRepository;
import de.verdox.hwapi.productid.dto.ProductSearchResultDTO;
import de.verdox.hwapi.util.C2CTitleCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRegistryServiceTest {

    private static final IdentifierType TITLE_NORMALIZED = IdentifierType.TITLE_NORMALIZED;
    private static final IdentifierType TITLE = IdentifierType.TITLE;
    private static final IdentifierType EAN = IdentifierType.EAN;

    @Mock
    private ProductIdentifierRepository productIdentifierRepository;
    @Mock
    private ProductIdentityRepository productIdentityRepository;

    private ProductRegistryService service;

    @BeforeEach
    void setUp() {
        service = new ProductRegistryService(productIdentifierRepository, productIdentityRepository);
        // Default: keine exakten Code/MPN/Title-Treffer. Einzelne Tests übersteuern gezielt.
        lenient().when(productIdentifierRepository.findByTypeAndIdentifier(any(), any()))
                .thenReturn(Optional.empty());
    }

    private ProductIdentifier identifier(long id, ProductIdentifier.IdentifierType type, String value) {
        ProductIdentifier pi = new ProductIdentifier();
        pi.setId(id);
        pi.setType(type);
        pi.setIdentifier(value);
        return pi;
    }

    @Test
    void c2cExactTitleMatchAfterCleaningSurfacesIdentityCodes() {
        ProductIdentity identity = new ProductIdentity();
        identity.setId(10L);
        ProductIdentifier title = identifier(1L, TITLE_NORMALIZED, "nvidia geforce rtx 4070 super");
        ProductIdentifier ean = identifier(2L, EAN, "4250351047322");
        title.setIdentity(identity);
        ean.setIdentity(identity);
        identity.getIdentifiers().add(title);
        identity.getIdentifiers().add(ean);

        when(productIdentifierRepository.findByTypeAndIdentifier(
                TITLE_NORMALIZED, "nvidia geforce rtx 4070 super"))
                .thenReturn(Optional.of(title));

        // Roher, verrauschter C2C-Titel wird bereinigt -> exakter 1.0-Treffer.
        var result = service.searchAggregatedC2C("Nvidia GeForce RTX 4070 Super VB Festpreis").orElseThrow();

        assertThat(result.bestScore()).isEqualTo(1.0);
        assertThat(result.eans()).contains("4250351047322");
    }

    @Test
    void fuzzyMatchRespectsComponentType() {
        ProductIdentifier gpu = identifier(20L, TITLE_NORMALIZED, "geforce rtx 4070 super");
        ProductIdentifier cpu = identifier(21L, TITLE_NORMALIZED, "cpu ryzen 5600x 4070");

        when(productIdentifierRepository.findTop50ByTypeAndIdentifierContaining(
                TITLE_NORMALIZED, "4070"))
                .thenReturn(List.of(gpu, cpu));

        // GPU-Query ("grafikkarte") soll die CPU-Kandidatin ausschließen (Type-Gating).
        var result = service.searchAggregated("Grafikkarte RTX 4070 Super").orElseThrow();

        assertThat(result.matches())
                .extracting(m -> m.identifier().getId())
                .contains(20L)
                .doesNotContain(21L);
        assertThat(result.matches().getFirst().identifier().getId()).isEqualTo(20L);
    }

    @Test
    void registerC2CTitleStoresNormalizedIdentifierForLearning() {
        when(productIdentifierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String raw = "RTX 4070 Super wie neu VB";
        ProductIdentifier normalized = service.registerC2CTitle(raw, "KLEINANZEIGEN");

        // Lernschleife: derselbe wiederkehrende Titel wird künftig exakt (1.0) gematcht,
        // weil die TITLE_NORMALIZED-Form bereinigt und konsistent gespeichert wird.
        assertThat(normalized.getType()).isEqualTo(TITLE_NORMALIZED);
        assertThat(normalized.getIdentifier()).isEqualTo("rtx 4070 super");
        assertThat(normalized.getIdentifier()).isEqualTo(C2CTitleCleaner.clean(raw));
        assertThat(normalized.getSources()).contains("KLEINANZEIGEN");
    }

    @Test
    void registerProductFromSearchResultLinksIdentifiersToNewIdentity() {
        when(productIdentifierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductIdentity identity = service.registerProductFromSearchResult(
                ProductSearchResultDTO.builder().title("Bequiet Power 12 850W").build(),
                "KLEINANZEIGEN");

        assertThat(identity).isNotNull();

        ArgumentCaptor<ProductIdentifier> saved = ArgumentCaptor.forClass(ProductIdentifier.class);
        verify(productIdentifierRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(pi -> assertThat(pi.getIdentity()).isSameAs(identity));
        assertThat(saved.getAllValues())
                .extracting(ProductIdentifier::getType)
                .containsExactlyInAnyOrder(TITLE, TITLE_NORMALIZED);
    }
}
