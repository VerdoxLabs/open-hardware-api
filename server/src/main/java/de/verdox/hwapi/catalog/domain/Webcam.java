package de.verdox.hwapi.catalog.domain;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
@Entity
@Table(name = "webcam")
public class Webcam extends CatalogAccessory<Webcam> {
    @Override public void checkIfLegal() {}
}
