package de.verdox.hwapi.catalog.domain;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
@Entity
@Table(name = "keyboard")
public class Keyboard extends CatalogAccessory<Keyboard> {
    @Override public void checkIfLegal() {}
}
