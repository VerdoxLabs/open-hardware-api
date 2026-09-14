package de.verdox.hwapi.catalog.domain.values;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class FanSpec {
    @Positive
    private Integer diameterMm; // z.B. 120
    @Positive
    private Integer rpmMax; // max. Drehzahl
    @Positive
    private Integer count; // Anzahl Lüfter
}