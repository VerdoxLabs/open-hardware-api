package de.verdox.openhardwareapi.io.shops.mindfactory;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.model.*;

public class MindfactoryScrapers {

    public static AbstractMultiMindfactoryScraper<CPU> forCPU(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<CPU>()
                .withQuery((EAN, UPC, MPN) -> (CPU) service.findLightByEanMPNUPCSN(CPU.class, EAN, UPC, MPN).orElse(new CPU()))
                .withURLs("https://www.mindfactory.de/Hardware/Prozessoren+(CPU).html")
                .withSpecsTranslation((cpu, specs) -> {
                    if (cpu.getModel().contains("AMD")) {
                        cpu.setManufacturer("AMD");
                    } else if (cpu.getModel().contains("Intel")) {
                        cpu.setManufacturer("Intel");
                    }
                })
                .build();
    }

    public static AbstractMultiMindfactoryScraper<Motherboard> forMotherboard(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<Motherboard>()
                .withQuery((EAN, UPC, MPN) -> (Motherboard) service.findLightByEanMPNUPCSN(Motherboard.class, EAN, UPC, MPN).orElse(new Motherboard()))
                .withURLs("https://www.mindfactory.de/Hardware/Mainboards/Desktop+Mainboards.html", "https://www.mindfactory.de/Hardware/Mainboards/Server+Mainboards.html")
                .withSpecsTranslation((motherboard, specs) -> {

                })
                .build();
    }

    public static AbstractMultiMindfactoryScraper<PSU> forPSU(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<PSU>()
                .withQuery((EAN, UPC, MPN) -> (PSU) service.findLightByEanMPNUPCSN(PSU.class, EAN, UPC, MPN).orElse(new PSU()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Netzteile+~+USVs+(PSU)/Netzteile+ATX.html",
                        "https://www.mindfactory.de/Hardware/Netzteile+~+USVs+(PSU)/Netzteile+SFX.html"
                )
                .withSpecsTranslation((psu, specs) -> {

                })
                .build();
    }

    public static AbstractMultiMindfactoryScraper<PCCase> forCase(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<PCCase>()
                .withQuery((EAN, UPC, MPN) -> (PCCase) service.findLightByEanMPNUPCSN(PCCase.class, EAN, UPC, MPN).orElse(new PCCase()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Gehaeuse/Big+Tower+Gehaeuse.html",
                        "https://www.mindfactory.de/Hardware/Gehaeuse/Midi+Tower+ohne+NT.html",
                        "https://www.mindfactory.de/Hardware/Gehaeuse/Mini+Tower+Gehaeuse.html",
                        "https://www.mindfactory.de/Hardware/Gehaeuse/Wuerfel+Gehaeuse.html",
                        "https://www.mindfactory.de/Hardware/Gehaeuse/Desktop+~+HTPC+Gehaeuse.html",
                        "https://www.mindfactory.de/Hardware/Gehaeuse/ITX+Gehaeuse.html",
                        "https://www.mindfactory.de/Hardware/Gehaeuse/Server+Gehaeuse.html",
                        "https://www.mindfactory.de/Hardware/Gehaeuse/Gehaeuse+gedaemmt.html"
                )
                .withSpecsTranslation((pcCase, specs) -> {

                })
                .build();
    }

    public static AbstractMultiMindfactoryScraper<GPU> forGPU(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<GPU>()
                .withQuery((EAN, UPC, MPN) -> (GPU) service.findLightByEanMPNUPCSN(GPU.class, EAN, UPC, MPN).orElse(new GPU()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/GeForce+RTX+fuer+Gaming.html",
                        "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/GeForce+GT+fuer+Multimedia.html",
                        "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/Radeon+RX+Serie.html",
                        "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/Intel+Arc.html",
                        "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/AMD+Radeon+Pro+Duo.html",
                        "https://www.mindfactory.de/Hardware/Grafikkarten+(VGA)/Quadro.html"
                )
                .withSpecsTranslation((gpu, specs) -> {

                })
                .build();
    }

    public static AbstractMultiMindfactoryScraper<CPUCooler> forCPUCooler(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<CPUCooler>()
                .withQuery((EAN, UPC, MPN) -> (CPUCooler) service.findLightByEanMPNUPCSN(CPUCooler.class, EAN, UPC, MPN).orElse(new CPUCooler()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Kuehlung+Luft/CPU+Kuehler.html"
                )
                .withSpecsTranslation((cpuCooler, specs) -> {
                    cpuCooler.setType(HardwareTypes.CoolerType.AIR);
                })
                .build();
    }

    public static AbstractMultiMindfactoryScraper<CPUCooler> forCPULiquidCooler(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<CPUCooler>()
                .withQuery((EAN, UPC, MPN) -> (CPUCooler) service.findLightByEanMPNUPCSN(CPUCooler.class, EAN, UPC, MPN).orElse(new CPUCooler()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Kuehlung+Wasser+(WaKue)/All-in-One+WaKue+(AIO).html"
                )
                .withSpecsTranslation((cpuCooler, specs) -> {
                    cpuCooler.setType(HardwareTypes.CoolerType.AIO_LIQUID);
                })
                .build();
    }

    public static AbstractMultiMindfactoryScraper<RAM> forRAM(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<RAM>()
                .withQuery((EAN, UPC, MPN) -> (RAM) service.findLightByEanMPNUPCSN(RAM.class, EAN, UPC, MPN).orElse(new RAM()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR5+Module.html",
                        "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR4+Module.html",
                        "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR3+Module.html",
                        "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR2+Module.html",
                        "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR5+ECC+Module.html",
                        "https://www.mindfactory.de/Hardware/Arbeitsspeicher+(RAM)/DDR4+ECC+Module.html"
                )
                .withSpecsTranslation((ram, specs) -> {

                })
                .build();
    }

    public static AbstractMultiMindfactoryScraper<Storage> forSSD(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<Storage>()
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Solid+State+Drives+(SSD).html"
                )
                .withSpecsTranslation((storage, specs) -> {

                })
                .build();
    }

    public static AbstractMultiMindfactoryScraper<Storage> forHDD(HardwareSpecService service) {
        return new AbstractMultiMindfactoryScraper.Builder<Storage>()
                .withQuery((EAN, UPC, MPN) -> (Storage) service.findLightByEanMPNUPCSN(Storage.class, EAN, UPC, MPN).orElse(new Storage()))
                .withURLs(
                        "https://www.mindfactory.de/Hardware/Festplatten+(HDD)/Interne+Festplatten.html"
                )
                .withSpecsTranslation((storage, specs) -> {

                })
                .build();
    }
}
