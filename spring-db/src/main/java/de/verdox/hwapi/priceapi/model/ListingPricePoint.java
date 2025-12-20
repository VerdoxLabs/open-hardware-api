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
        name = "remote_active_listing_price",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_ralp_listing_date",
                columnNames = {"listing_uuid", "snapshot_date"}
        ),
        indexes = {
                @Index(name = "idx_ralp_date", columnList = "snapshot_date"),
                @Index(name = "idx_ralp_captured_at", columnList = "captured_at")
        }
)
public class ListingPricePoint {

    @Id
    @GeneratedValue
    private UUID uuid;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_uuid", nullable = false)
    private RemoteActiveListing listing;

    @Column(name = "snapshot_date", nullable = false)
    private java.time.LocalDate snapshotDate;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Currency currency;

    @Column(name = "price", precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "shipping_price", precision = 18, scale = 2)
    private BigDecimal shippingPrice;

    @Column(name = "available_quantity")
    private Integer availableQuantity;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "condition")
    private ItemCondition condition;

    @Override
    public String toString() {
        return "ListingPricePoint{" +
                "uuid=" + uuid +
                ", listing=" + listing +
                ", snapshotDate=" + snapshotDate +
                ", capturedAt=" + capturedAt +
                ", currency=" + currency +
                ", price=" + price +
                ", shippingPrice=" + shippingPrice +
                ", availableQuantity=" + availableQuantity +
                ", condition=" + condition +
                '}';
    }
}
