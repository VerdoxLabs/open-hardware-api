package de.verdox.openhardwareapi.model;

import de.verdox.openhardwareapi.model.values.PowerConnector;
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
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorValue("GPUChip")
@DiscriminatorColumn(name = "gpu_chip_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GPUChip extends HardwareSpec {

    private String canonicalModel;

    @Enumerated(EnumType.STRING)
    private HardwareTypes.PcieVersion pcieVersion = HardwareTypes.PcieVersion.UNKNOWN;

    @Enumerated(EnumType.STRING)
    private HardwareTypes.VRAM_TYPE vramType = HardwareTypes.VRAM_TYPE.UNKNOWN; // z.B. GDDR6, GDDR6X

    @PositiveOrZero
    private double vramGb = 0;


    @PositiveOrZero
    private double lengthMm = 0;

    private double tdp = 0;

    @OneToMany(mappedBy = "chip", fetch = FetchType.EAGER) // optionaler Rückverweis
    private Set<GPU> gpus = new HashSet<>();


    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "gpu_power_connectors", joinColumns = @JoinColumn(name = "spec_id"))
    private Set<PowerConnector> powerConnectors = new LinkedHashSet<>();

    @Override
    public String toString() {
        return "GPU{" +
                "pcieVersion=" + pcieVersion +
                ", vramType=" + vramType +
                ", vramGb=" + vramGb +
                ", lengthMm=" + lengthMm +
                ", powerConnectors=" + powerConnectors +
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                ", launchDate=" + launchDate +
                ", tags=" + tags +
                ", attributes=" + attributes +
                '}';
    }

    @Override
    public void checkIfLegal() {
        if (canonicalModel == null || canonicalModel.isBlank()) {
            throw new IllegalArgumentException("canonical model cannot be null or blank for gpu chip "+getModel());
        }
    }
}