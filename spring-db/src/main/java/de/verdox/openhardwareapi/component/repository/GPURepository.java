package de.verdox.openhardwareapi.component.repository;

import de.verdox.openhardwareapi.model.GPU;
import org.springframework.stereotype.Repository;

@Repository
public interface GPURepository extends HardwareSpecificRepo<GPU> {

}
