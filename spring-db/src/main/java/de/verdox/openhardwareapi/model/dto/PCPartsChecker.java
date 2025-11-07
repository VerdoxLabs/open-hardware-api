package de.verdox.openhardwareapi.model.dto;

import de.verdox.openhardwareapi.model.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class PCPartsChecker {
    private CPU cpu;
    private Motherboard motherboard;
    private final List<RAM> rams = new ArrayList<>();
    private final List<GPU> gpus = new ArrayList<>();
    private PSU psu;
    private PCCase pcCase;
    private CPUCooler cpuCooler;

    public void selectCPU(CPU cpu) {
        this.cpu = cpu;
    }

    public void selectMotherboard(Motherboard motherboard) {
        this.motherboard = motherboard;
    }

    public void addRAM(RAM ram) {
        this.rams.add(ram);
    }

    public void addGPU(GPU gpu) {
        this.gpus.add(gpu);
    }

    public void setPSU(PSU psu) {
        this.psu = psu;
    }

    public void setCase(PCCase pcCase) {
        this.pcCase = pcCase;
    }

    public void setCPUCooler(CPUCooler cpuCooler) {
        this.cpuCooler = cpuCooler;
    }
}
