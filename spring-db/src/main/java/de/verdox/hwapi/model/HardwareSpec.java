package de.verdox.hwapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.verdox.hwapi.component.repository.HardwareSpecificRepo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.NaturalId;

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
    @Id
    @JsonIgnore
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @NotBlank
    protected String manufacturer;


    @NotBlank
    protected String model;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "hardware_spec_eans",
            joinColumns = @JoinColumn(name = "spec_id"),
            indexes = {
                    @Index(name = "idx_hardware_spec_eans_ean", columnList = "ean")
            }
    )
    @Column(name = "ean", length = 14, nullable = false)
    protected Set<String> EANs = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "hardware_spec_mpns",
            joinColumns = @JoinColumn(name = "spec_id"),
            indexes = {
                    @Index(name = "idx_hardware_spec_mpns_mpn", columnList = "mpn")
            }
    )
    @Column(name = "mpn")
    protected Set<String> MPNs = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "hardware_spec_picture_urls",
            joinColumns = @JoinColumn(name = "spec_id")
    )
    @Column(name = "url", nullable = false, length = 1024)
    protected List<String> pictureUrls = new ArrayList<>();

    protected LocalDate launchDate;

    public void addEAN(String ean) {
        if (ean == null || ean.isEmpty()) {
            return;
        }
        this.EANs.add(normalizeEan(ean));
    }

    public void addMPN(String mpn) {
        if (mpn == null || mpn.isEmpty()) {
            return;
        }
        this.MPNs.add(mpn);
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
        setEANs(this.EANs);
    }

    @JsonIgnore
    @Transient
    public List<String> getMpnsSorted() {
        return getMPNs().stream().sorted().toList();
    }

    @JsonIgnore
    @Transient
    public List<String> getEansSorted() {
        return getEANs().stream().sorted().toList();
    }

    public String displayMPNs() {
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : getMpnsSorted()) {
            if (!stringBuilder.isEmpty()) {
                stringBuilder.append(", ");
            }
            stringBuilder.append(s);
        }
        return stringBuilder.toString();
    }

    public String displayEANs() {
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : getEansSorted()) {
            if (!stringBuilder.isEmpty()) {
                stringBuilder.append(", ");
            }
            stringBuilder.append(s);
        }
        return stringBuilder.toString();
    }

    public void tryMerge(HardwareSpec<?> incoming) {
        if (incoming.getClass().equals(this.getClass())) {
            merge((SELF) incoming);
        }
    }

    public void merge(SELF other) {
        mergeSet(other, HardwareSpec::getEANs);
        mergeSet(other, HardwareSpec::getMPNs);
        getPictureUrls().clear();
        merge(other, HardwareSpec::getPictureUrls, (self, strings) -> {
            for (String string : strings) {
                if(!self.getPictureUrls().contains(string)) {
                    self.getPictureUrls().add(string);
                }
            }
        }, List::isEmpty);
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