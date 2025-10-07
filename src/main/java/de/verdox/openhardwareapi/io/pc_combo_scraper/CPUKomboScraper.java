package de.verdox.openhardwareapi.io.pc_combo_scraper;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.io.api.ComponentWebScraper;
import de.verdox.openhardwareapi.model.CPU;
import de.verdox.openhardwareapi.model.HardwareTypes;

import java.util.List;
import java.util.Map;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;

public class CPUKomboScraper extends AbstractPCKomboScraper<CPU> {
    public CPUKomboScraper(HardwareSpecService service) {
        super("CPU-Import", service, "https://www.pc-kombo.com/us/components/cpus", CPU::new);
    }

    @Override
    protected boolean supportsUpdatingExisting() {
        return true;
    }

    @Override
    protected void parseDetails(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, CPU target) {
        target.setSocket(ComponentWebScraper.extractFirstEnum(HardwareTypes.CpuSocket.class, "Socket", specs, (s, e) -> e.name().contains(s)));

        target.setTdpWatts((int) parseFirstInt("TDP", specs));

        target.setIntegratedGraphics(extractFirstString("Integrated graphics", specs));

        target.setCores((int) parseFirstInt("Cores", specs));
        target.setThreads((int) parseFirstInt("Threads", specs));

        target.setBaseClockMhz(parseFirstInt("Base Clock", specs) * 1000);
        target.setBoostClockMhz(parseFirstInt("Turbo Clock", specs) * 1000);

        target.setL3CacheMb((int) parseFirstInt("L3 Cache", specs));
    }
}
