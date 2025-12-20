package de.verdox.hwapi.productid;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "product_identifier",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"type", "identifier"})
        },
        indexes = {
                @Index(columnList = "type"),
                @Index(columnList = "identifier"),
                @Index(columnList = "type, identifier")
        }
)
@Data
public class ProductIdentifier {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(nullable = false, length = 50)
    private IdentifierType type;

    @Column(nullable = false, length = 512)
    private String identifier;

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
}