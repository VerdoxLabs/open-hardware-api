package de.verdox.hwapi.pricing.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import de.verdox.hwapi.catalog.domain.values.Currency;
import de.verdox.hwapi.catalog.domain.values.ItemCondition;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "ux_rsi_all",
                columnNames = {
                        "market_place_domain",
                        "market_place_item_id",
                        "ean",
                        "sell_price",
                        "currency",
                        "sell_date"
                }
        ),
        indexes = {
                // WICHTIG: physische Spaltennamen verwenden!
                @Index(name = "idx_rsi_ean", columnList = "ean"),
                @Index(name = "idx_rsi_ean_sellprice", columnList = "ean,sell_price"),
                // Deckt die Hot-Queries ab: ean [+currency] + sell_date >= from
                @Index(name = "idx_rsi_ean_currency_sell_date", columnList = "ean,currency,sell_date"),
                @Index(name = "idx_rsi_spec_id", columnList = "hardware_spec_id")
        }
)
@ToString
public class RemoteSoldItem {

    @Id
    @JsonIgnore
    private UUID uuid;

    @Column(name = "market_place_domain", nullable = false)
    private String marketPlaceDomain;

    @Column(name = "market_place_item_id", nullable = false)
    private String marketPlaceItemID;

    @Column(name = "ean", nullable = false)
    private String ean;

    @Column(name = "sell_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal sellPrice;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;

    @Column(name = "sell_date", nullable = false)
    private LocalDate sellDate;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "condition")
    private ItemCondition condition;

    @Column(name = "listing_title", length = 1024)
    private String listingTitle;

    @Column(name = "matched_ean", length = 14)
    private String matchedEan;

    @Column(name = "matched_mpn", length = 512)
    private String matchedMpn;

    @Enumerated(EnumType.STRING)
    @Column(name = "ebay_match_status", nullable = false)
    private EbayMatchStatus ebayMatchStatus = EbayMatchStatus.HIGH_CONFIDENCE;

    @Column(name = "ebay_match_reason", length = 1024)
    private String ebayMatchReason;

    /**
     * Logischer Verweis auf die HardwareSpec (Catalog) – bewusst ohne FK.
     * Ermöglicht Joins auf den Catalog, ohne die Deterministik des UUID-PKs zu
     * berühren (wird bei Bedarf nachträglich gesetzt).
     */
    @JsonIgnore
    @Column(name = "hardware_spec_id")
    private Long hardwareSpecId;

    public RemoteSoldItem(String marketPlaceDomain, String marketPlaceItemID, String ean,
                          BigDecimal sellPrice, Currency currency, LocalDate sellDate, ItemCondition itemCondition) {
        this.uuid = deriveUUID(marketPlaceDomain, marketPlaceItemID, ean, sellPrice, currency, sellDate);
        this.marketPlaceDomain = marketPlaceDomain;
        this.marketPlaceItemID = marketPlaceItemID;
        this.ean = ean;
        this.sellPrice = sellPrice;
        this.currency = currency;
        this.sellDate = sellDate;
        this.condition = itemCondition;
    }

    public static UUID deriveUUID(String marketPlaceDomain, String marketPlaceItemID,
                                  String ean, BigDecimal sellPrice,
                                  Currency currency, LocalDate sellDate) {

        String key = String.join("|",
                nullSafe(marketPlaceDomain),
                nullSafe(marketPlaceItemID),
                nullSafe(ean),
                sellPrice != null ? sellPrice.stripTrailingZeros().toPlainString() : "0",
                currency != null ? currency.name() : "UNKNOWN",
                sellDate != null ? sellDate.toString() : "0000-00-00"
        );

        UUID namespace = UUID.fromString("00000000-0000-0000-0000-000000000001");
        return uuid5(namespace, key);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s.trim();
    }

    public static UUID uuid5(UUID namespace, String name) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(toBytes(namespace));
            sha1.update(name.getBytes(StandardCharsets.UTF_8));
            byte[] hash = sha1.digest();

            hash[6] &= 0x0f;  // clear version
            hash[6] |= 0x50;  // set to version 5
            hash[8] &= 0x3f;  // clear variant
            hash[8] |= 0x80;  // set to IETF variant

            ByteBuffer bb = ByteBuffer.wrap(hash, 0, 16);
            long msb = bb.getLong();
            long lsb = bb.getLong();
            return new UUID(msb, lsb);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] toBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }
}
