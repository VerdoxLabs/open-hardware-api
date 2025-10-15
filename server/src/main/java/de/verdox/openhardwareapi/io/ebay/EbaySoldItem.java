package de.verdox.openhardwareapi.io.ebay;

import de.verdox.openhardwareapi.io.api.Price;

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
