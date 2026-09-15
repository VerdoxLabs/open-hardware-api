package de.verdox.hwapi.catalog.domain;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
@Entity
@Table(name = "wireless_network_card")
public class WirelessNetworkCard extends CatalogAccessory<WirelessNetworkCard> {
    @Override public void checkIfLegal() {}
}
