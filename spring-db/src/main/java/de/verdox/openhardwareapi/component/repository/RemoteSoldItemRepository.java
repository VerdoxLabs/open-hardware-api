package de.verdox.openhardwareapi.component.repository;

import de.verdox.openhardwareapi.model.RemoteSoldItem;
import de.verdox.openhardwareapi.model.values.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RemoteSoldItemRepository extends JpaRepository<RemoteSoldItem, Long> {
    interface PricePoint {
        LocalDate getSellPrice();   // = r.sellDate
        BigDecimal getPrice();      // = r.price
        String getCurrency();       // = r.currency
    }

    // Alle Punkte (für Graphen)
    @Query("""
            select r.sellDate as sellPrice, r.sellPrice as price, r.currency as currency
            from RemoteSoldItem r
            where r.EAN = :ean
            order by r.sellDate asc
            """)
    List<PricePoint> findPriceSeriesByEan(@Param("ean") String ean);

    // Punkte seit bestimmtem Datum
    @Query("""
            select r.sellDate as sellPrice, r.sellPrice as price, r.currency as currency
            from RemoteSoldItem r
            where r.EAN = :ean and r.sellDate >= :from
            order by r.sellDate asc
            """)
    List<PricePoint> findPriceSeriesByEanSince(@Param("ean") String ean,
                                               @Param("from") LocalDate from);

    // NEU: AVG für eine spezifische Währung
    @Query("""
            select avg(r.sellPrice)
            from RemoteSoldItem r
            where r.EAN = :ean and r.sellDate >= :from and r.currency = :currency
            """)
    Optional<Double> findAveragePriceSinceByCurrency(@Param("ean") String ean,
                                                     @Param("from") LocalDate from,
                                                     @Param("currency") Currency currency);
}