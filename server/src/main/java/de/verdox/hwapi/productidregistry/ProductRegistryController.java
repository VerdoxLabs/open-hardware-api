package de.verdox.hwapi.productidregistry;

import de.verdox.hwapi.productid.ProductIdentifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/product-registry")
@RequiredArgsConstructor
public class ProductRegistryController {

    private final ProductRegistryService productRegistryService;

    /**
     * Liefert den "besten" Identifier-Match zu einem Suchstring.
     *
     * Beispiel:
     * GET /api/product-registry/search/best?q=AMD%20Ryzen%205%208400F
     */
    @GetMapping("/search/best")
    public ResponseEntity<BestMatchResponse> searchBest(@RequestParam("q") String query) {
        return productRegistryService.findBestMatch(query)
                .map(best -> ResponseEntity.ok(toBestMatchResponse(best)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Liefert ein aggregiertes Ergebnis aus allen gefundenen Matches,
     * inkl. aller bekannten EANs, MPNs, Titel, Quellen usw.
     *
     * Beispiel:
     * GET /api/product-registry/search/aggregated?q=AMD%20Ryzen%205%208400F
     */
    @GetMapping("/search/aggregated")
    public ResponseEntity<AggregatedSearchResponse> searchAggregated(@RequestParam("q") String query) {
        return productRegistryService.searchAggregated(query)
                .map(result -> ResponseEntity.ok(toAggregatedResponse(result)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------
    // Mapper
    // -------------------------------------------------------------

    private BestMatchResponse toBestMatchResponse(ProductRegistryService.BestMatch bestMatch) {
        ProductIdentifier identifier = bestMatch.identifier();

        Long identityId = identifier.getIdentity() != null
                ? identifier.getIdentity().getId()
                : null;

        return new BestMatchResponse(
                identifier.getId(),
                identityId,
                identifier.getType().name(),
                identifier.getIdentifier(),
                identifier.getSources() != null
                        ? Set.copyOf(identifier.getSources())
                        : Set.of(),
                bestMatch.score()
        );
    }

    private AggregatedSearchResponse toAggregatedResponse(ProductRegistryService.AggregatedSearchResult result) {
        return new AggregatedSearchResponse(
                result.eans(),
                result.upcs(),
                result.gtins(),
                result.mpns(),
                result.titles(),
                result.titlesNormalized(),
                result.sources(),
                result.bestScore(),
                result.matches().stream()
                        .map(this::toBestMatchResponse)
                        .collect(Collectors.toList())
        );
    }

    // -------------------------------------------------------------
    // DTOs für die API (kannst du auch in eigene Dateien packen)
    // -------------------------------------------------------------

    public record BestMatchResponse(
            Long id,
            Long identityId,
            String type,
            String value,
            Set<String> sources,
            double score
    ) {
    }

    public record AggregatedSearchResponse(
            Set<String> eans,
            Set<String> upcs,
            Set<String> gtins,
            Set<String> mpns,
            Set<String> titles,
            Set<String> titlesNormalized,
            Set<String> sources,
            double bestScore,
            java.util.List<BestMatchResponse> matches
    ) {
    }
}
