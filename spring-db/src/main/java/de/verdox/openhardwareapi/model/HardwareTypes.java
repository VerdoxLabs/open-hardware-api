package de.verdox.openhardwareapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.util.Set;

public class HardwareTypes {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum Chipset {
        UNKNOWN,
        // Intel LGA1150 (Haswell / Broadwell)
        H81, B85, Q85, Q87, H87, Z87, H97, Z97,

        // Intel LGA1151 (Skylake / Kaby Lake / Coffee Lake)
        H110, B150, Q150, H170, Q170, Z170, B250, Q250, H270, Q270, Z270, H310, H310C, B360, B365, H370, Q370, Z370, Z390,

        // Intel LGA1200 (Comet Lake / Rocket Lake)
        H410, B460, H470, Q470, Z490, H510, B560, H570, Z590, W580,

        // Intel LGA1700 (Alder Lake / Raptor Lake)
        H610, B660, H670, Z690, W680, H710, B760, H770, Z790,

        // AMD AM4 (Ryzen 1000–5000)
        A300, A320, B350, X370, B450, X470, A520, B550, X570, X570S,

        // AMD AM5 (Ryzen 7000+)
        A620, B650, B650E, X670, X670E, B840, B850, X870, X870E,

        // AMD Threadripper TR4 (1000/2000-Serie)
        X399,

        // AMD Server SP3 (EPYC Naples/Rome/Milan)
        // -> SP3 ist "Chipsatzlos", aber Mainboard-Logik oft als "Server Board" bezeichnet
        SP3_PLATFORM
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Getter
    public enum DisplayPanel {
        UNKNOWN("Unknown"),
        IPS("IPS"), FAST_IPS("Fast IPS"), VA("VA"), WOLED(" W OLED"), NANO_IPS("Nano IPS"),

        ;
        private final String name;

