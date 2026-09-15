package de.verdox.hwapi.catalog.domain;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
@Entity
@Table(name = "optical_drive")
public class OpticalDrive extends CatalogAccessory<OpticalDrive> {
    @Override public void checkIfLegal() {}
}
