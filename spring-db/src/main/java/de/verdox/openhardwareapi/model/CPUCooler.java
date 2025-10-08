package de.verdox.openhardwareapi.model;


import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@DiscriminatorValue("COOLER")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CPUCooler extends HardwareSpec {
    @Enumerated(EnumType.STRING)
    private HardwareTypes.CoolerType type; // Luft oder AIO


    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cooler_supported_sockets", joinColumns = @JoinColumn(name = "spec_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "socket")
    private Set<HardwareTypes.CpuSocket> supportedSockets = new LinkedHashSet<>();


    @PositiveOrZero
    private double radiatorLengthMm = 0; // für AIOs, optional

    @PositiveOrZero
    private Integer tdpWatts = 0;


    @Override
    public String toString() {
        return "CPUCooler{" +
                "type=" + type +
                ", supportedSockets=" + supportedSockets +
                ", radiatorLengthMm=" + radiatorLengthMm +
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                ", launchDate=" + launchDate +
                ", tags=" + tags +
                ", attributes=" + attributes +
                '}';
    }

    @Override
    public void checkIfLegal() {
        if(type == null) {
            throw new IllegalArgumentException("The cooler type cannot be null!");
        }
    }
}
