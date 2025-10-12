package de.verdox.openhardwareapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.verdox.openhardwareapi.component.repository.HardwareSpecificRepo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Gemeinsame Felder für alle Hardware-Spezifikationen.
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "spec_type")
@Getter
@Setter
public abstract class HardwareSpec<SELF extends HardwareSpec<SELF>> {
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

    @PrePersist
    public void sanitizeNumbers() {
        HardwareSpecificRepo.normalizeEANMPNUPC(this.EAN);
        HardwareSpecificRepo.normalizeEANMPNUPC(this.UPC);
        HardwareSpecificRepo.normalizeEANMPNUPC(this.MPN);
    }

    public void merge(SELF other) {
        mergeString(other, HardwareSpec::getEAN, HardwareSpec::setEAN);
        mergeString(other, HardwareSpec::getMPN, HardwareSpec::setMPN);
        mergeString(other, HardwareSpec::getUPC, HardwareSpec::setUPC);
        mergeString(other, HardwareSpec::getModel, HardwareSpec::setModel);
        mergeString(other, HardwareSpec::getManufacturer, HardwareSpec::setManufacturer);
        merge(other, HardwareSpec::getLaunchDate, HardwareSpec::setLaunchDate, Objects::isNull);
    }

    protected final void mergeBool(SELF other, Function<SELF, Boolean> getter, BiConsumer<SELF, Boolean> setter) {
        merge(other, getter, setter, bool -> !bool);
    }

    protected final <ENUM extends Enum<ENUM>> void mergeEnum(SELF other, Function<SELF, ENUM> getter, BiConsumer<SELF, ENUM> setter, ENUM standardValue) {
        merge(other, getter, setter, anEnum -> anEnum.equals(standardValue));
    }

    protected final void mergeString(SELF other, Function<SELF, String> getter, BiConsumer<SELF, String> setter) {
        merge(other, getter, setter, s -> s == null || s.isBlank());
    }

    protected final <NUMBER extends Number> void mergeNumber(SELF other, Function<SELF, NUMBER> getter, BiConsumer<SELF, NUMBER> setter) {
        merge(other, getter, setter, number -> number == null || number.doubleValue() == 0);
    }

    protected final <ENUM extends Enum<ENUM>> void mergeEnumCollection(SELF other, Function<SELF, Collection<ENUM>> getter) {
        getter.apply(self()).addAll(getter.apply(other));
    }

    protected final <INPUT> void mergeSet(SELF other, Function<SELF, Set<INPUT>> getter) {
        getter.apply(self()).addAll(getter.apply(other));
    }

    protected final <INPUT> void merge(SELF other, Function<SELF, INPUT> getter, BiConsumer<SELF, INPUT> setter, Predicate<INPUT> isStandardValue) {
        INPUT newValue = getter.apply(other);
        if (isStandardValue.test(newValue)) {
            return;
        }
        INPUT currentValue = getter.apply((self()));
        if (!isStandardValue.test(currentValue)) {
            return;
        }
        setter.accept(self(), newValue);
    }

    protected final SELF self() {
        return (SELF) this;
    }
}