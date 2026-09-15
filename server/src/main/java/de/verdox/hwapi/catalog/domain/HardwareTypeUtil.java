package de.verdox.hwapi.catalog.domain;

import java.util.Collection;
import java.util.List;


public class HardwareTypeUtil {
    public static Collection<Class<? extends HardwareSpec<?>>> getSupportedSpecTypes() {
        return List.of(CPU.class, GPU.class, GPUChip.class, RAM.class, CPUCooler.class, PCCase.class, PSU.class, Motherboard.class, Storage.class, Display.class, Fan.class,
                Headphones.class, Keyboard.class, Mouse.class, Speakers.class, Webcam.class, SoundCard.class,
                WiredNetworkCard.class, WirelessNetworkCard.class, FanController.class, ThermalCompound.class,
                ExternalHardDrive.class, OpticalDrive.class, OperatingSystem.class);
    }
}
