package de.verdox.openhardwareapi.io.pc_combo_scraper;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.component.service.ScrapingService;
import de.verdox.openhardwareapi.model.GPU;
import de.verdox.openhardwareapi.model.GPUChip;
import de.verdox.openhardwareapi.util.GpuRegexParser;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;

public class GPUKomboScraper extends AbstractPCKomboScraper<GPU> {
    public GPUKomboScraper(HardwareSpecService service) {
        super("GPU-Import", service, "https://www.pc-kombo.com/us/components/gpus", GPU::new);
    }

    @Override
    protected boolean supportsUpdatingExisting() {
        return true;
    }

    @Override
    protected void updateExisting(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, GPU target) {
        try {
            GpuRegexParser.ParsedGpu parsedGpu = GpuRegexParser.parse(base.name()).orElse(null);

            if (parsedGpu != null) {
                GPUChip gpu;
                if (target.getVramType() != null && target.getVramGb() > 0) {
                    gpu = hardwareSpecService.findGPUModel(parsedGpu, target.getVramType(), target.getVramGb()).orElse(null);
                } else if (target.getVramType() != null) {
                    gpu = hardwareSpecService.findGPUModel(parsedGpu, target.getVramType()).orElse(null);
                } else {
                    gpu = hardwareSpecService.findGPUModel(parsedGpu).orElse(null);
                }

                if (gpu != null) {
                    target.setChip(gpu);

                    if (target.getPcieVersion() == null) {
                        target.setPcieVersion(gpu.getPcieVersion());
                    }

                    if (target.getVramType() == null) {
                        target.setVramType(gpu.getVramType());
                    }

                    if (target.getVramGb() == 0) {
                        target.setVramGb(gpu.getVramGb());
                    }

                    if (target.getLengthMm() == 0) {
                        target.setLengthMm(gpu.getLengthMm());
                    }

                    if (target.getTdp() == 0) {
                        target.setTdp(gpu.getTdp());
                    }

                    if (target.getLaunchDate() == null) {
                        target.setLaunchDate(gpu.getLaunchDate());
                    }

                } else {
                    //ScrapingService.LOGGER.log(Level.WARNING, "GPU not in database: " + base.name() + " -> " + parsedGpu.canonical());
                }
            } else {
                ScrapingService.LOGGER.log(Level.WARNING, "No canonical name found: " + base.name());
            }

            target.setLengthMm(parseFirstDouble("Length", specs));
            target.setTdp(parseFirstDouble("TDP", specs));
            target.setVramGb(parseFirstDouble("Vram", specs));
        } catch (Exception e) {

        }
    }

    @Override
    protected void parseDetails(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, GPU target) {
        updateExisting(base, specs, specsList, target);
    }
}
