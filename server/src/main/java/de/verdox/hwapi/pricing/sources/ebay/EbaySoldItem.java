package de.verdox.hwapi.pricing.sources.ebay;

import de.verdox.hwapi.catalog.ingestion.api.Price;

import java.time.LocalDate;
import java.util.List;

public record EbaySoldItem(
        String itemId,
        String title,
        List<String> condition,
        Price price,
        Integer bids,
        LocalDate soldDate
) {
}
