package de.verdox.hwapi.priceapi.io.ebay;

import de.verdox.hwapi.io.api.Price;
import de.verdox.hwapi.model.values.ItemCondition;

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
