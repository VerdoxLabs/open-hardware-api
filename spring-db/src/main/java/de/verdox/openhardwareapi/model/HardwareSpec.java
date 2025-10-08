package de.verdox.openhardwareapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * Gemeinsame Felder für alle Hardware-Spezifikationen.
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "spec_type")
@Getter
@Setter
public abstract class HardwareSpec {
    @Transient
    private UUID uuid = deriveUUID();

    @Id
    @JsonIgnore
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @NotBlank
    protected String manufacturer;


    @NotBlank
    protected String model;

    protected String EAN;

    protected String MPN;

    protected String UPC;

    protected LocalDate launchDate;

    public void setEAN(String ean) {
        if (ean == null || ean.isEmpty()) {
            this.EAN = null;
            return;
        }

        if (ean.charAt(0) == '0') {
            setMPN(ean.substring(1));
        } else {
            setMPN(ean);
        }

        this.EAN = ean;
    }

    /**
     * Freitext-Tags (z.B. "gaming", "workstation")
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hardware_spec_tags", joinColumns = @JoinColumn(name = "spec_id"))
    @Column(name = "tag")
    protected Set<String> tags = new LinkedHashSet<>();


    /**
     * Beliebige zusätzliche Schlüssel/Wert-Eigenschaften.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hardware_spec_attributes", joinColumns = @JoinColumn(name = "spec_id"))
    @MapKeyColumn(name = "attr_key")
    @Column(name = "attr_value", length = 2000)
    protected Map<String, String> attributes = new LinkedHashMap<>();

    public abstract void checkIfLegal();

    public UUID deriveUUID() {
        return UUID.nameUUIDFromBytes((EAN + "_" + MPN + "_" + UPC).getBytes(StandardCharsets.UTF_8));
    }
}