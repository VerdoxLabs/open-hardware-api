package de.verdox.hwapi.catalog.domain;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
@Entity
@Table(name = "fan_controller")
public class FanController extends CatalogAccessory<FanController> {
    @Override public void checkIfLegal() {}
}
