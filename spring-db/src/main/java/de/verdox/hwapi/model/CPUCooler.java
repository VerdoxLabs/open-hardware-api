package de.verdox.hwapi.model;


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
@NamedEntityGraph(
        name = "CPUCooler.All",
        includeAllAttributes = true
)
public class CPUCooler extends HardwareSpec<CPUCooler> {
    @Override
    public void merge(CPUCooler other) {
        super.merge(other);
        mergeEnum(other, CPUCooler::getType, CPUCooler::setType, HardwareTypes.CoolerType.UNKNOWN);
        mergeEnumCollection(other, CPUCooler::getSupportedSockets);
        mergeNumber(other, CPUCooler::getRadiatorLengthMm, CPUCooler::setRadiatorLengthMm);
        mergeNumber(other, CPUCooler::getTdpWatts, CPUCooler::setTdpWatts);
    }

    @Enumerated(EnumType.STRING)
    private HardwareTypes.CoolerType type = HardwareTypes.CoolerType.UNKNOWN; // Luft oder AIO


    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "cooler_supported_sockets", joinColumns = @JoinColumn(name = "spec_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "socket")
    private Set<HardwareTypes.CpuSocket> supportedSockets = new LinkedHashSet<>();


    @PositiveOrZero
    private double radiatorLengthMm = 0;

    @PositiveOrZero
    private Integer tdpWatts = 0;


    @Override
    public String toString() {
        return "CPUCooler{" +
                ", type=" + type +
                ", supportedSockets=" + supportedSockets +
                ", radiatorLengthMm=" + radiatorLengthMm +
                ", tdpWatts=" + tdpWatts +
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                ", EAN='" + EANs + '\'' +
                ", MPN='" + MPNs + '\'' +
                ", launchDate=" + launchDate +
                '}';
    }

    @Override
    public void checkIfLegal() {
        if (type == null) {
            throw new IllegalArgumentException("The cooler type cannot be null!");
        }
    }
}
