package de.verdox.openhardwareapi.io.pc_combo_scraper;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.model.HardwareTypes;
import de.verdox.openhardwareapi.model.PSU;
import de.verdox.openhardwareapi.model.values.PowerConnector;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;

public class PSUKomboScraper extends AbstractPCKomboScraper<PSU> {
    public PSUKomboScraper(HardwareSpecService service) {
        super("PSU-Import", service, "https://www.pc-kombo.com/us/components/psus", PSU::new);
    }


    @Override
    protected void parseDetails(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, PSU target) {
        target.setWattage((int) parseFirstInt("Watt", specs));
        target.setSize(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Size", specs, (s, motherboardFormFactor) -> motherboardFormFactor.getName().equalsIgnoreCase(s)));

        target.setEfficiencyRating(extractFirstEnum(HardwareTypes.PsuEfficiencyRating.class, "Efficiency Rating", specs, (s, e) -> e.name().contains(s.toUpperCase())));

        Set<PowerConnector> powerConnectors = new HashSet<>();

        int cables8Pin = Math.toIntExact(parseFirstInt("PCI-E cables 8-pin", specs));
        int cables6Pin = Math.toIntExact(parseFirstInt("PCI-E cables 6-pin", specs));

        if (cables8Pin > 0) {
            powerConnectors.add(new PowerConnector(HardwareTypes.PowerConnectorType.PCIE_8_PIN, cables8Pin));
        }

        if (cables6Pin > 0) {
            powerConnectors.add(new PowerConnector(HardwareTypes.PowerConnectorType.PCIE_6_PIN, cables6Pin));
        }

        powerConnectors.add(new PowerConnector(HardwareTypes.PowerConnectorType.ATX_24_PIN, 1));

        target.setConnectors(powerConnectors);
    }
}
