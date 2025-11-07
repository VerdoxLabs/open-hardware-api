package de.verdox.openhardwareapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.verdox.openhardwareapi.component.repository.HardwareSpecificRepo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.NaturalId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
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
    @Id
    @JsonIgnore
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @NotBlank
    protected String manufacturer;


    @NotBlank
    protected String model;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "hardware_spec_eans",
            joinColumns = @JoinColumn(name = "spec_id")
    )
    @Column(name = "ean", length = 14, nullable = false)
    protected Set<String> EANs = new HashSet<>();

    @NaturalId(mutable = true)
    @Column(name = "mpn", unique = true)
    protected String MPN;

    protected LocalDate launchDate;

    public void addEAN(String ean) {
        if (ean == null || ean.isEmpty()) {
            return;
        }
        this.EANs.add(normalizeEan(ean));
    }

    // Mini-Helper
    public static String normalizeEan(String raw) {
        if (raw == null) return null;
        String t = raw.trim().replaceAll("[^0-9]", "");
        if (t.isEmpty()) return null;
        if (t.length() == 12) t = "0" + t; // UPC-A -> EAN-13
        return (t.length() == 13 || t.length() == 14) ? t : null;
    }

    public abstract void checkIfLegal();

    @PrePersist
    public void sanitizeNumbers() {
        HardwareSpecificRepo.normalizeEAN_MPN(this.MPN);
        setEANs(this.EANs);
    }

    public void tryMerge(HardwareSpec<?> incoming) {
        if (incoming.getClass().equals(this.getClass())) {
            merge((SELF) incoming);
        }
    }

    public void merge(SELF other) {
        mergeSet(other, HardwareSpec::getEANs);
        mergeString(other, HardwareSpec::getMPN, HardwareSpec::setMPN);
        mergeString(other, HardwareSpec::getModel, HardwareSpec::setModel);
        mergeString(other, HardwareSpec::getManufacturer, HardwareSpec::setManufacturer);
        merge(other, HardwareSpec::getLaunchDate, HardwareSpec::setLaunchDate, Objects::isNull);
    }

    public final void mergeBool(SELF other, Function<SELF, Boolean> getter, BiConsumer<SELF, Boolean> setter) {
        merge(other, getter, setter, bool -> !bool);
    }

    public final <ENUM extends Enum<ENUM>> void mergeEnum(SELF other, Function<SELF, ENUM> getter, BiConsumer<SELF, ENUM> setter, ENUM standardValue) {
        merge(other, getter, setter, anEnum -> anEnum.equals(standardValue));
    }

    public final void mergeString(SELF other, Function<SELF, String> getter, BiConsumer<SELF, String> setter) {
        merge(other, getter, setter, s -> s == null || s.isBlank());
    }

    public final <NUMBER extends Number> void mergeNumber(SELF other, Function<SELF, NUMBER> getter, BiConsumer<SELF, NUMBER> setter) {
        merge(other, getter, setter, number -> number == null || number.doubleValue() == 0);
    }

    public final <ENUM extends Enum<ENUM>> void mergeEnumCollection(SELF other, Function<SELF, Collection<ENUM>> getter) {
        getter.apply(self()).addAll(getter.apply(other));
    }

    public final <INPUT> void mergeSet(SELF other, Function<SELF, Set<INPUT>> getter) {
        getter.apply(self()).addAll(getter.apply(other));
    }

    public final <INPUT> void merge(SELF other, Function<SELF, INPUT> getter, BiConsumer<SELF, INPUT> setter, Predicate<INPUT> isStandardValue) {
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

    public final SELF self() {
        return (SELF) this;
    }
}