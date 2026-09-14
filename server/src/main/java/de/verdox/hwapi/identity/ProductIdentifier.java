package de.verdox.hwapi.identity;

import com.fasterxml.jackson.annotation.JsonFormat;
import de.verdox.hwapi.identity.IdentifierNormalizer;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "product_identifier",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"type", "normalized_value"})
        },
        indexes = {
                @Index(columnList = "type"),
                @Index(columnList = "normalized_value"),
                @Index(columnList = "type, normalized_value")
        }
)
@Getter
@Setter
@ToString(exclude = "identity")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProductIdentifier {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(nullable = false, length = 50)
    private IdentifierType type;

    @Column(name = "identifier", nullable = false, length = 512)
    private String value;

    @Column(name = "normalized_value", nullable = false, length = 512)
    private String normalizedValue;

    @ElementCollection
    @CollectionTable(name = "product_identifier_sources", joinColumns = @JoinColumn(name = "identifier_id"))
    @Column(name = "source")
    private Set<String> sources = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "identity_id")
    private ProductIdentity identity;

    public enum IdentifierType {
        ASIN,
        EAN,
        UPC,
        GTIN,
        MPN,
        TITLE,
        TITLE_NORMALIZED
    }

    /** Backwards-compatible alias; new code should use value/normalizedValue. */
    @Deprecated
    public String getIdentifier() {
        return normalizedValue != null ? normalizedValue : value;
    }

    /** Backwards-compatible alias for existing callers. */
    @Deprecated
    public void setIdentifier(String identifier) {
        this.value = identifier;
        this.normalizedValue = normalize(identifier);
    }

    public void setType(IdentifierType type) {
        this.type = type;
        if (value != null) this.normalizedValue = normalize(value);
    }

    public void setValue(String value) {
        this.value = value;
        this.normalizedValue = normalize(value);
    }

    private String normalize(String raw) {
        if (raw == null || type == null) return raw;
        return switch (type) {
            case EAN, UPC, GTIN -> IdentifierNormalizer.gtin(raw);
            case MPN -> IdentifierNormalizer.mpn(raw);
            case ASIN -> IdentifierNormalizer.asin(raw);
            default -> raw.trim();
        };
    }
}
