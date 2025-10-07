package de.verdox.openhardwareapi.io.shops.alternate;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.model.*;

public class AlternateScrapers {

    public static AbstractMultiAlternateScraper<CPU> forCPU(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<CPU>()
                .withQuery((EAN, UPC, MPN) -> (CPU) service.findLightByEanMPNUPCSN(CPU.class, EAN, UPC, MPN).orElse(new CPU()))
                .withURLs(
                        "https://www.alternate.de/CPUs"
                )
                .withSpecsTranslation((cpu, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<Motherboard> forMotherboard(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<Motherboard>()
                .withQuery((EAN, UPC, MPN) -> (Motherboard) service.findLightByEanMPNUPCSN(Motherboard.class, EAN, UPC, MPN).orElse(new Motherboard()))
                .withURLs(
                        "https://www.alternate.de/Mainboards/Intel-Mainboards",
                        "https://www.alternate.de/Mainboards/AMD-Mainboards",
                        "https://www.alternate.de/Mainboards/ATX-Mainboards",
                        "https://www.alternate.de/Mainboards/Micro-ATX-Mainboards",
                        "https://www.alternate.de/Mainboards/Mini-ITX-Mainboards"
                )
                .withSpecsTranslation((motherboard, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<PSU> forPSU(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<PSU>()
                .withQuery((EAN, UPC, MPN) -> (PSU) service.findLightByEanMPNUPCSN(PSU.class, EAN, UPC, MPN).orElse(new PSU()))
                .withURLs(
                        "https://www.alternate.de/Netzteile/unter-500-Watt",
                        "https://www.alternate.de/Netzteile/ab-500-Watt",
                        "https://www.alternate.de/Netzteile/ab-750-Watt",
                        "https://www.alternate.de/Netzteile/ab-750-Watt",
                        "https://www.alternate.de/Netzteile/ab-1000-Watt",
                        "https://www.alternate.de/Netzteile/ATX-Netzteile"
                        )
                .withSpecsTranslation((psu, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<PCCase> forCase(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<PCCase>()
                .withQuery((EAN, UPC, MPN) -> (PCCase) service.findLightByEanMPNUPCSN(PCCase.class, EAN, UPC, MPN).orElse(new PCCase()))
                .withURLs(
                        "https://www.alternate.de/PC-Geh%C3%A4use/Midi-Tower",
                        "https://www.alternate.de/PC-Geh%C3%A4use/Big-Tower",
                        "https://www.alternate.de/PC-Geh%C3%A4use/Cube-Geh%C3%A4use",
                        "https://www.alternate.de/PC-Geh%C3%A4use/RGB-Geh%C3%A4use",
                        "https://www.alternate.de/PC-Geh%C3%A4use/ATX-Geh%C3%A4use",
                        "https://www.alternate.de/PC-Geh%C3%A4use/Micro-ATX-Geh%C3%A4use",
                        "https://www.alternate.de/PC-Geh%C3%A4use/Mini-ITX-Geh%C3%A4use",
                        "https://www.alternate.de/PC-Geh%C3%A4use/Reverse-Connector-Geh%C3%A4use",
                        "https://www.alternate.de/PC-Geh%C3%A4use/Weisse-PC-Geh%C3%A4use"
                )
                .withSpecsTranslation((pcCase, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<GPU> forGPU(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<GPU>()
                .withQuery((EAN, UPC, MPN) -> (GPU) service.findLightByEanMPNUPCSN(GPU.class, EAN, UPC, MPN).orElse(new GPU()))
                .withURLs(
                        "https://www.alternate.de/Grafikkarten/NVIDIA-Grafikkarten",
                        "https://www.alternate.de/Grafikkarten/AMD-Grafikkarten",
                        "https://www.alternate.de/Grafikkarten/Intel-Grafikkarten"
                )
                .withSpecsTranslation((gpu, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<CPUCooler> forCPUCooler(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<CPUCooler>()
                .withQuery((EAN, UPC, MPN) -> (CPUCooler) service.findLightByEanMPNUPCSN(CPUCooler.class, EAN, UPC, MPN).orElse(new CPUCooler()))
                .withURLs(
                        "https://www.alternate.de/CPU-K%C3%BChler"
                )
                .withSpecsTranslation((cpuCooler, specs) -> {
                    cpuCooler.setType(HardwareTypes.CoolerType.AIR);
                })
                .build();
    }

    public static AbstractMultiAlternateScraper<CPUCooler> forCPULiquidCooler(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<CPUCooler>()
                .withQuery((EAN, UPC, MPN) -> (CPUCooler) service.findLightByEanMPNUPCSN(CPUCooler.class, EAN, UPC, MPN).orElse(new CPUCooler()))
                .withURLs(
                        "https://www.alternate.de/Wasserk%C3%BChlungen/AiO-Wasserk%C3%BChlung"
                )
                .withSpecsTranslation((cpuCooler, specs) -> {
                    cpuCooler.setType(HardwareTypes.CoolerType.AIO_LIQUID);
                })
                .build();
    }

    public static AbstractMultiAlternateScraper<RAM> forRAM(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<RAM>()
                .withQuery((EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()))
                .withURLs(
                        "https://www.alternate.de/Arbeitsspeicher/DDR5-RAM",
                        "https://www.alternate.de/Arbeitsspeicher/DDR4-RAM",
                        "https://www.alternate.de/Arbeitsspeicher/DDR3-RAM",
                        "https://www.alternate.de/Arbeitsspeicher/DDR2-RAM"
                )
                .withSpecsTranslation((ram, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<Storage> forSataSSD(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<Storage>()
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs(
                        "https://www.alternate.de/SSD/SATA-SSD"
                )
                .withSpecsTranslation((storage, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<Storage> forM2SSD(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<Storage>()
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs(
                        "https://www.alternate.de/SSD/M-2-SSD"
                )
                .withSpecsTranslation((storage, specs) -> {

                })
                .build();
    }

    public static AbstractMultiAlternateScraper<Storage> forHDD(HardwareSpecService service) {
        return new AbstractMultiAlternateScraper.Builder<Storage>()
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs(
                        "https://www.alternate.de/Festplatten/SATA-Festplatten"
                )
                .withSpecsTranslation((storage, specs) -> {

                })
                .build();
    }

}
