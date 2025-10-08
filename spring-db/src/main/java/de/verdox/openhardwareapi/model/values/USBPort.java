package de.verdox.openhardwareapi.model.values;

import de.verdox.openhardwareapi.model.HardwareTypes;
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
public class USBPort {
    @Enumerated(EnumType.STRING)
    private HardwareTypes.UsbConnectorType type;

    @Enumerated(EnumType.STRING)
    private HardwareTypes.UsbVersion version;

    @Positive
    private Integer quantity; // Anzahl gleicher Slots
}