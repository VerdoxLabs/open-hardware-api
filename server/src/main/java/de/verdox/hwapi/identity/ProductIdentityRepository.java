package de.verdox.hwapi.identity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductIdentityRepository extends JpaRepository<ProductIdentity, Long> {
}