package de.verdox.openhardwareapi.io.pc_combo_scraper;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.model.Display;
import de.verdox.openhardwareapi.model.HardwareTypes;

import java.util.List;
import java.util.Map;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;

public class DisplayKomboScraper extends AbstractPCKomboScraper<Display> {
    public DisplayKomboScraper(HardwareSpecService service) {
        super("PC-Case-Import", service, "https://www.pc-kombo.com/us/components/displays", Display::new);
    }

    @Override
    protected void updateExisting(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, Display target) {
        target.setManufacturer(specs.get("Producer").getFirst());

        String[] resolutions = extractFirstString("Resolution", specs).split("x");


        target.setRefreshRate((int) parseFirstInt("Refresh Rate", specs));

        target.setDisplayPanel(extractFirstEnum(HardwareTypes.DisplayPanel.class, "Panel", specs, (s, displayPanel) -> displayPanel.getName().equalsIgnoreCase(s)));
        target.setDisplaySyncs(extractEnumSet(HardwareTypes.DisplaySync.class, "Sync", specs, (s, displaySync) -> displaySync.getName().equalsIgnoreCase(s)));

        target.setHdmiPorts((int) parseFirstInt("HDMI", specs));
        target.setDisplayPorts((int) parseFirstInt("DisplayPort", specs));
        target.setDviPorts((int) parseFirstInt("DVI", specs));
        target.setVgaPorts((int) parseFirstInt("VGA", specs));
        target.setResponseTimeMS((int) parseFirstInt("Response Time", specs));
        target.setInchSize(parseFirstDouble("Size", specs));

        if (resolutions.length >= 1) {
            target.setResWidth(parseIntSafely(resolutions[0]));
        }
        if (resolutions.length >= 2) {
            target.setResHeight(parseIntSafely(resolutions[1]));
        }

        target.setIntegratedSpeakers(parseBoolean("Speakers", specs));
        target.setCurved(parseBoolean("Curved", specs));
        target.setAdjustableSize(parseBoolean("Adjustable Height", specs));
    }

    @Override
    protected boolean supportsUpdatingExisting() {
        return true;
    }

    @Override
    protected void parseDetails(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, Display target) {
        updateExisting(base, specs, specsList, target);
    }

    private int parseIntSafely(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
