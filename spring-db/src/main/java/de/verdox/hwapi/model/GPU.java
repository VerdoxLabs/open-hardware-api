package de.verdox.hwapi.model;


import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@DiscriminatorValue("GPU")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@NamedEntityGraph(
        name = "GPU.All",
        includeAllAttributes = true
)
public class GPU extends HardwareSpec<GPU> {

    @Override
    public void sanitizeNumbers() {
        this.model = getModel()
                .replace(getManufacturer(), "")
                .replace(getGpuCanonicalName(), "")
                .replace(((int) getVramGb()) + " GB", "")
                .replace("Graphics Card", "")
                .replace("GeForce", "")
                .replace("Radeon", "")
                .replace("Arc", "")
                .replace("Video Card", "");
    }

    @Override
    public void merge(GPU other) {
        super.merge(other);
        mergeNumber(other, GPU::getLengthMm, GPU::setLengthMm);
        merge(other, GPU::getChip, GPU::setChip, Objects::isNull);
        mergeEnum(other, GPU::getPcieVersion, GPU::setPcieVersion, HardwareTypes.PcieVersion.UNKNOWN);
        mergeEnum(other, GPU::getVramType, GPU::setVramType, HardwareTypes.VRAM_TYPE.UNKNOWN);
        mergeNumber(other, GPU::getVramGb, GPU::setVramGb);
        mergeNumber(other, GPU::getTdp, GPU::setTdp);
        mergeNumber(other, GPU::getBaseClockMhz, GPU::setBaseClockMhz);
        mergeNumber(other, GPU::getBoostClockMhz, GPU::setBoostClockMhz);
        mergeNumber(other, GPU::getMemoryBusWidthBit, GPU::setMemoryBusWidthBit);
        mergeNumber(other, GPU::getRecommendedPsuWatts, GPU::setRecommendedPsuWatts);
        mergeNumber(other, GPU::getSlotWidth, GPU::setSlotWidth);
        mergeNumber(other, GPU::getFanCount, GPU::setFanCount);
        mergeSet(other, GPU::getColors, GPU::setColors);
        merge(other, GPU::getGpuCanonicalName, GPU::setGpuCanonicalName, s -> s == null || s.isBlank() || s.equals("unknown"));
    }

    @Column(nullable = false, length = 255)
    private String gpuCanonicalName = "unknown";

    public void setGpuCanonicalName(String gpuCanonicalName) {
        this.gpuCanonicalName = gpuCanonicalName.trim().replaceAll("\\s+", " ");
    }

    @PositiveOrZero
    private double lengthMm = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "gpu_chip_id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_gpu__chip"))
    private GPUChip chip;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.PcieVersion pcieVersion = HardwareTypes.PcieVersion.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.VRAM_TYPE vramType = HardwareTypes.VRAM_TYPE.UNKNOWN; // z.B. GDDR6, GDDR6X

    @PositiveOrZero
    private double vramGb = 0;

    private double tdp = 0;

    @PositiveOrZero
    @Column(name = "base_clock_mhz")
    private double baseClockMhz = 0;

    @PositiveOrZero
    @Column(name = "boost_clock_mhz")
    private double boostClockMhz = 0;

    @PositiveOrZero
    @Column(name = "memory_bus_width_bit")
    private int memoryBusWidthBit = 0;

    @PositiveOrZero
    @Column(name = "recommended_psu_watts")
    private int recommendedPsuWatts = 0;

    @PositiveOrZero
    @Column(name = "slot_width")
    private double slotWidth = 0;

    @PositiveOrZero
    @Column(name = "fan_count")
    private int fanCount = 0;

    @ElementCollection
    @CollectionTable(name="gpu_color", joinColumns=@JoinColumn(name="gpu_id"))
    @Column(name="color", nullable=false)
    private Set<String> colors = new HashSet<>();

    @Override
    public void checkIfLegal() {

    }

    @Override
    public String displayName() {
        return super.displayName() + " " +getGpuCanonicalName() + " "+vramGb+" GB";
    }

    @PostConstruct
    public void sanitize() {
        this.setGpuCanonicalName(getGpuCanonicalName());
    }



    @Override
    public String toString() {
        return "GPU{" +
                "MPN='" + MPNs + '\'' +
                ", lengthMm=" + lengthMm +
                ", chip=" + chip +
                ", pcieVersion=" + pcieVersion +
                ", vramType=" + vramType +
                ", vramGb=" + vramGb +
                ", tdp=" + tdp +
                ", baseClockMhz=" + baseClockMhz +
                ", boostClockMhz=" + boostClockMhz +
                ", memoryBusWidthBit=" + memoryBusWidthBit +
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                ", EAN='" + EANs + '\'' +
                ", launchDate=" + launchDate +
                '}';
    }
}