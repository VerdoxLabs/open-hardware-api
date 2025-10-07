package de.verdox.openhardwareapi.model;


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
public class Display extends HardwareSpec {
    @PositiveOrZero
    private Integer refreshRate = 0;

    @Enumerated(EnumType.STRING)
    private HardwareTypes.DisplayPanel displayPanel = HardwareTypes.DisplayPanel.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @ElementCollection(fetch = FetchType.EAGER)
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
    private Integer responseTimeMS = 0;

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
}