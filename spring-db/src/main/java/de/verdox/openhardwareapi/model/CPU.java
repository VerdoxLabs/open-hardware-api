package de.verdox.openhardwareapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("CPU")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CPU extends HardwareSpec<CPU> {

    @Override
    public void merge(CPU other) {
        super.merge(other);
        mergeEnum(other, CPU::getSocket, CPU::setSocket, HardwareTypes.CpuSocket.UNKNOWN);
        mergeString(other, CPU::getIntegratedGraphics, CPU::setIntegratedGraphics);
        mergeNumber(other, CPU::getCores, CPU::setCores);
        mergeNumber(other, CPU::getEfficiencyCores, CPU::setEfficiencyCores);
        mergeNumber(other, CPU::getPerformanceCores, CPU::setPerformanceCores);
        mergeNumber(other, CPU::getThreads, CPU::setThreads);
        mergeNumber(other, CPU::getBaseClockMhz, CPU::setBaseClockMhz);
        mergeNumber(other, CPU::getBaseClockMhzEfficiency, CPU::setBaseClockMhzEfficiency);
        mergeNumber(other, CPU::getBaseClockMhzPerformance, CPU::setBaseClockMhzPerformance);
        mergeNumber(other, CPU::getBoostClockMhz, CPU::setBoostClockMhz);
        mergeNumber(other, CPU::getL3CacheMb, CPU::setL3CacheMb);
        mergeNumber(other, CPU::getTdpWatts, CPU::setTdpWatts);
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.CpuSocket socket = HardwareTypes.CpuSocket.UNKNOWN;

    private String integratedGraphics = "Not available";

    @PositiveOrZero
    @Column(nullable = false)
    private int cores = 0;

    @PositiveOrZero
    @Column(nullable = false)
    private int efficiencyCores = 0;

    @PositiveOrZero
    @Column(nullable = false)
    private int performanceCores = 0;


    @PositiveOrZero
    @Column(nullable = false)
    private int threads = 0;


    @PositiveOrZero
    @Column(nullable = false)
    private double baseClockMhz = 0;

    @PositiveOrZero
    @Column(nullable = false)
    private double baseClockMhzEfficiency = 0;

    @PositiveOrZero
    @Column(nullable = false)
    private double baseClockMhzPerformance = 0;

    @PositiveOrZero
    private double boostClockMhz = 0;


    @PositiveOrZero
    private int l3CacheMb = 0;


    @PositiveOrZero
    private int tdpWatts = 0;

    @Override
    public String toString() {
        return "CPU{" +
                "boostClockMhz=" + boostClockMhz +
                ", l3CacheMb=" + l3CacheMb +
                ", tdpWatts=" + tdpWatts +
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                ", EAN='" + EAN + '\'' +
                ", MPN='" + MPN + '\'' +
                ", socket=" + socket +
                ", integratedGraphics='" + integratedGraphics + '\'' +
                ", cores=" + cores +
                ", efficiencyCores=" + efficiencyCores +
                ", performanceCores=" + performanceCores +
                ", threads=" + threads +
                ", baseClockMhz=" + baseClockMhz +
                ", baseClockMhzEfficiency=" + baseClockMhzEfficiency +
                ", baseClockMhzPerformance=" + baseClockMhzPerformance +
                ", launchDate=" + launchDate +
                '}';
    }

    @Override
    public void checkIfLegal() {

    }
}