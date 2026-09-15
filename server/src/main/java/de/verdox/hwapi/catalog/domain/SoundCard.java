package de.verdox.hwapi.catalog.domain;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
@Entity
@Table(name = "sound_card")
public class SoundCard extends CatalogAccessory<SoundCard> {
    @Override public void checkIfLegal() {}
}
