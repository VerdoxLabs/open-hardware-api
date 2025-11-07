package de.verdox.openhardwareapi.model.clustering;

import de.verdox.openhardwareapi.model.*;

public class ClusterRules {
    public static ClusterRule<RAM> RAM_CLUSTER =
            ClusterRule.forType(RAM.class)
                    .byInt(ram -> ram.getSizeGb() * ram.getSticks())
                    .and(builder -> builder.byEnum(RAM::getType));

    public static ClusterRule<Motherboard> MOTHERBOARD_CLUSTER =
            ClusterRule.forType(Motherboard.class)
                    .byEnum(Motherboard::getChipset);

    public static ClusterRule<PSU> PSU_CLUSTER =
            ClusterRule.forType(PSU.class).byEnum(PSU::getModularity)
                    .and(rule -> rule.byEnum(PSU::getEfficiencyRating));

    public static ClusterRule<Storage> STORAGE_CLUSTER =
            ClusterRule.forType(Storage.class).byEnum(Storage::getStorageInterface)
                    .and(rule -> rule.byEnum(Storage::getStorageType))
                    .and(rule -> rule.byInt(Storage::getCapacityGb));
}
