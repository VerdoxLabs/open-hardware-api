package de.verdox.hwapi.catalog.domain;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
@Entity
@Table(name = "thermal_compound")
public class ThermalCompound extends CatalogAccessory<ThermalCompound> {
    @Override public void checkIfLegal() {}
}
