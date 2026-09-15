package de.verdox.hwapi.catalog.domain;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
@Entity
@Table(name = "speakers")
public class Speakers extends CatalogAccessory<Speakers> {
    @Override public void checkIfLegal() {}
}
