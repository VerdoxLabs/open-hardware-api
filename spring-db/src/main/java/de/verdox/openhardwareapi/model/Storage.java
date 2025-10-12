package de.verdox.openhardwareapi.model;

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
public class Storage extends HardwareSpec<Storage> {

    @Override
    public void merge(Storage other) {
        super.merge(other);
        mergeEnum(other, Storage::getStorageType, Storage::setStorageType, HardwareTypes.StorageType.UNKNOWN);
        mergeEnum(other, Storage::getStorageInterface, Storage::setStorageInterface, HardwareTypes.StorageInterface.UNKNOWN);
        mergeNumber(other, Storage::getCapacityGb, Storage::setCapacityGb);
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HardwareTypes.StorageType storageType; // HDD/SSD


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HardwareTypes.StorageInterface storageInterface;


    @PositiveOrZero
    @Column(nullable = false)
    private Integer capacityGb = 0;

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
                ", capacityGb=" + capacityGb +
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                ", EAN='" + EAN + '\'' +
                ", MPN='" + MPN + '\'' +
                ", UPC='" + UPC + '\'' +
                ", launchDate=" + launchDate +
                '}';
    }
}