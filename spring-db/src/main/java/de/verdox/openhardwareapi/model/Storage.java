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
public class Storage extends HardwareSpec {
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
    public String toString() {
        return "Storage{" +
                "storageType=" + storageType +
                ", storageInterface=" + storageInterface +
                ", capacityGb=" + capacityGb +
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                ", launchDate=" + launchDate +
                ", tags=" + tags +
                ", attributes=" + attributes +
                '}';
    }

    @Override
    public void checkIfLegal() {
        if(storageType == null) {
            throw new IllegalArgumentException("storage type cannot be null!");
        }

        if(storageInterface == null) {
            throw new IllegalArgumentException("storage interface cannot be null!");
        }
    }
}