package de.verdox.hwapi.identity.application;

import de.verdox.hwapi.identity.ProductIdentifier;
import de.verdox.hwapi.identity.ProductIdentifierRepository;
import de.verdox.hwapi.identity.ProductIdentity;
import de.verdox.hwapi.identity.ProductIdentityRepository;
import de.verdox.hwapi.identity.dto.ProductSearchResultDTO;
import de.verdox.hwapi.identity.IdentifierNormalizer;
import de.verdox.hwapi.identity.support.C2CTitleCleaner;
import de.verdox.hwapi.identity.support.ComponentTypeClassifier;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class
ProductRegistryService {

    private static final Pattern MAIN_DIGITS_PATTERN = Pattern.compile("\\b(\\d{3,5})(?!\\s*(gb|g|ghz|mhz|w))\\b");

    private static final double MIN_FUZZY_SCORE = 0.80;
    /**
     * Serializes the lookup/create sequence for an identifier in this application instance.
     * The database unique index remains the final cross-instance safeguard.
     */
    private static final ReentrantLock[] REGISTRATION_LOCKS = createRegistrationLocks();

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
                case EAN -> eans.add(pi.getValue());
                case UPC -> upcs.add(pi.getValue());
                case GTIN -> gtins.add(pi.getValue());
                case MPN -> mpns.add(pi.getValue());
                case TITLE -> titles.add(pi.getValue());
                case TITLE_NORMALIZED -> titlesNormalized.add(pi.getValue());
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
    // C2C-Suche (Kleinanzeigen & Co.): erst Rauschen weg, dann Matchen
    // ------------------------------------------------------------------------

    /**
     * C2C-Variante von {@link #findBestMatch(String)}. Der rohe Inseratstitel wird zuerst
     * mit {@link C2CTitleCleaner} von Verkaufs-/Zustands-/Marketingbegriffen befreit,
     * bevor er gematcht wird. Damit bleiben die exakten {@code TITLE_NORMALIZED}
     * Treffer der Lernschleife konsistent – vorausgesetzt, auch die Registrierung
     * ({@link #registerC2CTitle}) wendet denselben Cleaner an.
     */
    @Transactional(readOnly = true)
    public Optional<BestMatch> findBestMatchC2C(String rawTitle) {
        String cleaned = C2CTitleCleaner.clean(rawTitle);
        if (cleaned == null || cleaned.isBlank()) {
            return Optional.empty();
        }
        return findBestMatch(cleaned);
    }

    /**
     * C2C-Variante von {@link #searchAggregated(String)}. Siehe {@link #findBestMatchC2C}.
     * Das Ergebnis fasst wie gewöhnlich alle Identifier der besten Identity zusammen.
     */
    @Transactional(readOnly = true)
    public Optional<AggregatedSearchResult> searchAggregatedC2C(String rawTitle) {
        String cleaned = C2CTitleCleaner.clean(rawTitle);
        if (cleaned == null || cleaned.isBlank()) {
            return Optional.empty();
        }
        return searchAggregated(cleaned);
    }

    /**
     * Registriert einen <b>bestätigten</b> C2C-Titel in der Registry und liefert den
     * {@code TITLE_NORMALIZED}-Identifier zurück, dessen Identity die Lernschleife dann auf
     * die bestätigte Produkt-Identity zeigen lassen muss.
     *
     * <p>Konsistenz-Garantie: Der Titel wird identisch zu {@link #searchAggregatedC2C}
     * normalisiert ({@code normalizeTitle(clean(raw))}), damit derselbe wiederkehrende
     * Titel beim nächsten C2C-Lauf ein <b>exakter</b> 1.0-Treffer wird – das ist der
     * Lerneffekt. Der rohe {@code TITLE}-Identifier wird zusätzlich für die Anzeige
     * gespeichert (ohne Normalisierung).
     *
     * @return der gespeicherte {@code TITLE_NORMALIZED}-Identifier (Identity noch leer)
     */
    @Transactional
    public ProductIdentifier registerC2CTitle(String rawTitle, String source) {
        String cleaned = C2CTitleCleaner.clean(rawTitle);
        register(ProductIdentifier.IdentifierType.TITLE, rawTitle, source);
        return register(ProductIdentifier.IdentifierType.TITLE_NORMALIZED, cleaned, source);
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

        findRegisteredIdentifier(ProductIdentifier.IdentifierType.TITLE_NORMALIZED, normalizedTitle)
                .ifPresent(pi -> putOrMax(byId, pi, 1.0));


        String fragment = chooseSearchFragment(normalizedTitle);

        List<ProductIdentifier> candidates =
                productIdentifierRepository.findTop50ByTypeAndIdentifierContaining(
                        ProductIdentifier.IdentifierType.TITLE_NORMALIZED, fragment
                );

        if (!candidates.isEmpty()) {
            JaroWinklerSimilarity similarity = new JaroWinklerSimilarity();

            String queryDigits = extractMainDigits(normalizedTitle);

            // Type-Gating: eine konkrete Komponentenart in der Query (z.B. "grafikkarte rtx 4070")
            // soll keine andere konkrete Art matchen (z.B. keine CPU). PC/OTHER/UNKNOWN bleiben
            // durchlässig, damit Bundles und unklare Titel nicht unnötig gefiltert werden.
            ComponentTypeClassifier.ComponentKind queryKind =
                    ComponentTypeClassifier.classify(normalizedTitle);

            for (ProductIdentifier candidate : candidates) {
                String candidateValue = candidate.getIdentifier();

                if (queryKind.isComponentType()
                        && !queryKind.equals(ComponentTypeClassifier.classify(candidateValue))) {
                    continue;
                }

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

        return mergeIdentifiersIntoIdentity(identifiers);
    }

    /**
     * Bestätigt einen C2C-Titel (Lernschleife): registriert die bestätigten Codes
     * (EAN/MPN) und den Titel und fasst ALLES in einer Identity zusammen.
     *
     * <p>Titel-Normalisierung ist C2C-konsistent ({@code C2CTitleCleaner} +
     * {@code normalizeTitle}), sodass derselbe wiederkehrende Titel bei einer
     * {@link #searchAggregatedC2C}-Suche künftig ein <b>exakter 1.0-Treffer</b>
     * wird – der Algorithmus lernt aus jeder Bestätigung.
     *
     * @param rawTitle roher C2C-Titel
     * @param eans     bestätigte EAN(s); null/leer erlaubt (nur-Titel-Bestätigung)
     * @param mpns     bestätigte MPN(s); null/leer erlaubt
     * @param source   z. B. "KLEINANZEIGEN"
     * @return die (ggf. neu geschaffene bzw. gemergte) Identity
     */
    @Transactional
    public ProductIdentity confirmC2cTitle(String rawTitle, Collection<String> eans,
                                           Collection<String> mpns, String source) {
        List<ProductIdentifier> identifiers = new ArrayList<>();

        if (eans != null && !eans.isEmpty()) {
            identifiers.addAll(
                    registerAll(ProductIdentifier.IdentifierType.EAN, eans, source).values()
            );
        }

        if (mpns != null && !mpns.isEmpty()) {
            identifiers.addAll(
                    registerAll(ProductIdentifier.IdentifierType.MPN, mpns, source).values()
            );
        }

        ProductIdentifier titleId = register(ProductIdentifier.IdentifierType.TITLE, rawTitle, source);
        ProductIdentifier titleNormId =
                register(ProductIdentifier.IdentifierType.TITLE_NORMALIZED, C2CTitleCleaner.clean(rawTitle), source);
        identifiers.add(titleId);
        identifiers.add(titleNormId);

        return mergeIdentifiersIntoIdentity(identifiers);
    }

    /** Reassigns a learned C2C title before registering its corrected product codes. */
    @Transactional
    public ProductIdentity reassignC2cTitle(String rawTitle, Collection<String> eans,
                                            Collection<String> mpns, String source) {
        String cleaned = C2CTitleCleaner.clean(rawTitle);
        detach(ProductIdentifier.IdentifierType.TITLE, rawTitle);
        detach(ProductIdentifier.IdentifierType.TITLE_NORMALIZED, cleaned);
        return confirmC2cTitle(rawTitle, eans, mpns, source);
    }

    private void detach(ProductIdentifier.IdentifierType type, String value) {
        if (value == null || value.isBlank()) return;
        productIdentifierRepository.findByTypeAndIdentifier(type, value).ifPresent(identifier -> {
            ProductIdentity identity = identifier.getIdentity();
            if (identity != null) identity.getIdentifiers().remove(identifier);
            identifier.setIdentity(null);
            productIdentifierRepository.save(identifier);
        });
    }

    /**
     * Fasst die Identifiers in genau einer Identity zusammen:
     * keine vorhanden → neue Identity; genau eine → übernehmen; mehrere → mergen
     * (alle Identifier der Verlierer-Identities zur Ziel-Identity umhängen).
     */
    private ProductIdentity mergeIdentifiersIntoIdentity(List<ProductIdentifier> identifiers) {
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
        if (normalizedValue == null || normalizedValue.isBlank()) {
            throw new IllegalArgumentException("Identifier value must be valid for type " + type);
        }

        ReentrantLock lock = lockFor(type, normalizedValue);
        lock.lock();
        try {
            ProductIdentifier identifier = findRegisteredIdentifier(type, normalizedValue)
                    .orElseGet(() -> newIdentifier(type, rawValue, normalizedValue));

            if (source != null && !source.isBlank()) {
                if (identifier.getSources() == null) {
                    identifier.setSources(new HashSet<>());
                }
                identifier.getSources().add(source);
            }

            return productIdentifierRepository.save(identifier);
        } finally {
            lock.unlock();
        }
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
                .map(value -> Map.entry(normalize(type, value), value))
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a
                ));

        Map<String, ProductIdentifier> byValue = new LinkedHashMap<>();
        // Do not bulk-create after a single pre-flight query. That leaves a race window
        // with register() and concurrent scraper jobs. register() performs the canonical
        // lookup and creation under the same per-identifier lock.
        for (Map.Entry<String, String> entry : normalizedToOriginal.entrySet()) {
            byValue.put(entry.getKey(), register(type, entry.getValue(), source));
        }

        return byValue;
    }

    private ProductIdentifier newIdentifier(ProductIdentifier.IdentifierType type,
                                            String rawValue,
                                            String normalizedValue) {
        ProductIdentifier pi = new ProductIdentifier();
        pi.setType(type);
        pi.setValue(rawValue);
        // ProductIdentifier deliberately retains the source value. The canonical value
        // must be set explicitly because TITLE_NORMALIZED has richer service-level rules.
        pi.setNormalizedValue(normalizedValue);
        return pi;
    }

    private Optional<ProductIdentifier> findRegisteredIdentifier(ProductIdentifier.IdentifierType type,
                                                                  String normalizedValue) {
        Optional<ProductIdentifier> exact = productIdentifierRepository
                .findByTypeAndIdentifier(type, normalizedValue);
        if (exact.isPresent() || type != ProductIdentifier.IdentifierType.TITLE_NORMALIZED) {
            return exact;
        }
        // V18 initially copied legacy raw titles into normalized_value. Reuse such a row
        // rather than inserting a second identifier that differs only by letter case.
        return productIdentifierRepository.findFirstByTypeAndNormalizedValueIgnoreCase(type, normalizedValue);
    }

    private static ReentrantLock lockFor(ProductIdentifier.IdentifierType type, String normalizedValue) {
        int index = Math.floorMod(Objects.hash(type, normalizedValue), REGISTRATION_LOCKS.length);
        return REGISTRATION_LOCKS[index];
    }

    private static ReentrantLock[] createRegistrationLocks() {
        ReentrantLock[] locks = new ReentrantLock[256];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
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

        String normalized = switch (type) {
            case TITLE -> trimmed;
            case TITLE_NORMALIZED -> normalizeTitle(trimmed);
            case MPN -> normalizeMpn(trimmed);
            case ASIN -> IdentifierNormalizer.asin(trimmed);
            case EAN, UPC, GTIN -> IdentifierNormalizer.gtin(trimmed);
        };
        return normalized;
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
        return IdentifierNormalizer.mpn(mpn);
    }

    private String normalizeNumericCode(String code) {
        String normalized = IdentifierNormalizer.gtin(code);
        return normalized == null ? "" : normalized;
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
