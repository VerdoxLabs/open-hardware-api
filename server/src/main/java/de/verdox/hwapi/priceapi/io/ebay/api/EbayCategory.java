package de.verdox.hwapi.priceapi.io.ebay.api;

import de.verdox.hwapi.model.*;
import lombok.Getter;

@Getter
public enum EbayCategory {
    CPU(CPU.class, "164"),
    RAM(RAM.class, "170083"),
    GPU(GPU.class, "27386"),
    MOTHERBOARD(Motherboard.class, "1244"),
    PSU(PSU.class, "42017"),
    CPU_COOLER(CPUCooler.class, "131503"),
    PC_CASE(PCCase.class, "42014"),
    ;
    private final Class<? extends HardwareSpec<?>> type;
    private final String ebayCategoryId;

    EbayCategory(Class<? extends HardwareSpec<?>> type, String ebayCategoryId) {
        this.type = type;
        this.ebayCategoryId = ebayCategoryId;
    }

    public static EbayCategory fromType(Class<? extends HardwareSpec<?>> type) {
        for (EbayCategory ebayCategory : EbayCategory.values()) {
            if (ebayCategory.type.equals(type)) return ebayCategory;
        }
        return null;
    }
}
