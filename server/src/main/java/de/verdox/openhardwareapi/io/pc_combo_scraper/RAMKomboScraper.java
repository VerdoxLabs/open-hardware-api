package de.verdox.openhardwareapi.io.pc_combo_scraper;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.component.service.ScrapingService;
import de.verdox.openhardwareapi.model.HardwareTypes;
import de.verdox.openhardwareapi.model.RAM;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;

public class RAMKomboScraper extends AbstractPCKomboScraper<RAM> {
    public RAMKomboScraper(HardwareSpecService service) {
        super("RAM-Import", service, "https://www.pc-kombo.com/us/components/rams", RAM::new);
    }

    @Override
    protected boolean supportsUpdatingExisting() {
        return true;
    }

    @Override
    protected void updateExisting(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, RAM target) {
        int sticks = Math.toIntExact(parseFirstInt("Sticks", specs));

        target.setType(extractDdrType(extractFirstString("Ram Type", specs)));
        target.setSizeGb(Math.toIntExact(parseFirstInt("Size", specs)));
        target.setSpeedMtps(Math.toIntExact(parseFirstInt("Clock", specs)));



        int[] timings = parseTimings(extractFirstString("Timings", specs));
        target.setCasLatency(timings[0]);
        target.setRowAddressToColumnAddressDelay(timings[1]);
        target.setRowPrechargeTime(timings[2]);
        target.setRowActiveTime(timings[3]);

        target.setSticks(sticks);
    }

    @Override
    protected void parseDetails(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, RAM target) {
        updateExisting(base, specs, specsList, target);
    }

    public static HardwareTypes.RamType extractDdrType(String text) {
        for (HardwareTypes.RamType value : HardwareTypes.RamType.values()) {
            if (text.toLowerCase().contains(value.name().toLowerCase())) {
                return value;
            }
        }
        return HardwareTypes.RamType.DDR4;
    }

    public static int[] parseTimings(String timings) {
        try {
            if (timings == null || timings.isEmpty()) return new int[4];
            String[] parts = timings.replace("--", "-").replace("-2N", "").replace("CL", "").split("-");

            int[] result = new int[4];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Integer.parseInt(parts[i].trim().replace("2N", ""));
            }
            return result;
        } catch (Exception e) {
            ScrapingService.LOGGER.log(Level.SEVERE, "Failed to parse timings " + timings, e.getMessage());
            return new int[4];
        }
    }
}
