package de.verdox.hwapi.catalog.domain;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
@Entity
@Table(name = "wired_network_card")
public class WiredNetworkCard extends CatalogAccessory<WiredNetworkCard> {
    @Override public void checkIfLegal() {}
}
