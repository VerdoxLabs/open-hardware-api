package de.verdox.hwapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import de.verdox.hwapi.model.values.PowerConnector;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "gpuchip",
        indexes = {
                @Index(name = "idx_gpuchip_canonical_model", columnList = "canonical_model")
        }
)
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorValue("GPUChip")
@DiscriminatorColumn(name = "gpu_chip_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@NamedEntityGraph(
        name = "GPUChip.All",
        includeAllAttributes = true
)
public class GPUChip extends HardwareSpec<GPUChip> {

    @Override
    public void merge(GPUChip other) {
        super.merge(other);
        mergeString(other, GPUChip::getCanonicalModel, GPUChip::setCanonicalModel);
        mergeEnum(other, GPUChip::getPcieVersion, GPUChip::setPcieVersion, HardwareTypes.PcieVersion.UNKNOWN);
        mergeEnum(other, GPUChip::getVramType, GPUChip::setVramType, HardwareTypes.VRAM_TYPE.UNKNOWN);
        mergeEnum(other, GPUChip::getArchitecture, GPUChip::setArchitecture, HardwareTypes.GpuArchitecture.UNKNOWN);
        mergeNumber(other, GPUChip::getVramGb, GPUChip::setVramGb);
        mergeNumber(other, GPUChip::getLengthMm, GPUChip::setLengthMm);
        mergeNumber(other, GPUChip::getTdp, GPUChip::setTdp);
        mergeNumber(other, GPUChip::getMemoryBusWidthBit, GPUChip::setMemoryBusWidthBit);
        mergeNumber(other, GPUChip::getMemoryBandwidthGbps, GPUChip::setMemoryBandwidthGbps);
        mergeNumber(other, GPUChip::getBoostClockMhz, GPUChip::setBoostClockMhz);
        mergeNumber(other, GPUChip::getProcessNodeNm, GPUChip::setProcessNodeNm);
        mergeSet(other, GPUChip::getPowerConnectors,  GPUChip::setPowerConnectors);
    }

    @Column(name = "canonical_model", nullable = false)
    private String canonicalModel;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.PcieVersion pcieVersion = HardwareTypes.PcieVersion.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.VRAM_TYPE vramType = HardwareTypes.VRAM_TYPE.UNKNOWN; // z.B. GDDR6, GDDR6X

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.GpuArchitecture architecture = HardwareTypes.GpuArchitecture.UNKNOWN;

    @PositiveOrZero
    private double vramGb = 0;


    @PositiveOrZero
    private double lengthMm = 0;

    @PositiveOrZero
    private double tdp = 0;

    @PositiveOrZero
    @Column(name = "memory_bus_width_bit")
    private int memoryBusWidthBit = 0;

    @PositiveOrZero
    @Column(name = "memory_bandwidth_gbps")
    private double memoryBandwidthGbps = 0;

    @PositiveOrZero
    @Column(name = "boost_clock_mhz")
    private double boostClockMhz = 0;

    @PositiveOrZero
    @Column(name = "process_node_nm")
    private int processNodeNm = 0;


    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "gpu_power_connectors", joinColumns = @JoinColumn(name = "spec_id"))
    private Set<PowerConnector> powerConnectors = new LinkedHashSet<>();

    /**
     * Reverse: alle Board-Varianten (GPU), die auf diesem Chip basieren.
     */
    @OneToMany(mappedBy = "chip", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<GPU> gpus = new LinkedHashSet<>();

    @Override
    public void checkIfLegal() {
        if (canonicalModel == null || canonicalModel.isBlank()) {
            throw new IllegalArgumentException("canonical model cannot be null or blank for gpu chip " + getModel());
        }
    }

    @Override
    public String toString() {
        return "GPUChip{" +
                "model='" + model + '\'' +
                ", canonicalModel='" + canonicalModel + '\'' +
                ", pcieVersion=" + pcieVersion +
                ", vramType=" + vramType +
                ", architecture=" + architecture +
                ", vramGb=" + vramGb +
                ", lengthMm=" + lengthMm +
                ", tdp=" + tdp +
                ", memoryBusWidthBit=" + memoryBusWidthBit +
                ", memoryBandwidthGbps=" + memoryBandwidthGbps +
                ", boostClockMhz=" + boostClockMhz +
                ", powerConnectors=" + powerConnectors +
                ", manufacturer='" + manufacturer + '\'' +
                ", EAN='" + EANs + '\'' +
                ", MPN='" + MPNs + '\'' +
                ", launchDate=" + launchDate +
                '}';
    }
}