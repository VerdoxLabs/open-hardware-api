package de.verdox.openhardwareapi.model.values;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.verdox.openhardwareapi.model.HardwareTypes;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        M2Slot m2Slot = (M2Slot) o;
        return pcieVersion == m2Slot.pcieVersion && supportedInterface == m2Slot.supportedInterface;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pcieVersion, supportedInterface);
    }
}