package de.verdox.hwapi.productid;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductIdentityRepository extends JpaRepository<ProductIdentity, Long> {
}