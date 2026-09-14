package de.verdox.hwapi.pricing.model;


import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "remote_active_listing",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_ral_marketplace_item",
                columnNames = {"market_place_domain", "market_place_item_id"}
        ),
        indexes = {
                @Index(name = "idx_ral_ean", columnList = "ean"),
                @Index(name = "idx_ral_mpn", columnList = "mpn"),
                @Index(name = "idx_ral_last_seen", columnList = "last_seen_at"),
                @Index(name = "idx_ral_active", columnList = "still_active"),
                @Index(name = "idx_ral_spec_id", columnList = "hardware_spec_id")
        }
)
public class RemoteActiveListing {

    @Id
    @GeneratedValue
    private UUID uuid;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "primary_region", nullable = false)
    private ListingEnums.Country primaryRegion;

    @Column(name = "market_place_name", nullable = false)
    private String marketPlaceName;

    @Column(name = "market_place_domain", nullable = false)
    private String marketPlaceDomain;

    @Column(name = "market_place_item_id", nullable = false)
    private String marketPlaceItemID;

    @Column(name = "ean")
    private String ean;

    @Column(name = "mpn")
    private String mpn;

    @Column(name = "title")
    private String title;

    @Column(name = "product_manufacturer")
    private String productManufacturer;

    @Column(name = "item_url", length = 1000)
    private String itemUrl;

    @Column(name = "merchant_image_url", length = 1000)
    private String merchantImageUrl;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "still_active", nullable = false)
    private boolean stillActive = true;

    /**
     * Logischer Verweis auf die HardwareSpec (Catalog) – bewusst ohne FK,
     * um die bestehende EAN/MPN-Kopplung nicht zu erzwingen und Upserts ohne
     * Spec-Lookup zu ermöglichen. Ggf. nachträglich gesetzt.
     */
    @Column(name = "hardware_spec_id")
    private Long hardwareSpecId;

    /**
     * Herleitung der Katalog-Zuordnung (C2C-Lernschleife): z. B. "C2C_AUTO"
     * (ungeprüfter Tier-1-Fuzzy-Titel) oder "C2C_CONFIRMED" (bestätigter Titel).
     */
    @Column(name = "match_source")
    private String matchSource;

    /** Matching-Konfidenz (0..1) zum Zeitpunkt der Zuordnung; null = nicht aus C2C gematcht. */
    @Column(name = "match_confidence")
    private Double matchConfidence;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (firstSeenAt == null) firstSeenAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        lastSeenAt = Instant.now();
    }

    @Override
    public String toString() {
        return "RemoteActiveListing{" +
                "uuid=" + uuid +
                ", marketPlaceDomain='" + marketPlaceDomain + '\'' +
                ", marketPlaceItemID='" + marketPlaceItemID + '\'' +
                ", ean='" + ean + '\'' +
                ", mpn='" + mpn + '\'' +
                ", title='" + title + '\'' +
                ", productManufacturer='" + productManufacturer + '\'' +
                ", itemUrl='" + itemUrl + '\'' +
                ", merchantImageUrl='" + merchantImageUrl + '\'' +
                ", firstSeenAt=" + firstSeenAt +
                ", lastSeenAt=" + lastSeenAt +
                ", stillActive=" + stillActive +
                '}';
    }
}