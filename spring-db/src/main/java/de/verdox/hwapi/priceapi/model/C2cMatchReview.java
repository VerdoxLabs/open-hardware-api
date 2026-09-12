package de.verdox.hwapi.priceapi.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Ein Eintrag in der C2C-Review-Queue: ein Inserat-Titel, dessen Zuordnung zur
 * Product-Identity <b>noch nicht</b> automatisch verknüpft wurde (Tier 2/3),
 * weil der fehlerbehaftete C2C-Titel zu unsicher war.
 *
 * <p>Lebenszyklus:
 * <ol>
 *   <li>Scrape findet einen Titel, der nur schwach/ambiguous matcht → Status {@link Status#PENDING}.</li>
 *   <li>Mensch (z.&nbsp;B. über den Discord-Bot) bestätigt den Kandidaten
 *       ({@link #matchedEan}/{@link #matchedMpn}).</li>
 *   <li>Bestätigung registriert den Titel in der Product-Identity (Lernschleife) →
 *       Status {@link Status#CONFIRMED}. Derselbe wiederkehrende Titel ist beim
 *       nächsten Lauf ein exakter 1.0-Treffer (Tier 1).</li>
 * </ol>
 *
 * <p>Die eindeutige Kombination (Inserat-ID, geCleant Titel) verhindert, dass
 * stündliche Re-Scrapes dieselbe Review mehrfach anlegen.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "c2c_match_review",
        uniqueConstraints = @UniqueConstraint(
                name = "uc_c2c_match_review",
                columnNames = {"market_place_item_id", "title_normalized"}
        ),
        indexes = {
                @Index(name = "idx_c2c_match_review_status", columnList = "status"),
                @Index(name = "idx_c2c_match_review_tier", columnList = "tier")
        }
)
public class C2cMatchReview {

    /** Review-Status. */
    public enum Status {
        PENDING,
        CONFIRMED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "c2c_match_review_gen")
    @SequenceGenerator(name = "c2c_match_review_gen", sequenceName = "c2c_match_review_seq", allocationSize = 50)
    private Long id;

    @Column(name = "market_place_item_id", nullable = false)
    private String marketPlaceItemId;

    /** Roher Inserat-Titel (C2C-Rauschen enthalten) – für Anzeige &amp; erneute Registrierung. */
    @Column(name = "raw_title", nullable = false)
    private String rawTitle;

    /** Gecleant Titel ({@code C2CTitleCleaner}) – Dedup- &amp; Anzeigeschlüssel. */
    @Column(name = "title_normalized", nullable = false)
    private String titleNormalized;

    /** Bester Matching-Score (0..1) zum Zeitpunkt des Findens. */
    @Column(name = "best_score", nullable = false)
    private double bestScore;

    /** 2 = plausibel (Review), 3 = zu schwach/kein Treffer. */
    @Column(name = "tier", nullable = false)
    private int tier;

    /** Top-Kandidat(en) aus dem Match – Basis für die Bestätigung. */
    @Column(name = "matched_ean")
    private String matchedEan;

    @Column(name = "matched_mpn")
    private String matchedMpn;

    @Column(name = "ad_url", length = 1000)
    private String adUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "confirmed_by")
    private String confirmedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = Status.PENDING;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
