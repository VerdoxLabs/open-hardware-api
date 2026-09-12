package de.verdox.hwapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.verdox.hwapi.model.values.FanSpec;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gehäuselüfter als eigenständige Komponente (eigene Preiskategorie im Handel).
 */
@Entity
@Table(name = "fan")
@DiscriminatorValue("FAN")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@NamedEntityGraph(
        name = "Fan.All",
        includeAllAttributes = true
)
public class Fan extends HardwareSpec<Fan> {

    @Override
    public void merge(Fan other) {
        super.merge(other);
        mergeEnum(other, Fan::getConnectorType, Fan::setConnectorType, HardwareTypes.FanConnectorType.UNKNOWN);
        mergeNumber(other, Fan::getAirflowCfm, Fan::setAirflowCfm);
        mergeNumber(other, Fan::getStaticPressureMmH2o, Fan::setStaticPressureMmH2o);
        mergeNumber(other, Fan::getNoiseLevelDB, Fan::setNoiseLevelDB);
        merge(other, Fan::getFanSpec, Fan::setFanSpec,
                spec -> spec == null || spec.getDiameterMm() == null || spec.getDiameterMm() == 0);
        mergeBool(other, Fan::getHasRgb, Fan::setHasRgb);
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private HardwareTypes.FanConnectorType connectorType = HardwareTypes.FanConnectorType.UNKNOWN;

    @Embedded
    private FanSpec fanSpec = new FanSpec();

    @PositiveOrZero
    @Column(name = "airflow_cfm")
    private double airflowCfm = 0;

    @PositiveOrZero
    @Column(name = "static_pressure_mm_h2o")
    private double staticPressureMmH2o = 0;

    @PositiveOrZero
    @Column(name = "noise_level_db")
    private Double noiseLevelDB = 0D;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "fan_mount_positions", joinColumns = @JoinColumn(name = "spec_id"))
    @Column(name = "position")
    private Set<String> mountPositions = new LinkedHashSet<>();

    @Column(name = "has_rgb")
    private Boolean hasRgb = false;

    @Override
    public void checkIfLegal() {

    }

    @Override
    public String toString() {
        return "Fan{" +
                "model='" + model + '\'' +
                ", connectorType=" + connectorType +
                ", fanSpec=" + fanSpec +
                ", airflowCfm=" + airflowCfm +
                ", staticPressureMmH2o=" + staticPressureMmH2o +
                ", noiseLevelDB=" + noiseLevelDB +
                ", hasRgb=" + hasRgb +
                ", manufacturer='" + manufacturer + '\'' +
                ", EAN='" + EANs + '\'' +
                ", MPN='" + MPNs + '\'' +
                ", launchDate=" + launchDate +
                '}';
    }
}
