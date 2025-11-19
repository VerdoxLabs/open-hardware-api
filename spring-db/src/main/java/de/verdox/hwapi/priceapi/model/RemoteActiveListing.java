package de.verdox.hwapi.priceapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.model.values.ItemCondition;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
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
                @Index(name = "idx_ral_ean_price", columnList = "ean,price")
        }
)
public class RemoteActiveListing {

    @Id
    @GeneratedValue
    private UUID uuid;

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

    @Column(name = "item_url", length = 1000)
    private String itemUrl;

    @Column(name = "price", precision = 18, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "currency")
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "condition")
    private ItemCondition condition;

    @Column(name = "shipping_price", precision = 18, scale = 2)
    private BigDecimal shippingPrice;

    @Column(name = "available_quantity")
    private Integer availableQuantity;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "still_active", nullable = false)
    private boolean stillActive = true;

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
}
