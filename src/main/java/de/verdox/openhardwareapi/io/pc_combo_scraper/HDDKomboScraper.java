package de.verdox.openhardwareapi.io.pc_combo_scraper;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.model.HardwareTypes;
import de.verdox.openhardwareapi.model.Storage;

import java.util.List;
import java.util.Map;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;


public class HDDKomboScraper extends AbstractPCKomboScraper<Storage> {
    public HDDKomboScraper(HardwareSpecService service) {
        super("HDD-Import", service, "https://www.pc-kombo.com/us/components/hdds", Storage::new);
    }


    @Override
    protected void parseDetails(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, Storage target) {
        target.setStorageType(HardwareTypes.StorageType.HDD);
        target.setStorageInterface(HardwareTypes.StorageInterface.SATA);
        target.setCapacityGb((int) (parseFirstInt("Size", specs) * 1000));
    }
}
