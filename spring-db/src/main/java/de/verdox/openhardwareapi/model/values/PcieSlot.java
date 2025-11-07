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
public class PcieSlot {
    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.PcieVersion version;

    @Positive
    private Integer lanes; // z.B. 16, 8, 4, 1

    @Positive
    private Integer quantity; // Anzahl gleicher Slots

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PcieSlot pcieSlot = (PcieSlot) o;
        return version == pcieSlot.version && Objects.equals(lanes, pcieSlot.lanes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, lanes);
    }
}