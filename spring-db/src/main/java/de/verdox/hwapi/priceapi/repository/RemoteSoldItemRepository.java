package de.verdox.hwapi.priceapi.repository;

import de.verdox.hwapi.priceapi.model.RemoteSoldItem;
import de.verdox.hwapi.model.values.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface RemoteSoldItemRepository extends JpaRepository<RemoteSoldItem, UUID> {

    interface PricePoint {
        LocalDate getSellDate();   // = r.sellDate
        BigDecimal getPrice();     // = r.price
        Currency getCurrency();      // = r.currency
    }

    interface EANPricePoint {
        String getEan();
        LocalDate getSellDate();   // = r.sellDate
        BigDecimal getPrice();     // = r.price
        Currency getCurrency();      // = r.currency
    }

    // --- Preis-Zeitreihen ----------------------------------------------------

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

    // --- Durchschnittspreis --------------------------------------------------

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
