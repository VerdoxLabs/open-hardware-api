package de.verdox.hwapi.model;


import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
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
    public void sanitize() {
        super.sanitize();
        setModel(getModel()
                .replace("CPU", "")
                .replace("Cooler", "")
                .replace(getManufacturer(), "")
        );
    }

    @Override
    public void merge(CPUCooler other) {
        super.merge(other);
        mergeEnum(other, CPUCooler::getType, CPUCooler::setType, HardwareTypes.CoolerType.UNKNOWN);
        mergeEnumCollection(other, CPUCooler::getSupportedSockets);
        mergeNumber(other, CPUCooler::getRadiatorLengthMm, CPUCooler::setRadiatorLengthMm);
        mergeNumber(other, CPUCooler::getTdpWatts, CPUCooler::setTdpWatts);
        mergeEnum(other, CPUCooler::getFanConnectorType, CPUCooler::setFanConnectorType, HardwareTypes.FanConnectorType.UNKNOWN);
        mergeBool(other, CPUCooler::getHasRgb, CPUCooler::setHasRgb);
        mergeSet(other, CPUCooler::getColors, CPUCooler::setColors);
        mergeNumber(other, CPUCooler::getFanRPM, CPUCooler::setFanRPM);
        mergeNumber(other, CPUCooler::getNoiseLevelDB, CPUCooler::setNoiseLevelDB);
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

    @PositiveOrZero
    @Column(name = "fan_rpm")
    private Integer fanRPM = 0;

    @PositiveOrZero
    @Column(name = "noise_level_db")
    private Double noiseLevelDB = 0D;

    @Enumerated(EnumType.STRING)
    @Column(name = "fan_connector_type")
    private HardwareTypes.FanConnectorType fanConnectorType = HardwareTypes.FanConnectorType.UNKNOWN;

    @Column(name = "has_rgb")
    private Boolean hasRgb = false;

    @ElementCollection
    @CollectionTable(name="cpucooler_color", joinColumns=@JoinColumn(name="cpucooler_id"))
    @Column(name="color", nullable=false)
    private Set<String> colors = new HashSet<>();


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
