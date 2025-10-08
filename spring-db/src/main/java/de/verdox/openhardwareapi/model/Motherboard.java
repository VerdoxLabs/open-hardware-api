package de.verdox.openhardwareapi.model;

import de.verdox.openhardwareapi.model.values.M2Slot;
import de.verdox.openhardwareapi.model.values.PcieSlot;
import de.verdox.openhardwareapi.model.values.USBPort;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@DiscriminatorValue("MOTHERBOARD")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Motherboard extends HardwareSpec {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HardwareTypes.CpuSocket socket = HardwareTypes.CpuSocket.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HardwareTypes.Chipset chipset = HardwareTypes.Chipset.UNKNOWN;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HardwareTypes.MotherboardFormFactor formFactor = HardwareTypes.MotherboardFormFactor.UNKNOWN;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HardwareTypes.RamType ramType = HardwareTypes.RamType.UNKNOWN;


    @PositiveOrZero
    private Integer ramSlots = 0;

    @PositiveOrZero
    private Integer ramCapacity = 0;

    @PositiveOrZero
    private Integer sataSlots = 0;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "m2Slots", joinColumns = @JoinColumn(name = "spec_id"))
    private Set<M2Slot> m2Slots = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pcieSlots", joinColumns = @JoinColumn(name = "spec_id"))
    private Set<PcieSlot> pcieSlots = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usbPort", joinColumns = @JoinColumn(name = "spec_id"))
    private Set<USBPort> usbPort = new HashSet<>();

    @PositiveOrZero
    private int usb3Headers = 0;


    @Override
    public String toString() {
        return "Motherboard{" +
                "socket=" + socket +
                ", chipset=" + chipset +
                ", formFactor=" + formFactor +
                ", ramType=" + ramType +
                ", ramSlots=" + ramSlots +
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