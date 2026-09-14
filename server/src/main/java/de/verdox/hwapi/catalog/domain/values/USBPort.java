package de.verdox.hwapi.catalog.domain.values;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.verdox.hwapi.catalog.domain.HardwareTypes;
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
public class USBPort {
    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.UsbConnectorType type;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.UsbVersion version;

    @Positive
    private Integer quantity; // Anzahl gleicher Slots

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        USBPort usbPort = (USBPort) o;
        return type == usbPort.type && version == usbPort.version;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, version);
    }
}