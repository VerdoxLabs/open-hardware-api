package de.verdox.hwapi.model;


import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

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
    public void merge(GPU other) {
        super.merge(other);
        mergeNumber(other, GPU::getLengthMm, GPU::setLengthMm);
        merge(other, GPU::getChip, GPU::setChip, Objects::isNull);
        mergeEnum(other, GPU::getPcieVersion, GPU::setPcieVersion, HardwareTypes.PcieVersion.UNKNOWN);
        mergeEnum(other, GPU::getVramType, GPU::setVramType, HardwareTypes.VRAM_TYPE.UNKNOWN);
        mergeNumber(other, GPU::getVramGb, GPU::setVramGb);
        mergeNumber(other, GPU::getTdp, GPU::setTdp);
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

    @Override
    public void checkIfLegal() {

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
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                ", EAN='" + EANs + '\'' +
                ", launchDate=" + launchDate +
                '}';
    }
}