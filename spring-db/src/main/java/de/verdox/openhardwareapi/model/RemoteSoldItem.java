package de.verdox.openhardwareapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.verdox.openhardwareapi.model.values.Currency;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        indexes = {
                @Index(name = "idx_rsi_ean", columnList = "EAN"),
                @Index(name = "idx_rsi_ean_sellprice", columnList = "EAN,sellPrice")
        }
)
public class RemoteSoldItem {
    @Id
    @JsonIgnore
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private String marketPlaceDomain;

    private String marketPlaceItemID;

    private String EAN;

    private BigDecimal sellPrice;

    @Enumerated(EnumType.STRING)
    private Currency currency;

    private LocalDate sellDate;
}
