package de.verdox.hwapi.pricing.application.kleinanzeigen;

import de.verdox.hwapi.catalog.domain.values.Currency;
import de.verdox.hwapi.pricing.application.RemoteActiveListingWriterService;
import de.verdox.hwapi.pricing.sources.kleinanzeigen.KleinanzeigenAd;
import de.verdox.hwapi.pricing.sources.kleinanzeigen.KleinanzeigenScraper;
import de.verdox.hwapi.pricing.model.C2cMatchReview;
import de.verdox.hwapi.pricing.model.ListingEnums;
import de.verdox.hwapi.pricing.model.RemoteActiveListing;
import de.verdox.hwapi.pricing.repository.C2cMatchReviewRepository;
import de.verdox.hwapi.identity.application.ProductRegistryService;
import de.verdox.hwapi.identity.support.C2CTitleCleaner;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verarbeitet rohe Kleinanzeigen-Ads ({@link KleinanzeigenAd}) gegen die
 * Product-Identity-Registry und upsertet die Zuordnung in die aktive
 * Listings-Schiene (analog zur Awin-Integration).
 *
 * <p><b>Zuordnung nach Konfidenz gestaffelt</b> – C2C-Titel sind fehlerbehaftet,
 * daher wird nicht blind gematcht:
 * <ul>
 *   <li><b>Tier 1</b> (Score &ge; {@value #TIER1_MIN}, meist exakte 1.0): sauber →
 *       EAN/MPN verknüpfen + upserten (Provenienz "C2C_AUTO"). Der Writer
 *       back-linkt das dann in den Katalog.</li>
 *   <li><b>Tier 2</b> ({@value #TIER2_MIN}–{@value #TIER1_MIN}): plausibel, aber
 *       riskant → in die Review-Queue ({@link C2cMatchReview}).</li>
 *   <li><b>Tier 3</b> (&lt; {@value #TIER2_MIN} oder kein Treffer): zu unsicher →
 *       ebenfalls Review-Queue.</li>
 * </ul>
 *
 * <p><b>Lern-Schleife:</b> {@link #confirmC2cMatch} registriert den bestätigten
 * Titel + Codes in der Product-Identity. Der identische Titel wird beim nächsten
 * Lauf ein exakter 1.0-Treffer (Tier 1) und landet dann automatisch im Preis-Track.
 */
@Service
@RequiredArgsConstructor
public class KleinanzeigenPriceService {

    private static final Logger LOGGER = Logger.getLogger(KleinanzeigenPriceService.class.getSimpleName());
    public static final String SOURCE = "KLEINANZEIGEN";
    private static final String MARKET_DOMAIN = "kleinanzeigen.de";

    /** Provenienz-Werte für {@link RemoteActiveListing#setMatchSource}. */
    public static final String MATCH_SOURCE_AUTO = "C2C_AUTO";
    public static final String MATCH_SOURCE_CONFIRMED = "C2C_CONFIRMED";

    /** Auto-Link-Grenze: darunter wird ein Fuzzy-Titel nicht ungeprüft verknüpft. */
    private static final double TIER1_MIN = 0.93;
    /** Ab hier ist ein Fuzzy-Titel "plausibel" (Review-Kandidat), darunter ist er zu schwach. */
    private static final double TIER2_MIN = 0.80;

    /** Kategorien/Queries, die gecrawlt werden. Startpunkt – hier pflegen. */
    private static final List<String> QUERIES = List.of(
            "Grafikkarte", "Prozessor", "Mainboard", "RAM", "SSD", "Netzteil"
    );

    private final ProductRegistryService productRegistryService;
    private final RemoteActiveListingWriterService listingWriter;
    private final C2cMatchReviewRepository reviewRepository;

    private volatile KleinanzeigenScraper scraper;

    private KleinanzeigenScraper scraper() {
        if (scraper == null) {
            synchronized (this) {
                if (scraper == null) {
                    scraper = new KleinanzeigenScraper("kleinanzeigen-scraper");
                }
            }
        }
        return scraper;
    }

    // ------------------------------------------------------------------------
    // Geplanter Lauf (Pfad end-to-end): Scraper → Match → Writer/Review
    // ------------------------------------------------------------------------

    /**
     * Stündlicher C2C-Scrape. Degraded graceful: ohne erreichbares Selenium-Grid
     * oder bei Bot-Challenge liefert der Scraper (halb) leere Listen und der Run
     * endet mit leerem Report – kein harter Fehler.
     */
    @Scheduled(cron = "0 20 * * * *", zone = "Europe/Berlin")
    @Async
    public void runScheduledScrape() {
        LOGGER.log(Level.INFO, "Kleinanzeigen-Scrape gestartet");
        int total = 0;
        for (String query : QUERIES) {
            List<KleinanzeigenAd> ads = scraper().fetch(query, 2);
            if (ads.isEmpty()) {
                continue;
            }
            KleinanzeigenScrapeReport report = processAds(ads);
            total += report.total();
            LOGGER.log(Level.INFO, "Kleinanzeigen '" + query + "': " + report.summary());
        }
        LOGGER.log(Level.INFO, "Kleinanzeigen-Scrape fertig, " + total + " Ads verarbeitet");
    }

    // ------------------------------------------------------------------------
    // Kern: Match + Staffelung + Upsert/Review
    // ------------------------------------------------------------------------

    /**
     * Verarbeitet eine Liste roher Ads. Tier 1 (mit Preis) wird upsertet;
     * Tier 2/3 (und Tier 1 ohne Richtpreis) fließen in die Review-Queue.
     */
    @Transactional
    public KleinanzeigenScrapeReport processAds(List<KleinanzeigenAd> ads) {
        int tier1 = 0, tier2 = 0, tier3 = 0, noPrice = 0, errors = 0;

        for (KleinanzeigenAd ad : ads) {
            try {
                MatchResult m = match(ad.title());

                if (m.tier() == 1 && ad.price() != null) {
                    upsert(ad, m, MATCH_SOURCE_AUTO);
                    tier1++;
                } else {
                    if (m.tier() == 1) {
                        // sauber gematcht, aber "VB" ohne Richtpreis → kein Snapshot,
                        // aber der Titel lohnt die Lern-Schleife → Review (Kandidat).
                        noPrice++;
                    } else if (m.tier() == 2) {
                        tier2++;
                    } else {
                        tier3++;
                    }
                    persistReview(ad, m);
                }
            } catch (Exception e) {
                errors++;
                LOGGER.log(Level.WARNING, "Kleinanzeigen-Ad " + ad.adId() + " (" + ad.title() + ") fehlgeschlagen", e);
            }
        }

        return new KleinanzeigenScrapeReport(ads.size(), tier1, tier2, tier3, noPrice, errors);
    }

    /**
     * Matcht einen rohen C2C-Titel gegen die Registry (bereinigt + typ-gefiltert)
     * und leitet die Tier-Staffel + die zu verknüpfenden Codes ab.
     * (Package-privat zum Testen.)
     */
    MatchResult match(String rawTitle) {
        return productRegistryService.searchAggregatedC2C(rawTitle)
                .map(agg -> {
                    double score = agg.bestScore();
                    String ean = agg.eans().stream().filter(Objects::nonNull).findFirst().orElse(null);
                    String mpn = agg.mpns().stream().filter(Objects::nonNull).findFirst().orElse(null);
                    int tier = score >= TIER1_MIN ? 1 : (score >= TIER2_MIN ? 2 : 3);
                    return new MatchResult(score, ean, mpn, tier);
                })
                .orElseGet(() -> new MatchResult(0.0, null, null, 3));
    }

    /**
     * Hält einen noch nicht automatisch verknüpften Titel in der Review-Queue.
     * Dedup über (Inserat-ID, geCleant Titel) – stündliche Re-Scrapes duplizieren
     * daher nicht. Bestehende PENDING-Reviews werden nicht überschrieben.
     */
    @Transactional
    public void persistReview(KleinanzeigenAd ad, MatchResult m) {
        String cleaned = C2CTitleCleaner.clean(ad.title());
        if (cleaned == null || cleaned.isBlank()) {
            return;
        }
        if (reviewRepository.existsByMarketPlaceItemIdAndTitleNormalized(ad.adId(), cleaned)) {
            return;
        }
        C2cMatchReview review = new C2cMatchReview();
        review.setMarketPlaceItemId(ad.adId());
        review.setRawTitle(ad.title());
        review.setTitleNormalized(cleaned);
        review.setBestScore(m.score());
        review.setTier(m.tier());
        review.setMatchedEan(m.ean());
        review.setMatchedMpn(m.mpn());
        review.setAdUrl(ad.url());
        review.setStatus(C2cMatchReview.Status.PENDING);
        reviewRepository.save(review);
    }

    private void upsert(KleinanzeigenAd ad, MatchResult m, String matchSource) {
        RemoteActiveListing listing = listingWriter.upsertIdentityAndDailyPrice(
                ListingEnums.Country.DE,
                SOURCE,
                MARKET_DOMAIN,
                ad.adId(),
                m.ean(),
                m.mpn(),
                ad.title(),
                null,
                ad.url(),
                ad.imageUrl(),
                ad.price(),
                ad.currency() != null ? ad.currency() : Currency.EURO,
                null,
                1
        );
        // Provenienz nachtragen (gleiche Transaktion → wird mitgeflushed).
        listing.setMatchSource(matchSource);
        listing.setMatchConfidence(m.score());
    }

    // ------------------------------------------------------------------------
    // Lern-Schleife: Review bestätigen / ablehnen
    // ------------------------------------------------------------------------

    /** Offene Reviews, unsicherste zuerst – Basis für den Discord-Review-Flow. */
    @Transactional(readOnly = true)
    public List<C2cMatchReview> getReviewQueue() {
        return reviewRepository.findReviewQueue(C2cMatchReview.Status.PENDING);
    }

    /**
     * Bestätigt eine Review (Lern-Schleife):
     * <ol>
     *   <li>Titel + bestätigte Codes gehen in die Product-Identity
     *       ({@link ProductRegistryService#confirmC2cTitle}) – derselbe Titel
     *       wird künftig ein exakter 1.0-Treffer.</li>
     *   <li>Wenn das Inserat einen Preis hat, wird es nun (als bestätigt) upsertet.</li>
     *   <li>Review → CONFIRMED.</li>
     * </ol>
     *
     * @param reviewId    ID der Review
     * @param confirmedBy wer bestätigt (z. B. Discord-User)
     * @return die bestellte/erzeugte Identity, oder null wenn die Review nicht existiert
     */
    @Transactional
    public de.verdox.hwapi.identity.ProductIdentity confirmC2cMatch(Long reviewId, String confirmedBy) {
        C2cMatchReview review = reviewRepository.findById(reviewId).orElse(null);
        if (review == null) {
            return null;
        }

        de.verdox.hwapi.identity.ProductIdentity identity = productRegistryService.confirmC2cTitle(
                review.getRawTitle(),
                review.getMatchedEan() != null ? List.of(review.getMatchedEan()) : List.of(),
                review.getMatchedMpn() != null ? List.of(review.getMatchedMpn()) : List.of(),
                SOURCE
        );

        // Kein Preis-Upsert hier: der Preis ist in der Review nicht bekannt und ein
        // Platzhalter-Snapshot würde die Preisdaten verschmutzen. Beim nächsten Scrape
        // ist derselbe Titel ein exakter Tier-1-Treffer und läuft mit echtem Preis ein.

        review.setStatus(C2cMatchReview.Status.CONFIRMED);
        review.setConfirmedBy(confirmedBy);
        reviewRepository.save(review);

        return identity;
    }

    /**
     * Ablehnt eine Review (falscher Kandidat). Der Titel bleibt unverknüpft;
     * negative Caches (keine erneute Review) sind der nächste Ausbau.
     */
    @Transactional
    public C2cMatchReview rejectC2cMatch(Long reviewId, String confirmedBy) {
        C2cMatchReview review = reviewRepository.findById(reviewId).orElse(null);
        if (review == null) {
            return null;
        }
        review.setStatus(C2cMatchReview.Status.REJECTED);
        review.setConfirmedBy(confirmedBy);
        return reviewRepository.save(review);
    }

    // ------------------------------------------------------------------------
    // Resultate
    // ------------------------------------------------------------------------

    /** Ergebnis eines Einzel-Matches: Konfidenz, verknüpfbare Codes, Tier. */
    record MatchResult(double score, String ean, String mpn, int tier) {
    }

    /** Aggregierter Lauf-Report. */
    public record KleinanzeigenScrapeReport(
            int total,
            int tier1,
            int tier2,
            int tier3,
            int noPrice,
            int errors
    ) {
        public String summary() {
            return String.format("total=%d tier1=%d tier2=%d tier3=%d noPrice=%d errors=%d",
                    total, tier1, tier2, tier3, noPrice, errors);
        }
    }
}
