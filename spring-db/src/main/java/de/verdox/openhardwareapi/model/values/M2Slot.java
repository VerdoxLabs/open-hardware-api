package de.verdox.openhardwareapi.model.values;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class M2Slot {
    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.PcieVersion pcieVersion;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.StorageInterface supportedInterface; // NVME, SATA

    @Positive
    private Integer quantity; // wie viele gleiche Slots
}