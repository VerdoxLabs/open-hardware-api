package de.verdox.hwapi.catalog.domain;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
@Entity
@Table(name = "operating_system")
public class OperatingSystem extends CatalogAccessory<OperatingSystem> {
    @Override public void checkIfLegal() {}
}
