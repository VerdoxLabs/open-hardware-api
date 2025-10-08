package de.verdox.openhardwareapi.model.values;

import de.verdox.openhardwareapi.model.HardwareTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Embeddable
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class PowerConnector {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HardwareTypes.PowerConnectorType type; // z.B. "PCIe 8-pin", "ATX 24-pin"
    @Positive
    private Integer quantity;
}
