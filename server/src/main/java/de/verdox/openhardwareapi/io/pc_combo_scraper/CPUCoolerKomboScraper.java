package de.verdox.openhardwareapi.io.pc_combo_scraper;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.model.CPUCooler;
import de.verdox.openhardwareapi.model.HardwareTypes;

import java.util.List;
import java.util.Map;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;


public class CPUCoolerKomboScraper extends AbstractPCKomboScraper<CPUCooler> {
    public CPUCoolerKomboScraper(HardwareSpecService service) {
        super("CPU-Cooler-Import", service, "https://www.pc-kombo.com/us/components/cpucoolers", CPUCooler::new);
    }


    @Override
    protected void parseDetails(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, CPUCooler target) {

        target.setType(base.name().contains("Liquid") ? HardwareTypes.CoolerType.AIO_LIQUID : HardwareTypes.CoolerType.AIR);

        if (target.getType().equals(HardwareTypes.CoolerType.AIO_LIQUID)) {
            if (base.name().contains("360")) {
                target.setRadiatorLengthMm(360);
            } else if (base.name().contains("240")) {
                target.setRadiatorLengthMm(240);
            } else if (base.name().contains("120")) {
                target.setRadiatorLengthMm(120);
            }
        }

        target.setSupportedSockets(extractEnumSet(HardwareTypes.CpuSocket.class, "Supported Sockets", specs, (s, cpuSocket) -> {
            return s.toUpperCase().contains(cpuSocket.name());
        }));
        target.setTdpWatts((int) parseFirstInt("TDP", specs));
    }
}
