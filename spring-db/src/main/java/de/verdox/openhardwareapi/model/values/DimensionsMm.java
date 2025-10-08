package de.verdox.openhardwareapi.model.values;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DimensionsMm {
    @Positive
    private Double width;
    @Positive
    private Double height;
    @Positive
    private Double depth;
}
