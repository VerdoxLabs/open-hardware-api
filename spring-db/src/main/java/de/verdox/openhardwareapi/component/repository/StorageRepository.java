package de.verdox.openhardwareapi.component.repository;

import de.verdox.openhardwareapi.model.Storage;
import org.springframework.stereotype.Repository;

@Repository
public interface StorageRepository extends HardwareSpecificRepo<Storage> {

}
