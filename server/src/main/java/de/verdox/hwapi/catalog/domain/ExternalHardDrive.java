package de.verdox.hwapi.catalog.domain;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
@Entity
@Table(name = "external_hard_drive")
public class ExternalHardDrive extends CatalogAccessory<ExternalHardDrive> {
    @Override public void checkIfLegal() {}
}
