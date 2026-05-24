package de.verdox.hwapi.priceapi.repository;

import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.model.values.ItemCondition;
import de.verdox.hwapi.priceapi.model.RemoteSoldItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public interface RemoteSoldItemRepository extends JpaRepository<RemoteSoldItem, UUID> {

    interface PricePoint {
        LocalDate getSellDate();
        BigDecimal getPrice();
        Currency getCurrency();
    }

    interface EANPricePoint {
        String getEan();
        LocalDate getSellDate();
        BigDecimal getPrice();
        Currency getCurrency();
    }

    /**
     * Interne Query: RemoteSoldItem hat nur "ean" als Identifier-Feld.
     * Daher: ein Set aus allen identifiers (EAN/MPN) und gegen r.ean matchen.
     */
    @Query("""
        SELECT r
        FROM RemoteSoldItem r
        WHERE r.ean IN :identifiers
          AND r.condition IN :conditions
          AND r.sellDate >= :fromDate
        """)
    List<RemoteSoldItem> findPricePointsInternal(
            @Param("identifiers") Set<String> identifiers,
            @Param("conditions") Set<ItemCondition> conditions,
            @Param("fromDate") LocalDate fromDate
    );

    /**
     * Öffentliche Methode: monthSince wie gehabt.
     * Bugfix: mpns wurden zuvor fälschlich gegen r.ean geprüft (und sogar doppelt "r.ean IN ...").
     * Jetzt: merge.
     */
    default List<RemoteSoldItem> findPricePoints(
            Set<String> mpns,
            Set<String> eans,
            Set<ItemCondition> conditions,
            int monthSince
    ) {
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("conditions must not be null or empty");
        }

        Set<String> identifiers = new HashSet<>();
        if (mpns != null) mpns.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).forEach(identifiers::add);
        if (eans != null) eans.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).forEach(identifiers::add);

        if (identifiers.isEmpty()) return List.of();

        LocalDate fromDate = LocalDate.now().minusMonths(monthSince);
        return findPricePointsInternal(identifiers, conditions, fromDate);
    }

    @Query("""
        select r.sellDate as sellDate, r.sellPrice as price, r.currency as currency
        from RemoteSoldItem r
        where r.ean = :ean
        order by r.sellDate asc
        """)
    List<PricePoint> findPriceSeriesByEan(@Param("ean") String ean);

    @Query("""
        select r.sellDate as sellDate, r.sellPrice as price, r.currency as currency
        from RemoteSoldItem r
        where r.ean = :ean and r.sellDate >= :from
        order by r.sellDate asc
        """)
    List<PricePoint> findPriceSeriesByEanSince(@Param("ean") String ean,
                                               @Param("from") LocalDate from);

    @Query("""
        select avg(r.sellPrice)
        from RemoteSoldItem r
        where r.ean = :ean
          and r.sellDate >= :from
          and r.currency = :currency
        """)
    Optional<Double> findAveragePriceSinceByCurrency(@Param("ean") String ean,
                                                     @Param("from") LocalDate from,
                                                     @Param("currency") Currency currency);

    @Query("""
    select percentile_cont(0.5) within group (order by r.sellPrice)
    from RemoteSoldItem r
    where r.ean in :eans
      and r.sellDate >= :since
      and r.currency = :currency
    """)
    BigDecimal medianPriceForEansSince(
            @Param("eans") Collection<String> eans,
            @Param("since") LocalDate since,
            @Param("currency") Currency currency
    );

    @Query("""
        select r.ean as ean, r.sellDate as sellDate, r.sellPrice as price, r.currency as currency
        from RemoteSoldItem r
        where r.ean in :eans
          and r.currency = :currency
          and r.sellDate >= :since
        """)
    List<EANPricePoint> findUnitPricesSince(
            @Param("eans") Collection<String> eans,
            @Param("since") LocalDate since,
            @Param("currency") Currency currency
    );
}
