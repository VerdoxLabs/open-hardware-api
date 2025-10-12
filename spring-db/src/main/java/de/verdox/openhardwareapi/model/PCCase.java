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
public class PCCase extends HardwareSpec<PCCase> {

    @Override
    public void merge(PCCase other) {
        super.merge(other);
        mergeEnum(other, PCCase::getSizeClass, PCCase::setSizeClass, HardwareTypes.CaseSizeClass.UNKNOWN);
        merge(other, PCCase::getDimensions, PCCase::setDimensions, dimensionsMm -> dimensionsMm == null || (dimensionsMm.getDepth() == 0 || dimensionsMm.getHeight() == 0 || dimensionsMm.getWidth() == 0));
        mergeSet(other, PCCase::getMotherboardSupport);
        mergeNumber(other, PCCase::getMaxGpuLengthMm, PCCase::setMaxGpuLengthMm);
        mergeNumber(other, PCCase::getMaxCpuCoolerHeightMm, PCCase::setMaxCpuCoolerHeightMm);
    }

    @Enumerated(EnumType.STRING)
    private HardwareTypes.CaseSizeClass sizeClass = HardwareTypes.CaseSizeClass.MID_TOWER;


    @Embedded
    private DimensionsMm dimensions = new DimensionsMm(0d, 0d, 0d);


    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mb_case_support", joinColumns = @JoinColumn(name = "spec_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "form_factor")
    private Set<HardwareTypes.MotherboardFormFactor> motherboardSupport = new LinkedHashSet<>();


    @PositiveOrZero
    private double maxGpuLengthMm = 0;


    @PositiveOrZero
    private double maxCpuCoolerHeightMm = 0;

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

    @Override
    public String toString() {
        return "PCCase{" +
                "EAN='" + EAN + '\'' +
                ", sizeClass=" + sizeClass +
                ", dimensions=" + dimensions +
                ", motherboardSupport=" + motherboardSupport +
                ", maxGpuLengthMm=" + maxGpuLengthMm +
                ", maxCpuCoolerHeightMm=" + maxCpuCoolerHeightMm +
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                ", MPN='" + MPN + '\'' +
                ", UPC='" + UPC + '\'' +
                ", launchDate=" + launchDate +
                '}';
    }
}