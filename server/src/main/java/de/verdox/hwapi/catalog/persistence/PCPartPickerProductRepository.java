package de.verdox.hwapi.catalog.persistence;

import de.verdox.hwapi.catalog.domain.PCPartPickerProduct;
import org.springframework.stereotype.Repository;

@Repository
public interface PCPartPickerProductRepository extends HardwareSpecificRepo<PCPartPickerProduct> {
}
