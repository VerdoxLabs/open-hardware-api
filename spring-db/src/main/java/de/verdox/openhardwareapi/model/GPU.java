package de.verdox.openhardwareapi.model;


import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("GPU")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GPU extends HardwareSpec {
    @PositiveOrZero
    private double lengthMm = 0;

    @ManyToOne(fetch = FetchType.EAGER, optional = true)
    @JoinColumn(name = "gpu_chip_id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_gpu__chip"))
    private GPUChip chip;

    @Enumerated(EnumType.STRING)
    private HardwareTypes.PcieVersion pcieVersion = HardwareTypes.PcieVersion.UNKNOWN;

    @Enumerated(EnumType.STRING)
    private HardwareTypes.VRAM_TYPE vramType = HardwareTypes.VRAM_TYPE.UNKNOWN; // z.B. GDDR6, GDDR6X

    @PositiveOrZero
    private double vramGb = 0;

    private double tdp = 0;

    @Override
    public void checkIfLegal() {

    }
}