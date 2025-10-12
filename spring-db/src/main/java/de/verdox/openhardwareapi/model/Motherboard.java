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
public class Motherboard extends HardwareSpec<Motherboard> {

    @Override
    public void merge(Motherboard other) {
        super.merge(other);
        mergeEnum(other, Motherboard::getSocket, Motherboard::setSocket, HardwareTypes.CpuSocket.UNKNOWN);
        mergeEnum(other, Motherboard::getChipset, Motherboard::setChipset, HardwareTypes.Chipset.UNKNOWN);
        mergeEnum(other, Motherboard::getFormFactor, Motherboard::setFormFactor, HardwareTypes.MotherboardFormFactor.UNKNOWN);
        mergeEnum(other, Motherboard::getRamType, Motherboard::setRamType, HardwareTypes.RamType.UNKNOWN);
        mergeNumber(other, Motherboard::getRamSlots, Motherboard::setRamSlots);
        mergeNumber(other, Motherboard::getRamCapacity, Motherboard::setRamCapacity);
        mergeNumber(other, Motherboard::getSataSlots, Motherboard::setSataSlots);
        mergeSet(other, Motherboard::getM2Slots);
        mergeSet(other, Motherboard::getPcieSlots);
        mergeSet(other, Motherboard::getUsbPort);
        mergeNumber(other, Motherboard::getUsb3Headers, Motherboard::setUsb3Headers);
    }

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
    public void checkIfLegal() {

    }

    @Override
    public String toString() {
        return "Motherboard{" +
                "usbPort=" + usbPort +
                ", socket=" + socket +
                ", chipset=" + chipset +
                ", formFactor=" + formFactor +
                ", ramType=" + ramType +
                ", ramSlots=" + ramSlots +
                ", ramCapacity=" + ramCapacity +
                ", sataSlots=" + sataSlots +
                ", m2Slots=" + m2Slots +
                ", pcieSlots=" + pcieSlots +
                ", usb3Headers=" + usb3Headers +
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                ", EAN='" + EAN + '\'' +
                ", MPN='" + MPN + '\'' +
                ", UPC='" + UPC + '\'' +
                ", launchDate=" + launchDate +
                '}';
    }
}