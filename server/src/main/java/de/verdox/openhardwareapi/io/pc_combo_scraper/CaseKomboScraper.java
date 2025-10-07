package de.verdox.openhardwareapi.io.pc_combo_scraper;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.model.HardwareTypes;
import de.verdox.openhardwareapi.model.PCCase;
import de.verdox.openhardwareapi.model.values.DimensionsMm;

import java.util.List;
import java.util.Map;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;

public class CaseKomboScraper extends AbstractPCKomboScraper<PCCase> {
    public CaseKomboScraper(HardwareSpecService service) {
        super("PC-Case-Import", service, "https://www.pc-kombo.com/us/components/cases", PCCase::new);
    }


    @Override
    protected void parseDetails(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, PCCase target) {
        target.setManufacturer(specs.get("Producer").getFirst());

        target.setDimensions(new DimensionsMm());

        target.getDimensions().setWidth(parseFirstDouble("Width", specs));
        target.getDimensions().setDepth(parseFirstDouble("Depth", specs));
        target.getDimensions().setHeight(parseFirstDouble("Height", specs));

        target.setSizeClass(PCCase.classify(target.getDimensions()));

        if(base.name().contains("Midi-Tower") || base.name().contains("Midi")) {
            target.setSizeClass(HardwareTypes.CaseSizeClass.MID_TOWER);
        }
        else if(base.name().contains("Big-Tower") || base.name().contains("Big Tower")) {
            target.setSizeClass(HardwareTypes.CaseSizeClass.FULL_TOWER);
        }

        target.setMotherboardSupport(extractEnumSet(HardwareTypes.MotherboardFormFactor.class, "Motherboard", specs, (s, motherboardFormFactor) -> motherboardFormFactor.getName().equalsIgnoreCase(s)));

        target.setMaxGpuLengthMm(parseFirstDouble("Supported GPU length", specs));
        target.setMaxCpuCoolerHeightMm(parseFirstDouble("Supported CPU cooler height", specs));
    }
}