        DisplayPanel(String name) {
            this.name = name;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Getter
    public enum DisplaySync {
        G_SYNC("G-Sync"), FREE_SYNC("Free Sync");
        private final String name;

        DisplaySync(String name) {
            this.name = name;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Getter
    public enum CpuSocket {
        UNKNOWN, FP5, sTR4, FT5, FP4, FP6, FP7, FP8, FP9, FP10, FP11,
        FL1, FL2, FL3, FL4, FL5, FL6, FL7, FL8, FL9,
        FP7R1, FP7R2,
        sWRX1, sWRX2, sWRX3, sWRX4, sWRX5, sWRX6, sWRX7, sWRX8,

        // AMD
        AM4(Chipset.A300, Chipset.A320, Chipset.B350, Chipset.X370, Chipset.B450, Chipset.X470, Chipset.A520, Chipset.B550, Chipset.X570, Chipset.X570S),

        AM5(Chipset.A620, Chipset.B650, Chipset.B650E, Chipset.X670, Chipset.X670E, Chipset.B840, Chipset.B850, Chipset.X870, Chipset.X870E),

        TR4(Chipset.X399),

        SP3(Chipset.SP3_PLATFORM),

        // Intel
        LGA1150(Chipset.H81, Chipset.B85, Chipset.Q85, Chipset.Q87, Chipset.H87, Chipset.Z87, Chipset.H97, Chipset.Z97),

        LGA1151(Chipset.H110, Chipset.B150, Chipset.Q150, Chipset.H170, Chipset.Q170, Chipset.Z170, Chipset.B250, Chipset.Q250, Chipset.H270, Chipset.Q270, Chipset.Z270, Chipset.H310, Chipset.H310C, Chipset.B360, Chipset.B365, Chipset.H370, Chipset.Q370, Chipset.Z370, Chipset.Z390),

        LGA1200(Chipset.H410, Chipset.B460, Chipset.H470, Chipset.Q470, Chipset.Z490, Chipset.H510, Chipset.B560, Chipset.H570, Chipset.Z590, Chipset.W580),

        LGA1700(Chipset.H610, Chipset.B660, Chipset.H670, Chipset.Z690, Chipset.W680, Chipset.H710, Chipset.B760, Chipset.H770, Chipset.Z790),

        LGA1744(), LGA1851(), BGA1744(), BGA1964(), BGA2551(), BGA2049(), BGA2114(), BGA2833(), BGA1792(), BGA1781(), LGA1449(), BGA1787(), BGA1528(), UTBGA1377(), BGA1440(), BGA1526(),

        STR5(),
        ;

        private final Set<Chipset> chipsets;

        CpuSocket(Chipset... chipsets) {
            this.chipsets = Set.of(chipsets);
        }
    }


    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum RamType {UNKNOWN, DDR5, DDR4, DDR3, DDR2, DDR}

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum StorageType {UNKNOWN, HDD, SSD}

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum StorageInterface {UNKNOWN, SATA, NVME}

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum PcieVersion {UNKNOWN, GEN1, GEN2, GEN3, GEN4, GEN5, GEN6}

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum CoolerType {UNKNOWN, AIR, AIO_LIQUID}

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Getter
    public enum MotherboardFormFactor {
        UNKNOWN("Unknown"), ITX("ITX"), M_ATX("Micro-ATX"), ATX("ATX"), E_ATX("E-ATX");
        private final String name;

        MotherboardFormFactor(String name) {
            this.name = name;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Getter
    public enum PSUFormFactor {
        UNKNOWN("Unknown"),
        ATX("ATX"),
        SFX("SFX"),
        SFX_L("SFX-L"),
        TFX("TFX"),
        FLEX_ATX("Flex ATX"),
        ;
        private final String name;

        PSUFormFactor(String name) {
            this.name = name;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum PSU_MODULARITY {UNKNOWN, NON_MODULAR, SEMI_MODULAR, FULL_MODULAR}

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum PsuEfficiencyRating {UNKNOWN, NONE, BRONZE, SILVER, GOLD, PLATINUM, TITANIUM}

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum CaseSizeClass {UNKNOWN, MINI_ITX, MICRO_ATX, MID_TOWER, FULL_TOWER}

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum VRAM_TYPE {UNKNOWN, GDDR, GDDR2, GDDR3, GDDR4, GDDR5, GDDR5X, GDDR6, GDDR6X, HBM1, HBM2, HBM3, HBM2E, LPDDR5, DDR5, DDR4}

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum PowerConnectorType {
        // Mainboard / CPU
        ATX_24_PIN, EPS_4_PIN, EPS_8_PIN, EPS_4_PLUS_4_PIN,


        // GPU / PCIe
        PCIE_6_PIN, PCIE_8_PIN, PCIE_12_PIN, PCIE_12VHPWR_16_PIN,


        // Storage / Peripherie
        SATA_POWER, MOLEX_4_PIN, BERG_FLOPPY_4_PIN,


        // Spezialfälle
        PROPRIETARY, OTHER
    }

    /**
     * USB Anschluss-Typ (Bauform/Stecker)
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum UsbConnectorType {USB_A, USB_C, INTERNAL_HEADER_9PIN, INTERNAL_HEADER_19PIN, USB_C_HEADER}


    /**
     * USB Protokoll-/Versionsfamilie
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum UsbVersion {USB2_0, USB3_0, USB3_1, USB3_2_GEN1, USB3_2_GEN2, USB3_2_GEN2x2, USB4, THUNDERBOLT4}


    /**
     * Position der Ports
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum PortLocation {REAR_IO, FRONT_HEADER}


    /**
     * Ethernet-Geschwindigkeit
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum EthernetSpeed {ETH_1G, ETH_2_5G, ETH_5G, ETH_10G}


    /**
     * WLAN-Standard
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum WifiStandard {WIFI4_80211N, WIFI5_80211AC, WIFI6_80211AX, WIFI6E_80211AX_6GHZ, WIFI7_80211BE}


    /**
     * Display-Ausgangs-Typen am Board
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum DisplayOutputType {HDMI, DISPLAYPORT, DVI, VGA}
}
