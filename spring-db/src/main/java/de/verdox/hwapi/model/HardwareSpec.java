package de.verdox.hwapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.text.Normalizer;
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
    @EqualsAndHashCode.Exclude
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @NotBlank
    protected String manufacturer;


    @NotBlank
    protected String model;

    @Transient
    public String displayName() {
        return  manufacturer + " " + model;
    }

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
    protected Set<String> pictureUrls = new HashSet<>();

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
        this.MPNs.add(normalizeMpn(mpn));
    }

    // Mini-Helper
    public static String normalizeEan(String raw) {
        if (raw == null) return null;
        String t = raw.trim().replaceAll("[^0-9]", "");
        if (t.isEmpty()) return null;
        if (t.length() == 12) t = "0" + t; // UPC-A -> EAN-13
        return (t.length() == 13 || t.length() == 14) ? t : null;
    }

    public static String normalizeMpn(String mpn) {
        if (mpn == null) return null;

        return Normalizer.normalize(mpn, Normalizer.Form.NFKC)
                .trim()
                .replace('\u00A0', ' ')
                .toUpperCase(Locale.ROOT);
    }


    public abstract void checkIfLegal();


    @PrePersist
    public void sanitizeNumbers() {
        var modelBefore = getModel();
        sanitize();

        var eansCopy = new HashSet<>(this.EANs);
        var mpnsCopy = new HashSet<>(this.MPNs);

        this.EANs = new HashSet<>();
        this.MPNs = new HashSet<>();

        for (String ean : eansCopy) {
            addEAN(ean);
        }

        for (String mpn : mpnsCopy) {
            addMPN(mpn);
        }

        setModel(
                getModel()
                        .replace(getManufacturer(), "")
                        .replace(getClass().getSimpleName(), "")
        );

        if(getModel().isBlank()) {
            setModel(modelBefore);
        }
        getPictureUrls().remove("https://images2.productserve.com/noimage.gif");
    }

    public void sanitize() {

    }

    public String getDisplayPictureUrl() {
        for (String pictureUrl : getPictureUrls()) {
            if(pictureUrl.contains("noimage")) {
                continue;
            }
            return pictureUrl;
        }
        return "https://images2.productserve.com/noimage.gif";
    }

    @PostLoad
    public void postLoad() {
        getPictureUrls().remove("https://images2.productserve.com/noimage.gif");
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
        for (String s : getMPNs()) {
            if (!stringBuilder.isEmpty()) {
                stringBuilder.append(", ");
            }
            stringBuilder.append(s);
        }
        return stringBuilder.toString();
    }

    public String displayEANs() {
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : getEANs()) {
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
        mergeSet(other, HardwareSpec::getEANs, HardwareSpec::setEANs);
        mergeSet(other, HardwareSpec::getMPNs, HardwareSpec::setMPNs);
        mergeSet(other, HardwareSpec::getPictureUrls, HardwareSpec::setPictureUrls);
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

    public final <INPUT> void mergeSet(SELF other, Function<SELF, Set<INPUT>> getter, BiConsumer<SELF, Set<INPUT>> setter) {
        Set<INPUT> set = new HashSet<>(getter.apply(self()));
        set.addAll(getter.apply(other));
        setter.accept(self(), set);
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