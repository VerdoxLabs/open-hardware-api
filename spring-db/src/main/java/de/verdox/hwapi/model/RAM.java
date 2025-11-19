package de.verdox.hwapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("RAM")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@NamedEntityGraph(
        name = "RAM.All",
        includeAllAttributes = true
)
public class RAM extends HardwareSpec<RAM> {

    @Override
    public void merge(RAM other) {
        super.merge(other);
        mergeEnum(other, RAM::getType, RAM::setType, HardwareTypes.RamType.UNKNOWN);
        mergeNumber(other, RAM::getSticks, RAM::setSticks);
        mergeNumber(other, RAM::getSizeGb, RAM::setSizeGb);
        mergeNumber(other, RAM::getSpeedMtps, RAM::setSpeedMtps);
        mergeNumber(other, RAM::getCasLatency, RAM::setCasLatency);
        mergeNumber(other, RAM::getRowAddressToColumnAddressDelay, RAM::setRowAddressToColumnAddressDelay);
        mergeNumber(other, RAM::getRowPrechargeTime, RAM::setRowPrechargeTime);
        mergeNumber(other, RAM::getRowActiveTime, RAM::setRowActiveTime);
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.RamType type = HardwareTypes.RamType.UNKNOWN;

    @PositiveOrZero
    @Column(nullable = false)
    private Integer sticks = 0;

    @PositiveOrZero
    @Column(nullable = false)
    private Integer sizeGb = 0; // pro Modul


    @PositiveOrZero
    private Integer speedMtps = 0; // z.B. 6000

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.RamFormFactor formFactor = HardwareTypes.RamFormFactor.DIMM;

    @PositiveOrZero
    private Integer casLatency = 0; // CL Zahl
    @PositiveOrZero
    private Integer rowAddressToColumnAddressDelay = 0; // TRCD
    @PositiveOrZero
    private Integer rowPrechargeTime = 0; // TRP
    @PositiveOrZero
    private Integer rowActiveTime = 0; // TRAS

    @Column(name = "is_ecc", nullable = false)
    private boolean isECC = false;

    private boolean hasHeatSpreader;

    @Override
    public void checkIfLegal() {

    }

    @Transient
    @JsonIgnore
    public int getTotalSizeGB() {
        return sizeGb * sticks;
    }

    @Override
    public String toString() {
        return "RAM{" +
                "manufacturer='" + manufacturer + '\'' +
                ", type=" + type +
                ", sticks=" + sticks +
                ", sizeGb=" + sizeGb +
                ", speedMtps=" + speedMtps +
                ", casLatency=" + casLatency +
                ", rowAddressToColumnAddressDelay=" + rowAddressToColumnAddressDelay +
                ", rowPrechargeTime=" + rowPrechargeTime +
                ", rowActiveTime=" + rowActiveTime +
                ", model='" + model + '\'' +
                ", EAN='" + EANs + '\'' +
                ", MPN='" + MPNs + '\'' +
                ", launchDate=" + launchDate +
                '}';
    }
}