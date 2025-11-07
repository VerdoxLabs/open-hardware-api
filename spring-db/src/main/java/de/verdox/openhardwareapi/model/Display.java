package de.verdox.openhardwareapi.model;


import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@DiscriminatorValue("Display")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@NamedEntityGraph(
        name = "Display.All",
        includeAllAttributes = true
)
public class Display extends HardwareSpec<Display> {

    @Override
    public void merge(Display other) {
        super.merge(other);
        mergeNumber(other, Display::getRefreshRate, Display::setRefreshRate);
        mergeEnum(other, Display::getDisplayPanel, Display::setDisplayPanel, HardwareTypes.DisplayPanel.UNKNOWN);
        mergeSet(other, Display::getDisplaySyncs);
        mergeNumber(other, Display::getHdmiPorts, Display::setHdmiPorts);
        mergeNumber(other, Display::getDisplayPorts, Display::setDisplayPorts);
        mergeNumber(other, Display::getDviPorts, Display::setDviPorts);
        mergeNumber(other, Display::getVgaPorts, Display::setVgaPorts);
        mergeNumber(other, Display::getResponseTimeMS, Display::setResponseTimeMS);
        mergeNumber(other, Display::getInchSize, Display::setInchSize);
        mergeNumber(other, Display::getResWidth, Display::setResWidth);
        mergeNumber(other, Display::getResHeight, Display::setResHeight);
        mergeBool(other, Display::getIntegratedSpeakers, Display::setIntegratedSpeakers);
        mergeBool(other, Display::getCurved, Display::setCurved);
        mergeBool(other, Display::getAdjustableSize, Display::setAdjustableSize);
    }

    @PositiveOrZero
    private Integer refreshRate = 0;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.DisplayPanel displayPanel = HardwareTypes.DisplayPanel.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "display_sync", joinColumns = @JoinColumn(name = "spec_id"))
    private Set<HardwareTypes.DisplaySync> displaySyncs = new HashSet<>();

    @PositiveOrZero
    private Integer hdmiPorts = 0;

    @PositiveOrZero
    private Integer displayPorts = 0;

    @PositiveOrZero
    private Integer dviPorts = 0;

    @PositiveOrZero
    private Integer vgaPorts = 0;

    @PositiveOrZero
    private Double responseTimeMS = 0D;

    @PositiveOrZero
    private Double inchSize = 0d;

    @PositiveOrZero
    private Integer resWidth = 0;

    @PositiveOrZero
    private Integer resHeight = 0;

    private Boolean integratedSpeakers = false;

    private Boolean curved = false;

    private Boolean adjustableSize = false;

    @Override
    public void checkIfLegal() {

    }

    @Override
    public String toString() {
        return "Display{" +
                "launchDate=" + launchDate +
                ", refreshRate=" + refreshRate +
                ", displayPanel=" + displayPanel +
                ", displaySyncs=" + displaySyncs +
                ", hdmiPorts=" + hdmiPorts +
                ", displayPorts=" + displayPorts +
                ", dviPorts=" + dviPorts +
                ", vgaPorts=" + vgaPorts +
                ", responseTimeMS=" + responseTimeMS +
                ", inchSize=" + inchSize +
                ", resWidth=" + resWidth +
                ", resHeight=" + resHeight +
                ", integratedSpeakers=" + integratedSpeakers +
                ", curved=" + curved +
                ", adjustableSize=" + adjustableSize +
                ", manufacturer='" + manufacturer + '\'' +
                ", model='" + model + '\'' +
                ", EAN='" + EANs + '\'' +
                ", MPN='" + MPN + '\'' +
                '}';
    }
}