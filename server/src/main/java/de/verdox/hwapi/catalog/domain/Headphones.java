package de.verdox.hwapi.catalog.domain;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
@Entity
@Table(name = "headphones")
public class Headphones extends CatalogAccessory<Headphones> {
    @Override public void checkIfLegal() {}
}
