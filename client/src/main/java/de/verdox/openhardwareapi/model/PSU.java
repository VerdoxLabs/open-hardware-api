package de.verdox.openhardwareapi.model;

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
public class PSU extends HardwareSpec {
    @PositiveOrZero
    @Column(nullable = false)
    private Integer wattage = 0;


    @Enumerated(EnumType.STRING)
    private HardwareTypes.PsuEfficiencyRating efficiencyRating = HardwareTypes.PsuEfficiencyRating.NONE;

    @Enumerated(EnumType.STRING)
    private HardwareTypes.PSU_MODULARITY modularity = HardwareTypes.PSU_MODULARITY.UNKNOWN;

    @Enumerated(EnumType.STRING)
    private HardwareTypes.MotherboardFormFactor size = HardwareTypes.MotherboardFormFactor.UNKNOWN;


    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "psu_connectors", joinColumns = @JoinColumn(name = "spec_id"))
    private Set<PowerConnector> connectors = new LinkedHashSet<>();

    @Override
    public String toString() {
        return "PSU{" +
                "wattage=" + wattage +
                ", efficiencyRating=" + efficiencyRating +
                ", modularity=" + modularity +
                ", connectors=" + connectors +
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                ", launchDate=" + launchDate +
                ", tags=" + tags +
                ", attributes=" + attributes +
                '}';
    }

    @Override
    public void checkIfLegal() {

    }
}
