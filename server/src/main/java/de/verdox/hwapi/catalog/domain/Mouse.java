package de.verdox.hwapi.catalog.domain;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
@Entity
@Table(name = "mouse")
public class Mouse extends CatalogAccessory<Mouse> {
    @Override public void checkIfLegal() {}
}
