package de.verdox.openhardwareapi.model;

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
public class CPU extends HardwareSpec {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HardwareTypes.CpuSocket socket = HardwareTypes.CpuSocket.UNKNOWN;

    private String integratedGraphics = "Not available";

    @PositiveOrZero
    @Column(nullable = false)
    private int cores = 0;


    @PositiveOrZero @Column(nullable = false)
    private int threads = 0;


    @PositiveOrZero @Column(nullable = false)
    private double baseClockMhz = 0;


    @PositiveOrZero
    private double boostClockMhz = 0;


    @PositiveOrZero
    private int l3CacheMb = 0;


    @PositiveOrZero
    private int tdpWatts = 0;

    @Override
    public String toString() {
        return "CPU{" +
                "socket=" + socket +
                ", cores=" + cores +
                ", threads=" + threads +
                ", baseClockMhz=" + baseClockMhz +
                ", boostClockMhz=" + boostClockMhz +
                ", l3CacheMb=" + l3CacheMb +
                ", tdpWatts=" + tdpWatts +
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                ", launchDate=" + launchDate +
                ", tags=" + tags +
                ", attributes=" + attributes +
                '}';
    }

    @Override
    public void checkIfLegal() {

    }
}