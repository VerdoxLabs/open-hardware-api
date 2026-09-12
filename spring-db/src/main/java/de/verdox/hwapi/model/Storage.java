package de.verdox.hwapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("STORAGE")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@NamedEntityGraph(
        name = "Storage.All",
        includeAllAttributes = true
)
public class Storage extends HardwareSpec<Storage> {

    @Override
    public void merge(Storage other) {
        super.merge(other);
        mergeEnum(other, Storage::getStorageType, Storage::setStorageType, HardwareTypes.StorageType.UNKNOWN);
        mergeEnum(other, Storage::getStorageInterface, Storage::setStorageInterface, HardwareTypes.StorageInterface.UNKNOWN);
        mergeEnum(other, Storage::getStorageFormFactor, Storage::setStorageFormFactor, HardwareTypes.StorageFormFactor.UNKNOWN);
        mergeEnum(other, Storage::getNandType, Storage::setNandType, HardwareTypes.NandType.UNKNOWN);
        mergeEnum(other, Storage::getNvmExpressVersion, Storage::setNvmExpressVersion, HardwareTypes.NvmExpressVersion.UNKNOWN);
        mergeNumber(other, Storage::getCapacityGb, Storage::setCapacityGb);
        mergeNumber(other, Storage::getReadSpeedMbs, Storage::setReadSpeedMbs);
        mergeNumber(other, Storage::getWriteSpeedMbs, Storage::setWriteSpeedMbs);
        mergeBool(other, Storage::getHasDramCache, Storage::setHasDramCache);
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.StorageType storageType = HardwareTypes.StorageType.UNKNOWN; // HDD/SSD

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.StorageInterface storageInterface = HardwareTypes.StorageInterface.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_form_factor", nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.StorageFormFactor storageFormFactor = HardwareTypes.StorageFormFactor.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "nand_type", nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.NandType nandType = HardwareTypes.NandType.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "nvme_version", nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.NvmExpressVersion nvmExpressVersion = HardwareTypes.NvmExpressVersion.UNKNOWN;

    @PositiveOrZero
    @Column(nullable = false)
    private Integer capacityGb = 0;

    @PositiveOrZero
    @Column(name = "read_speed_mbs", nullable = false)
    private Integer readSpeedMbs = 0;

    @PositiveOrZero
    @Column(name = "write_speed_mbs", nullable = false)
    private Integer writeSpeedMbs = 0;

    @Column(name = "has_dram_cache", nullable = false)
    private Boolean hasDramCache = false;

    @Override
    public void checkIfLegal() {
        if (storageType == null) {
            throw new IllegalArgumentException("storage type cannot be null!");
        }

        if (storageInterface == null) {
            throw new IllegalArgumentException("storage interface cannot be null!");
        }
    }

    @Override
    public String toString() {
        return "Storage{" +
                "storageType=" + storageType +
                ", storageInterface=" + storageInterface +
                ", storageFormFactor=" + storageFormFactor +
                ", nandType=" + nandType +
                ", nvmExpressVersion=" + nvmExpressVersion +
                ", capacityGb=" + capacityGb +
                ", readSpeedMbs=" + readSpeedMbs +
                ", writeSpeedMbs=" + writeSpeedMbs +
                ", hasDramCache=" + hasDramCache +
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                ", EAN='" + EANs + '\'' +
                ", MPN='" + MPNs + '\'' +
                ", launchDate=" + launchDate +
                '}';
    }
}