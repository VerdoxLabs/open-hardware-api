package de.verdox.hwapi.priceapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import de.verdox.hwapi.model.values.Currency;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "price_lookup_block",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_plb_ean_currency",
                columnNames = {"ean", "currency"}
        ),
        indexes = {
                @Index(name = "idx_plb_ean_currency", columnList = "ean,currency")
        }
)
public class PriceLookupBlock {

    @Id
    @JsonIgnore
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "ean", nullable = false)
    private String ean;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;

    @Column(name = "blocked_until", nullable = false)
    private Instant blockedUntil;
}
