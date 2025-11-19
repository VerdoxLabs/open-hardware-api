package de.verdox.hwapi.model;

import java.util.Collection;
import java.util.List;


public class HardwareTypeUtil {
    public static Collection<Class<? extends HardwareSpec<?>>> getSupportedSpecTypes() {
        return List.of(CPU.class, GPU.class, RAM.class, CPUCooler.class, PCCase.class, PSU.class, Motherboard.class, Storage.class, Display.class);
    }
}
