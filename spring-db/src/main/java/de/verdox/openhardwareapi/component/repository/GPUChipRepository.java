package de.verdox.openhardwareapi.component.repository;

import de.verdox.openhardwareapi.model.GPUChip;
import de.verdox.openhardwareapi.model.HardwareTypes;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GPUChipRepository extends HardwareSpecificRepo<GPUChip> {

    Optional<GPUChip> findByCanonicalModelIgnoreCase(String canonicalModel);

    Optional<GPUChip> findFirstByCanonicalModelIgnoreCaseAndVramType(String canonicalModel, HardwareTypes.VRAM_TYPE vramType);

    Optional<GPUChip> findFirstByCanonicalModelIgnoreCaseAndVramTypeAndVramGb(String canonicalModel, HardwareTypes.VRAM_TYPE vramType, double vramGb);
}
