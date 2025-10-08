package de.verdox.openhardwareapi.model;

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
public class RAM extends HardwareSpec {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HardwareTypes.RamType type = HardwareTypes.RamType.UNKNOWN;

    @PositiveOrZero
    @Column(nullable = false)
    private Integer sticks = 0;

    @PositiveOrZero
    @Column(nullable = false)
    private Integer sizeGb = 0; // pro Modul


    @PositiveOrZero
    private Integer speedMtps = 0; // z.B. 6000


    @PositiveOrZero
    private Integer casLatency = 0; // CL Zahl
    @PositiveOrZero
    private Integer rowAddressToColumnAddressDelay = 0; // TRCD
    @PositiveOrZero
    private Integer rowPrechargeTime = 0; // TRP
    @PositiveOrZero
    private Integer rowActiveTime = 0; // TRAS

    @Override
    public String toString() {
        return "RAM{" + "type=" + type + ", sizeGb=" + sizeGb + ", speedMtps=" + speedMtps + ", casLatency=" + casLatency + "-" + rowAddressToColumnAddressDelay + "-" + rowPrechargeTime + "-" + rowActiveTime + ", manufacturer='" + manufacturer + '\'' + ", model='" + model + '\'' + ", launchDate=" + launchDate + ", tags=" + tags + ", attributes=" + attributes + '}';
    }

    @Override
    public void checkIfLegal() {

    }
}