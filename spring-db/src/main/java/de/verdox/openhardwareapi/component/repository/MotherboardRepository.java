package de.verdox.openhardwareapi.component.repository;

import de.verdox.openhardwareapi.model.HardwareTypes;
import de.verdox.openhardwareapi.model.Motherboard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface MotherboardRepository extends HardwareSpecificRepo<Motherboard> {
    @Override
    @EntityGraph(value = "Motherboard.All")
    @Query("""
                select h
                from #{#entityName} h
                where h.id in :ids
                order by h.id asc
            """)
    List<Motherboard> findAllByIdInOrderByIdAsc(@Param("ids") List<Long> ids);

    @Override
    @EntityGraph(value = "Motherboard.All")
    Optional<Motherboard> findByEan(String ean);

    @Override
    @EntityGraph(value = "Motherboard.All")
    Optional<Motherboard> findByMPNIgnoreCase(String mpn);

    @EntityGraph(value = "Motherboard.All")
    @Query("""
           select distinct h
           from #{#entityName} h
           join h.EANs e
           where e in :eans
           """)
    List<Motherboard> findAllByEANNormIn(@Param("eans") Set<String> eans);

    @EntityGraph(value = "Motherboard.All")
    @Query("select h from HardwareSpec h where h.MPN in :mpns")
    List<Motherboard> findAllByMPNNormIn(@Param("mpns") Set<String> mpns);

    List<Motherboard> findByChipsetAndSocketAndFormFactor(HardwareTypes.Chipset chipset, HardwareTypes.CpuSocket cpuSocket, HardwareTypes.MotherboardFormFactor formFactor);

    Page<Motherboard> findByChipsetAndSocketAndFormFactor(HardwareTypes.Chipset chipset, HardwareTypes.CpuSocket cpuSocket, HardwareTypes.MotherboardFormFactor formFactor, Pageable pageable);
}
