package de.verdox.openhardwareapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.verdox.openhardwareapi.model.values.PowerConnector;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@DiscriminatorValue("PSU")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@NamedEntityGraph(
        name = "PSU.All",
        includeAllAttributes = true
)
public class PSU extends HardwareSpec<PSU> {

    @Override
    public void merge(PSU other) {
        super.merge(other);
        mergeNumber(other, PSU::getWattage, PSU::setWattage);
        mergeEnum(other, PSU::getEfficiencyRating, PSU::setEfficiencyRating, HardwareTypes.PsuEfficiencyRating.UNKNOWN);
        mergeEnum(other, PSU::getModularity, PSU::setModularity, HardwareTypes.PSU_MODULARITY.UNKNOWN);
        mergeEnum(other, PSU::getSize, PSU::setSize, HardwareTypes.PSUFormFactor.UNKNOWN);
        mergeSet(other, PSU::getConnectors);
    }

    @PositiveOrZero
    @Column(nullable = false)
    private Integer wattage = 0;


    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.PsuEfficiencyRating efficiencyRating = HardwareTypes.PsuEfficiencyRating.NONE;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.PSU_MODULARITY modularity = HardwareTypes.PSU_MODULARITY.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.PSUFormFactor size = HardwareTypes.PSUFormFactor.UNKNOWN;

    private Float psuPowerVersion = 3.0F;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "psu_connectors", joinColumns = @JoinColumn(name = "spec_id"), uniqueConstraints = @UniqueConstraint(columnNames = {"SPEC_ID", "TYPE"}))
    private Set<PowerConnector> connectors = new LinkedHashSet<>();

    @Override
    public void checkIfLegal() {

    }

    @Override
    public String toString() {
        return "PSU{" +
                "model='" + model + '\'' +
                ", wattage=" + wattage +
                ", efficiencyRating=" + efficiencyRating +
                ", modularity=" + modularity +
                ", size=" + size +
                ", connectors=" + connectors +
                ", manufacturer='" + manufacturer + '\'' +
                ", EAN='" + EANs + '\'' +
                ", MPN='" + MPN + '\'' +
                ", launchDate=" + launchDate +
                '}';
    }
}
