package de.verdox.hwapi.identity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "product_identity")
@Data
public class ProductIdentity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToMany(mappedBy = "identity", cascade = CascadeType.ALL)
    @EqualsAndHashCode.Exclude
    private Set<ProductIdentifier> identifiers = new HashSet<>();
}
