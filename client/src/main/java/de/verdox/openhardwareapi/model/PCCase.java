package de.verdox.openhardwareapi.model;

import de.verdox.openhardwareapi.model.values.DimensionsMm;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@DiscriminatorValue("COMPUTER_CASE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PCCase extends HardwareSpec {
    @Enumerated(EnumType.STRING)
    private HardwareTypes.CaseSizeClass sizeClass = HardwareTypes.CaseSizeClass.MID_TOWER;


    @Embedded
    private DimensionsMm dimensions = new DimensionsMm(0d,0d,0d);


    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mb_case_support", joinColumns = @JoinColumn(name = "spec_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "form_factor")
    private Set<HardwareTypes.MotherboardFormFactor> motherboardSupport = new LinkedHashSet<>();


    @PositiveOrZero
    private double maxGpuLengthMm = 0;


    @PositiveOrZero
    private double maxCpuCoolerHeightMm = 0;

    @Override
    public String toString() {
        return "PCCase{" + "sizeClass=" + sizeClass + ", dimensions=" + dimensions + ", motherboardSupport=" + motherboardSupport + ", maxGpuLengthMm=" + maxGpuLengthMm + ", maxCpuCoolerHeightMm=" + maxCpuCoolerHeightMm + ", manufacturer='" + manufacturer + '\'' + ", model='" + model + '\'' + ", launchDate=" + launchDate + ", tags=" + tags + ", attributes=" + attributes + '}';
    }

    public static HardwareTypes.CaseSizeClass classify(DimensionsMm d) {
        double height = d.getHeight();
        double depth = d.getDepth();

        if (height < 300 && depth < 400) {
            return HardwareTypes.CaseSizeClass.MINI_ITX;
        } else if (height < 400) {
            return HardwareTypes.CaseSizeClass.MICRO_ATX;
        } else if (height < 500) {
            return HardwareTypes.CaseSizeClass.MID_TOWER;
        } else {
            return HardwareTypes.CaseSizeClass.FULL_TOWER;
        }
    }

    @Override
    public void checkIfLegal() {

    }
}