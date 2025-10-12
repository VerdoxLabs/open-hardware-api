package de.verdox.openhardwareapi.io.api;

import de.verdox.openhardwareapi.model.HardwareSpec;

import java.util.List;
import java.util.Map;

public interface ScrapedHardwareMapper<HARDWARE extends HardwareSpec<HARDWARE>> {
    String id();

    String[] urlsToScrape();

    HARDWARE findHardwareOrCreate(String EAN, String UPC, String MPN);

    void translateSpecsToTarget(Map<String, List<String>> specs, HARDWARE target);
}
