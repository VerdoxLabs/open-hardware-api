package de.verdox.hwapi.pricing.repository;

import de.verdox.hwapi.pricing.model.C2cMatchReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface C2cMatchReviewRepository extends JpaRepository<C2cMatchReview, Long> {

    List<C2cMatchReview> findByStatus(C2cMatchReview.Status status);

    boolean existsByMarketPlaceItemIdAndTitleNormalized(String marketPlaceItemId, String titleNormalized);

    /**
     * Offene (PENDING) Reviews, nach Konfidenz aufsteigend und Alter absteigend –
     * also die unsichersten / am längsten wartenden zuerst. Basis für die
     * Discord-Review-Queue und für manuelles Durchgehen.
     */
    @Query("""
            select r from C2cMatchReview r
            where r.status = :status
            order by r.bestScore asc, r.createdAt desc
            """)
    List<C2cMatchReview> findReviewQueue(@Param("status") C2cMatchReview.Status status);
}
