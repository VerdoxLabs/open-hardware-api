package de.verdox.openhardwareapi.io.pc_combo_scraper;

import de.verdox.openhardwareapi.component.service.HardwareSpecService;
import de.verdox.openhardwareapi.model.HardwareTypes;
import de.verdox.openhardwareapi.model.Motherboard;
import de.verdox.openhardwareapi.model.values.M2Slot;
import de.verdox.openhardwareapi.model.values.PcieSlot;
import de.verdox.openhardwareapi.model.values.USBPort;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.verdox.openhardwareapi.io.api.ComponentWebScraper.*;


public class MotherboardKomboScraper extends AbstractPCKomboScraper<Motherboard> {
    public MotherboardKomboScraper(HardwareSpecService service) {
        super("Motherboard-Import", service, "https://www.pc-kombo.com/us/components/motherboards", Motherboard::new);
    }


    @Override
    protected void parseDetails(PcKomboListItem base, Map<String, List<String>> specs, List<SpecEntry> specsList, Motherboard target) {
        target.setSocket(extractFirstEnum(HardwareTypes.CpuSocket.class, "Socket", specs, (s, e) -> e.name().contains(s)));
        target.setChipset(extractFirstEnum(HardwareTypes.Chipset.class, "Chipset", specs, (s, e) -> e.name().contains(s)));
        target.setFormFactor(extractFirstEnum(HardwareTypes.MotherboardFormFactor.class, "Motherboard", specs, (s, motherboardFormFactor) -> motherboardFormFactor.getName().equalsIgnoreCase(s)));
        target.setRamType(extractFirstEnum(HardwareTypes.RamType.class, "Memory Type", specs, (s, e) -> e.name().contains(s)));
        target.setRamSlots((int) parseFirstInt("Ramslots", specs));
        target.setRamCapacity((int) parseFirstInt("Memory Capacity", specs));
        target.setSataSlots((int) parseFirstInt("SATA", specs));

        Set<M2Slot> m2Slots = new HashSet<>();
        Set<PcieSlot> pcieSlots = new HashSet<>();
        Set<USBPort> usbPort = new HashSet<>();

        int m2Gen3Slots = Math.toIntExact(parseFirstInt("M.2 (PCI-E 3.0)", specs));
        int m2Gen4Slots = Math.toIntExact(parseFirstInt("M.2 (PCI-E 4.0)", specs));

        if (m2Gen3Slots > 0) {
            m2Slots.add(new M2Slot(HardwareTypes.PcieVersion.GEN3, HardwareTypes.StorageInterface.NVME, m2Gen3Slots));
        }

        if (m2Gen4Slots > 0) {
            m2Slots.add(new M2Slot(HardwareTypes.PcieVersion.GEN4, HardwareTypes.StorageInterface.NVME, m2Gen3Slots));
        }

        int pcieGen3X1 = Math.toIntExact(parseFirstInt("PCI-E 3.0 x1", specs));
        int pcieGen3X4 = Math.toIntExact(parseFirstInt("PCI-E 3.0 x4", specs));
        int pcieGen3X8 = Math.toIntExact(parseFirstInt("PCI-E 3.0 x8", specs));
        int pcieGen3X16 = Math.toIntExact(parseFirstInt("PCI-E 3.0 x16", specs));

        int pcieGen4X1 = Math.toIntExact(parseFirstInt("PCI-E 4.0 x1", specs));
        int pcieGen4X4 = Math.toIntExact(parseFirstInt("PCI-E 4.0 x4", specs));
        int pcieGen4X8 = Math.toIntExact(parseFirstInt("PCI-E 4.0 x8", specs));
        int pcieGen4X16 = Math.toIntExact(parseFirstInt("PCI-E 4.0 x16", specs));

        if (pcieGen3X1 > 0) {
            pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN3, 1, pcieGen3X1));
        }

        if (pcieGen3X4 > 0) {
            pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN3, 4, pcieGen3X4));
        }

        if (pcieGen3X8 > 0) {
            pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN3, 8, pcieGen3X8));
        }

        if (pcieGen3X16 > 0) {
            pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN3, 16, pcieGen3X16));
        }

        if (pcieGen4X1 > 0) {
            pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN4, 1, pcieGen3X1));
        }

        if (pcieGen4X4 > 0) {
            pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN4, 4, pcieGen3X4));
        }

        if (pcieGen4X8 > 0) {
            pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN4, 8, pcieGen3X8));
        }

        if (pcieGen4X16 > 0) {
            pcieSlots.add(new PcieSlot(HardwareTypes.PcieVersion.GEN4, 16, pcieGen3X16));
        }

        int usb3Slots = Math.toIntExact(parseFirstInt("USB 3 Slots", specs));

        if (usb3Slots > 0) {
            usbPort.add(new USBPort(HardwareTypes.UsbConnectorType.USB_A, HardwareTypes.UsbVersion.USB3_2_GEN1, usb3Slots));
        }


        int usbC = Math.toIntExact(parseFirstInt("USB 3 Type-C", specs));

        if (usbC > 0) {
            usbPort.add(new USBPort(HardwareTypes.UsbConnectorType.USB_C, HardwareTypes.UsbVersion.USB3_2_GEN1, usb3Slots));
        }

        int usb3Headers = Math.toIntExact(parseFirstInt("USB 3 Headers", specs));

        target.setM2Slots(m2Slots);
        target.setPcieSlots(pcieSlots);
        target.setUsbPort(usbPort);
        target.setUsb3Headers(usb3Headers);
    }
}
