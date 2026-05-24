package de.verdox.hwapi.productidregistry;

import de.verdox.hwapi.productid.ProductIdentifier;
import de.verdox.hwapi.productid.ProductIdentifierRepository;
import de.verdox.hwapi.productid.ProductIdentity;
import de.verdox.hwapi.productid.ProductIdentityRepository;
import de.verdox.hwapi.productid.dto.ProductSearchResultDTO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class
ProductRegistryService {

    private static final Pattern MAIN_DIGITS_PATTERN = Pattern.compile("\\b(\\d{3,5})(?!\\s*(gb|g|ghz|mhz|w))\\b");

    private static final double MIN_FUZZY_SCORE = 0.80;

    private final ProductIdentifierRepository productIdentifierRepository;
    private final ProductIdentityRepository productIdentityRepository;

    // ------------------------------------------------------------------------
    // Öffentliche Suche: Bester Match
    // ------------------------------------------------------------------------

    /**
     * Findet den "besten" Identifier für den gegebenen Input,
     * ohne dass vorher ein IdentifierType angegeben werden muss.
     */
    @Transactional(readOnly = true)
    public Optional<BestMatch> findBestMatch(String rawInput) {
        List<BestMatch> matches = findMatches(rawInput);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(matches.get(0)); // bereits nach Score sortiert
    }

    // ------------------------------------------------------------------------
    // Öffentliche Suche: Aggregiertes Ergebnis
    // ------------------------------------------------------------------------

    /**
     * Führt eine Suche mit allen Matching-Techniken (exakt + fuzzy) aus
     * und fasst alle Identifier der BESTEN Identity zu einem maximalen Ergebnis zusammen.
     *
     * D.h. es wird NICHT über mehrere unterschiedliche Identities aggregiert,
     * damit z.B. Ryzen 7 2700 und 2700X nicht vermischt werden.
     */
    @Transactional(readOnly = true)
    public Optional<AggregatedSearchResult> searchAggregated(String rawInput) {
        List<BestMatch> matches = findMatches(rawInput);
        if (matches.isEmpty()) {
            return Optional.empty();
        }

        double bestScore = matches.getFirst().score();
        BestMatch primaryMatch = matches.getFirst();
        ProductIdentifier primaryIdentifier = primaryMatch.identifier();

        Set<ProductIdentifier> allIdentifiers = new HashSet<>();

        if (primaryIdentifier.getIdentity() != null
                && primaryIdentifier.getIdentity().getIdentifiers() != null) {
            allIdentifiers.addAll(primaryIdentifier.getIdentity().getIdentifiers());
        } else {
            allIdentifiers.add(primaryIdentifier);
        }

        for (BestMatch match : matches) {
            ProductIdentifier pi = match.identifier();
            if (pi.getIdentity() != null
                    && primaryIdentifier.getIdentity() != null
                    && Objects.equals(pi.getIdentity().getId(), primaryIdentifier.getIdentity().getId())) {
                allIdentifiers.add(pi);
            }
        }

        // jetzt nach Typ aufsplitten
        Set<String> eans = new HashSet<>();
        Set<String> upcs = new HashSet<>();
        Set<String> gtins = new HashSet<>();
        Set<String> mpns = new HashSet<>();
        Set<String> titles = new HashSet<>();
        Set<String> titlesNormalized = new HashSet<>();
        Set<String> sources = new HashSet<>();

        for (ProductIdentifier pi : allIdentifiers) {
            switch (pi.getType()) {
                case EAN -> eans.add(pi.getIdentifier());
                case UPC -> upcs.add(pi.getIdentifier());
                case GTIN -> gtins.add(pi.getIdentifier());
                case MPN -> mpns.add(pi.getIdentifier());
                case TITLE -> titles.add(pi.getIdentifier());
                case TITLE_NORMALIZED -> titlesNormalized.add(pi.getIdentifier());
            }
            if (pi.getSources() != null) {
                sources.addAll(pi.getSources());
            }
        }

        AggregatedSearchResult result = new AggregatedSearchResult(
                Collections.unmodifiableSet(eans),
                Collections.unmodifiableSet(upcs),
                Collections.unmodifiableSet(gtins),
                Collections.unmodifiableSet(mpns),
                Collections.unmodifiableSet(titles),
                Collections.unmodifiableSet(titlesNormalized),
                Collections.unmodifiableSet(sources),
                bestScore,
                List.copyOf(matches)
        );

        return Optional.of(result);
    }

    // ------------------------------------------------------------------------
    // Interne Matching-Logik: ALLE relevanten Treffer
    // ------------------------------------------------------------------------

    /**
     * Sucht alle relevanten Matches für den Input.
     *
     * - Exakte Matches für numerische Codes (EAN/GTIN/UPC)
     * - Exakte MPN
     * - Exakte TITLE_NORMALIZED
     * - Fuzzy TITLE_NORMALIZED mit:
     *   - Substring-Heuristik (wenn einer String den anderen enthält)
     *   - dynamischem Threshold für kurze Suchbegriffe
     */
    @Transactional(readOnly = true)
    protected List<BestMatch> findMatches(String rawInput) {
        if (rawInput == null) {
            return List.of();
        }

        String trimmed = rawInput.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }

        Map<Long, BestMatch> byId = new HashMap<>();

        // 1) Numerischer Code (EAN/UPC/GTIN) – exakte Matches haben höchste Priorität (Score 1.0)
        String digitsOnly = normalizeNumericCode(trimmed);
        if (!digitsOnly.isEmpty()) {
            for (ProductIdentifier.IdentifierType type : List.of(
                    ProductIdentifier.IdentifierType.EAN,
                    ProductIdentifier.IdentifierType.GTIN,
                    ProductIdentifier.IdentifierType.UPC
            )) {
                productIdentifierRepository.findByTypeAndIdentifier(type, digitsOnly)
                        .ifPresent(pi -> putOrMax(byId, pi, 1.0));
            }
        }

        // 2) MPN-Exact-Match
        String normalizedMpn = normalizeMpn(trimmed);
        productIdentifierRepository.findByTypeAndIdentifier(ProductIdentifier.IdentifierType.MPN, normalizedMpn)
                .ifPresent(pi -> putOrMax(byId, pi, 1.0));

        // 3) TITLE_NORMALIZED: exakt + fuzzy
        String normalizedTitle = normalizeTitle(trimmed);

        productIdentifierRepository.findByTypeAndIdentifier(
                        ProductIdentifier.IdentifierType.TITLE_NORMALIZED, normalizedTitle
                )
                .ifPresent(pi -> putOrMax(byId, pi, 1.0));


        String fragment = chooseSearchFragment(normalizedTitle);

        List<ProductIdentifier> candidates =
                productIdentifierRepository.findTop50ByTypeAndIdentifierContaining(
                        ProductIdentifier.IdentifierType.TITLE_NORMALIZED, fragment
                );

        if (!candidates.isEmpty()) {
            JaroWinklerSimilarity similarity = new JaroWinklerSimilarity();

            String queryDigits = extractMainDigits(normalizedTitle);

            for (ProductIdentifier candidate : candidates) {
                String candidateValue = candidate.getIdentifier();

                String a = normalizedTitle;
                String b = candidateValue;

                double score;

                // 1) Substring-Heuristik
                if (b.contains(a) || a.contains(b)) {
                    score = (double) Math.min(a.length(), b.length()) / Math.max(a.length(), b.length());
                    if (score < 0.9) {
                        score = 0.9;
                    }
                } else {
                    // 2) normaler Jaro-Winkler
                    score = similarity.apply(a, b);
                }

                // 3) Domain-Heuristik: Haupt-Ziffernfolge (z.B. 9900, 2700, 4070, 580)
                String candidateDigits = extractMainDigits(b);

                if (queryDigits != null && candidateDigits != null) {
                    if (queryDigits.equals(candidateDigits)) {
                        // gleiche Modellnummer -> boosten
                        score = Math.min(1.0, score + 0.1);
                    } else {
                        // andere Modellnummer -> leicht abwerten
                        score = Math.max(0.0, score - 0.1);
                    }
                }

                // 4) dynamischer Threshold: bei kurzen Queries toleranter
                double dynamicThreshold = MIN_FUZZY_SCORE;
                int len = a.length();
                if (len <= 6) {
                    dynamicThreshold = 0.6;
                } else if (len <= 10) {
                    dynamicThreshold = 0.7;
                }

                if (score >= dynamicThreshold) {
                    putOrMax(byId, candidate, score);
                }
            }
        }

        // Map -> Liste, nach Score absteigend sortieren
        return byId.values().stream()
                .sorted(Comparator.comparingDouble(BestMatch::score).reversed())
                .toList();
    }

    @Transactional
    public ProductIdentity registerProductFromSearchResult(ProductSearchResultDTO dto, String source) {
        List<ProductIdentifier> identifiers = new ArrayList<>();

        if (dto.eans() != null) {
            identifiers.addAll(
                    registerAll(ProductIdentifier.IdentifierType.EAN, dto.eans(), source).values()
            );
        }

        if (dto.upcs() != null) {
            identifiers.addAll(
                    registerAll(ProductIdentifier.IdentifierType.UPC, dto.upcs(), source).values()
            );
        }

        if (dto.gtins() != null) {
            identifiers.addAll(
                    registerAll(ProductIdentifier.IdentifierType.GTIN, dto.gtins(), source).values()
            );
        }

        if (dto.mpns() != null) {
            identifiers.addAll(
                    registerAll(ProductIdentifier.IdentifierType.MPN, dto.mpns(), source).values()
            );
        }

        if (dto.title() != null && !dto.title().isBlank()) {
            ProductIdentifier titleId = register(ProductIdentifier.IdentifierType.TITLE, dto.title(), source);
            ProductIdentifier titleNormId = register(ProductIdentifier.IdentifierType.TITLE_NORMALIZED, dto.title(), source);
            identifiers.add(titleId);
            identifiers.add(titleNormId);
        }

        Set<ProductIdentity> existingIdentities = identifiers.stream()
                .map(ProductIdentifier::getIdentity)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        ProductIdentity targetIdentity;

        if (existingIdentities.isEmpty()) {
            targetIdentity = new ProductIdentity();
            targetIdentity = productIdentityRepository.save(targetIdentity);
        } else if (existingIdentities.size() == 1) {
            targetIdentity = existingIdentities.iterator().next();
        } else {
            Iterator<ProductIdentity> it = existingIdentities.iterator();
            targetIdentity = it.next();
            while (it.hasNext()) {
                ProductIdentity other = it.next();
                for (ProductIdentifier pi : other.getIdentifiers()) {
                    pi.setIdentity(targetIdentity);
                }
                targetIdentity.getIdentifiers().addAll(other.getIdentifiers());
                productIdentityRepository.delete(other);
            }
        }

        for (ProductIdentifier identifier : identifiers) {
            if (identifier.getIdentity() == null || !identifier.getIdentity().equals(targetIdentity)) {
                identifier.setIdentity(targetIdentity);
            }
        }

        return targetIdentity;
    }

    private void putOrMax(Map<Long, BestMatch> map, ProductIdentifier identifier, double score) {
        if (identifier.getId() == null) {
            // sollte nicht passieren, da aus DB geladen – fallback
            long key = System.identityHashCode(identifier);
            BestMatch existing = map.get(key);
            if (existing == null || score > existing.score()) {
                map.put(key, new BestMatch(identifier, score));
            }
            return;
        }
        BestMatch existing = map.get(identifier.getId());
        if (existing == null || score > existing.score()) {
            map.put(identifier.getId(), new BestMatch(identifier, score));
        }
    }

    // ------------------------------------------------------------------------
    // Registrieren (mit/ohne Source)
    // ------------------------------------------------------------------------

    @Transactional
    public ProductIdentifier register(ProductIdentifier.IdentifierType type, String rawValue) {
        return register(type, rawValue, null);
    }

    @Transactional
    public ProductIdentifier register(ProductIdentifier.IdentifierType type, String rawValue, String source) {
        String normalizedValue = normalize(type, rawValue);

        ProductIdentifier identifier = productIdentifierRepository
                .findByTypeAndIdentifier(type, normalizedValue)
                .orElseGet(() -> {
                    ProductIdentifier pi = new ProductIdentifier();
                    pi.setType(type);
                    pi.setIdentifier(normalizedValue);
                    return pi;
                });

        if (source != null && !source.isBlank()) {
            if (identifier.getSources() == null) {
                identifier.setSources(new HashSet<>());
            }
            identifier.getSources().add(source);
        }

        return productIdentifierRepository.save(identifier);
    }

    @Transactional
    public Map<String, ProductIdentifier> registerAll(ProductIdentifier.IdentifierType type,
                                                      Collection<String> rawValues) {
        return registerAll(type, rawValues, null);
    }

    @Transactional
    public Map<String, ProductIdentifier> registerAll(ProductIdentifier.IdentifierType type,
                                                      Collection<String> rawValues,
                                                      String source) {
        if (rawValues == null || rawValues.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> normalizedToOriginal = rawValues.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toMap(
                        v -> normalize(type, v),
                        v -> v,
                        (a, b) -> a
                ));

        Set<String> normalizedValues = normalizedToOriginal.keySet();

        List<ProductIdentifier> existing = productIdentifierRepository
                .findAllByTypeAndIdentifierIn(type, normalizedValues);

        Map<String, ProductIdentifier> byValue = existing.stream()
                .collect(Collectors.toMap(ProductIdentifier::getIdentifier, pi -> pi));

        List<ProductIdentifier> toCreate = normalizedValues.stream()
                .filter(v -> !byValue.containsKey(v))
                .map(v -> {
                    ProductIdentifier pi = new ProductIdentifier();
                    pi.setType(type);
                    pi.setIdentifier(v);
                    return pi;
                })
                .toList();

        if (!toCreate.isEmpty()) {
            List<ProductIdentifier> created = productIdentifierRepository.saveAll(toCreate);
            created.forEach(pi -> byValue.put(pi.getIdentifier(), pi));
        }

        if (source != null && !source.isBlank()) {
            for (ProductIdentifier pi : byValue.values()) {
                if (pi.getSources() == null) {
                    pi.setSources(new HashSet<>());
                }
                pi.getSources().add(source);
            }
            productIdentifierRepository.saveAll(byValue.values());
        }

        return byValue;
    }

    // ------------------------------------------------------------------------
    // Lookup / Utility
    // ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<ProductIdentifier> find(ProductIdentifier.IdentifierType type, String rawValue) {
        if (rawValue == null) {
            return Optional.empty();
        }
        String normalized = normalize(type, rawValue);
        return productIdentifierRepository.findByTypeAndIdentifier(type, normalized);
    }

    @Transactional(readOnly = true)
    public boolean exists(ProductIdentifier.IdentifierType type, String rawValue) {
        if (rawValue == null) {
            return false;
        }
        String normalized = normalize(type, rawValue);
        return productIdentifierRepository.existsByTypeAndIdentifier(type, normalized);
    }

    @Transactional(readOnly = true)
    public List<ProductIdentifier> findAllByType(ProductIdentifier.IdentifierType type) {
        return productIdentifierRepository.findAllByType(type);
    }

    // ------------------------------------------------------------------------
    // Normalisierung
    // ------------------------------------------------------------------------

    private String normalize(ProductIdentifier.IdentifierType type, String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();

        if (trimmed.isEmpty()) {
            return trimmed;
        }

        return switch (type) {
            case TITLE -> trimmed;
            case TITLE_NORMALIZED -> normalizeTitle(trimmed);
            case MPN -> normalizeMpn(trimmed);
            case ASIN -> value;
            case EAN, UPC, GTIN -> normalizeNumericCode(trimmed);
        };
    }

    private String extractMainDigits(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }

        Matcher matcher = MAIN_DIGITS_PATTERN.matcher(s);
        String best = null;

        while (matcher.find()) {
            String g = matcher.group(1);
            // wir nehmen die längste passende Sequenz (z.B. 12400 vor 400)
            if (best == null || g.length() > best.length()) {
                best = g;
            }
        }

        return best;
    }

    private String chooseSearchFragment(String normalizedTitle) {
        if (normalizedTitle == null || normalizedTitle.isBlank()) {
            return "";
        }

        String[] tokens = normalizedTitle.split("\\s+");
        String bestWithDigits = null;
        String bestFallback = null;

        for (String t : tokens) {
            if (t.length() < 3) {
                continue;
            }

            boolean hasDigit = t.chars().anyMatch(Character::isDigit);
            if (hasDigit) {
                // Bevorzuge Tokens mit Ziffern (z.B. 500dx, 9900k, 4070)
                if (bestWithDigits == null || t.length() > bestWithDigits.length()) {
                    bestWithDigits = t;
                }
            } else {
                // Merke längsten "normalen" Token als Fallback
                if (bestFallback == null || t.length() > bestFallback.length()) {
                    bestFallback = t;
                }
            }
        }

        if (bestWithDigits != null) {
            return bestWithDigits;
        }
        if (bestFallback != null) {
            return bestFallback;
        }

        // letzter Fallback: erste 15 Zeichen
        String fragment = normalizedTitle;
        if (fragment.length() > 15) {
            fragment = fragment.substring(0, 15);
        }
        return fragment;
    }

    /**
     * Aggressive Titel-Normalisierung:
     * - Klammern-Inhalt entfernen
     * - Rauschwörter (processor, cpu, oem, tray, boxed, etc.) raus
     * - GHz/Core-Angaben entfernen
     * - alles Nicht-Alphanumerische -> Space
     */
    private String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }

        String lower = title.toLowerCase(Locale.ROOT);

        // 1) Inhalte in runden Klammern entfernen
        lower = lower.replaceAll("\\([^)]*\\)", " ");

        // 2) typische Rausch-Wörter entfernen
        lower = lower.replaceAll("\\bprocessor\\b", " ");
        lower = lower.replaceAll("\\bcpu\\b", " ");
        lower = lower.replaceAll("\\boem\\b", " ");
        lower = lower.replaceAll("\\btray\\b", " ");
        lower = lower.replaceAll("\\bbox(ed)?\\b", " ");
        lower = lower.replaceAll("\\bretail\\b", " ");
        lower = lower.replaceAll("\\bedition\\b", " ");

        // 3) GHz-Angaben entfernen: "3.7 ghz", "3,2ghz", etc.
        lower = lower.replaceAll("\\b[0-9]+([\\.,][0-9]+)?\\s*ghz\\b", " ");

        // 4) Core-Angaben entfernen: "8-core", "8 core"
        lower = lower.replaceAll("\\b[0-9]+\\s*core(s)?\\b", " ");

        // 5) Restliches Nicht-Alphanumerisches zu Leerzeichen
        lower = lower.replaceAll("[^a-z0-9]+", " ");

        // 6) Whitespace normalisieren
        lower = lower.replaceAll("\\s+", " ").trim();

        return lower;
    }

    private String normalizeMpn(String mpn) {
        String upper = mpn.toUpperCase(Locale.ROOT);
        upper = upper.replaceAll("\\s+", "");
        return upper.trim();
    }

    private String normalizeNumericCode(String code) {
        String digitsOnly = code.replaceAll("[^0-9]", "");
        return digitsOnly.trim();
    }

    // ------------------------------------------------------------------------
    // Records
    // ------------------------------------------------------------------------

    public record BestMatch(ProductIdentifier identifier, double score) {
    }

    public record AggregatedSearchResult(
            Set<String> eans,
            Set<String> upcs,
            Set<String> gtins,
            Set<String> mpns,
            Set<String> titles,
            Set<String> titlesNormalized,
            Set<String> sources,
            double bestScore,
            List<BestMatch> matches
    ) {
    }
}
