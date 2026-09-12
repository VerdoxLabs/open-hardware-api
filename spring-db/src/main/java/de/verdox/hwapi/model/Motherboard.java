package de.verdox.hwapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.verdox.hwapi.model.values.M2Slot;
import de.verdox.hwapi.model.values.PcieSlot;
import de.verdox.hwapi.model.values.USBPort;
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
@NamedEntityGraph(
        name = "Motherboard.All",
        includeAllAttributes = true
)
public class Motherboard extends HardwareSpec<Motherboard> {

    @Override
    public void sanitize() {
        setModel(getModel()
                .replace(chipset.name(), "")
                .replace(socket.getName(), "")

                .replace("Mini ITX", "")
                .replace("Micro ATX", "")
                .replace("ATX", "")
        );
    }

    @Override
    public String displayName() {
        return super.displayName()+" " + chipset.name()+" "+socket.getName()+" "+getFormFactor().name();
    }

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
        mergeSet(other, Motherboard::getM2Slots,  Motherboard::setM2Slots);
        mergeSet(other, Motherboard::getPcieSlots,  Motherboard::setPcieSlots);
        mergeSet(other, Motherboard::getUsbPort,  Motherboard::setUsbPort);
        mergeNumber(other, Motherboard::getUsb3Headers, Motherboard::setUsb3Headers);
        mergeEnum(other, Motherboard::getWlanStandard, Motherboard::setWlanStandard, HardwareTypes.WifiStandard.UNKNOWN);
        mergeEnum(other, Motherboard::getEthernetSpeed, Motherboard::setEthernetSpeed, HardwareTypes.EthernetSpeed.UNKNOWN);
        mergeSet(other, Motherboard::getDisplayOutputs, Motherboard::setDisplayOutputs);
        mergeBool(other, Motherboard::getHasBluetooth, Motherboard::setHasBluetooth);
        mergeBool(other, Motherboard::getHasFrontUsbC, Motherboard::setHasFrontUsbC);
        mergeSet(other, Motherboard::getColors, Motherboard::setColors);
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.CpuSocket socket = HardwareTypes.CpuSocket.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.Chipset chipset = HardwareTypes.Chipset.UNKNOWN;


    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(nullable = false)
    private HardwareTypes.MotherboardFormFactor formFactor = HardwareTypes.MotherboardFormFactor.UNKNOWN;


    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(nullable = false)
    private HardwareTypes.RamType ramType = HardwareTypes.RamType.UNKNOWN;


    @PositiveOrZero
    private Integer ramSlots = 0;

    @PositiveOrZero
    private Integer ramCapacity = 0;

    @PositiveOrZero
    private Integer sataSlots = 0;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "M2SLOTS", joinColumns = @JoinColumn(name = "spec_id"), uniqueConstraints = @UniqueConstraint(columnNames = {"SPEC_ID", "PCIE_VERSION", "SUPPORTED_INTERFACE"}))
    private Set<M2Slot> m2Slots = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "pcieSlots", joinColumns = @JoinColumn(name = "spec_id"), uniqueConstraints = @UniqueConstraint(columnNames = {"SPEC_ID", "VERSION", "LANES"}))
    private Set<PcieSlot> pcieSlots = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "usbPort", joinColumns = @JoinColumn(name = "spec_id"), uniqueConstraints = @UniqueConstraint(columnNames = {"SPEC_ID", "TYPE", "VERSION"}))
    private Set<USBPort> usbPort = new HashSet<>();

    @PositiveOrZero
    private int usb3Headers = 0;

    @ElementCollection
    @CollectionTable(name="motherboard_color", joinColumns=@JoinColumn(name="motherboard_id"))
    @Column(name="color", nullable=false)
    private Set<String> colors = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "wlan_standard")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.WifiStandard wlanStandard = HardwareTypes.WifiStandard.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "ethernet_speed")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.EthernetSpeed ethernetSpeed = HardwareTypes.EthernetSpeed.UNKNOWN;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "motherboard_display_outputs", joinColumns = @JoinColumn(name = "spec_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "output_type")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Set<HardwareTypes.DisplayOutputType> displayOutputs = new HashSet<>();

    @Column(name = "has_bluetooth")
    private Boolean hasBluetooth = false;

    @Column(name = "has_front_usb_c")
    private Boolean hasFrontUsbC = false;

    public void addOrMerge(USBPort incoming) {
        if (incoming == null) return;

        for (USBPort existing : usbPort) {
            if (existing.getType() == incoming.getType()
                    && existing.getVersion() == incoming.getVersion()) {
                int a = existing.getQuantity() == null ? 0 : existing.getQuantity();
                int b = incoming.getQuantity() == null ? 0 : incoming.getQuantity();
                existing.setQuantity(a + b); //
                return;
            }
        }
        usbPort.add(incoming);
    }

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
                ", EAN='" + EANs + '\'' +
                ", MPN='" + MPNs + '\'' +
                ", launchDate=" + launchDate +
                '}';
    }

    public boolean isCompatibleWith(CPU cpu) {
        return cpu.getSocket().equals(this.socket);
    }

    public boolean isCompatibleWith(RAM ram) {
        return ram.getType().equals(this.ramType);
    }

    public boolean isCompatibleWith(PCCase pcCase) {
        return pcCase.getMotherboardSupport().contains(getFormFactor());
    }

    public boolean isCompatibleWith(GPU gpu) {
        return this.getPcieSlots().stream().anyMatch(pcieSlot -> pcieSlot.getLanes() == 16 && pcieSlot.getVersion().equals(gpu.getPcieVersion()));
    }
}