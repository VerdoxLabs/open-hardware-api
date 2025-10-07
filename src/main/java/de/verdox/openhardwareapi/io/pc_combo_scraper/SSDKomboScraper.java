package de.verdox.openhardwareapi.io.pc_combo_scraper;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.model.HardwareTypes;
import de.verdox.openhardwareapi.model.Storage;

import java.util.List;
import java.util.Map;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;

public class SSDKomboScraper extends AbstractPCKomboScraper<Storage> {
    public SSDKomboScraper(HardwareSpecService service) {
        super("SSD-Import", service, "https://www.pc-kombo.com/us/components/ssds", Storage::new);
    }


    @Override
    protected void parseDetails(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, Storage target) {
        target.setStorageType(HardwareTypes.StorageType.SSD);

        if (extractFirstString("Form Factor", specs).equals("M.2")) {
            target.setStorageInterface(HardwareTypes.StorageInterface.NVME);
        } else {
            target.setStorageInterface(HardwareTypes.StorageInterface.SATA);
        }
        target.setCapacityGb((int) (parseFirstInt("Size", specs)));
    }
}
