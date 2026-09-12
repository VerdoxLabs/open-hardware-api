package de.verdox.hwapi.util;

import de.verdox.hwapi.util.ComponentTypeClassifier.ComponentKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentTypeClassifierTest {

    @Test
    void classifiesGpu() {
        assertThat(ComponentTypeClassifier.classify("Nvidia GeForce RTX 4070 Super"))
                .isEqualTo(ComponentKind.GPU);
        assertThat(ComponentTypeClassifier.classify("Grafikkarte RTX 4070 VB")).isEqualTo(ComponentKind.GPU);
        assertThat(ComponentTypeClassifier.classify("Radeon RX 9060 XT")).isEqualTo(ComponentKind.GPU);
        assertThat(ComponentTypeClassifier.classify("GTX 1080 8GB")).isEqualTo(ComponentKind.GPU);
    }

    @Test
    void classifiesCpu() {
        assertThat(ComponentTypeClassifier.classify("Intel Core i9 13900K")).isEqualTo(ComponentKind.CPU);
        assertThat(ComponentTypeClassifier.classify("AMD Ryzen 7 5800X")).isEqualTo(ComponentKind.CPU);
        assertThat(ComponentTypeClassifier.classify("CPU i7 12700")).isEqualTo(ComponentKind.CPU);
    }

    @Test
    void classifiesRam() {
        assertThat(ComponentTypeClassifier.classify("DDR4 RAM 16GB 3200")).isEqualTo(ComponentKind.RAM);
        assertThat(ComponentTypeClassifier.classify("DDR5 32GB Kit")).isEqualTo(ComponentKind.RAM);
        assertThat(ComponentTypeClassifier.classify("Arbeitspeicher 8GB")).isEqualTo(ComponentKind.RAM);
    }

    @Test
    void classifiesStorage() {
        assertThat(ComponentTypeClassifier.classify("SSD 1TB NVMe")).isEqualTo(ComponentKind.STORAGE);
        assertThat(ComponentTypeClassifier.classify("Festplatte 2TB SATA")).isEqualTo(ComponentKind.STORAGE);
    }

    @Test
    void classifiesMotherboardPsuCaseDisplay() {
        assertThat(ComponentTypeClassifier.classify("Mainboard B550 AM4")).isEqualTo(ComponentKind.MOTHERBOARD);
        assertThat(ComponentTypeClassifier.classify("Netzteil 850W Gold")).isEqualTo(ComponentKind.PSU);
        assertThat(ComponentTypeClassifier.classify("Gehäuse Mid-Tower")).isEqualTo(ComponentKind.CASE);
        assertThat(ComponentTypeClassifier.classify("Monitor 27 Zoll 144Hz")).isEqualTo(ComponentKind.DISPLAY);
    }

    @Test
    void ambiguousSocketQueryResolvesToCpu() {
        // "Ryzen 5 5800X AM4": CPU-Signale (ryzen) > Mainboard-Signale (am4) -> CPU.
        assertThat(ComponentTypeClassifier.classify("Ryzen 5 5800X AM4")).isEqualTo(ComponentKind.CPU);
    }

    @Test
    void pcBundleIsTransparent() {
        // Explizites "PC"-Signal -> Bundles sollen nicht gefiltert werden.
        assertThat(ComponentTypeClassifier.classify("Gaming PC i7 + RTX 4070")).isEqualTo(ComponentKind.PC);
        assertThat(ComponentTypeClassifier.classify("PC Set mit Monitor")).isEqualTo(ComponentKind.PC);
        assertThat(ComponentKind.PC.isComponentType()).isFalse();
    }

    @Test
    void unknownAndEmptyAreTransparent() {
        assertThat(ComponentTypeClassifier.classify("Kiste")).isEqualTo(ComponentKind.OTHER);
        assertThat(ComponentTypeClassifier.classify("")).isEqualTo(ComponentKind.UNKNOWN);
        assertThat(ComponentTypeClassifier.classify(null)).isEqualTo(ComponentKind.UNKNOWN);
        assertThat(ComponentKind.OTHER.isComponentType()).isFalse();
        assertThat(ComponentKind.UNKNOWN.isComponentType()).isFalse();
        assertThat(ComponentKind.GPU.isComponentType()).isTrue();
    }
}
