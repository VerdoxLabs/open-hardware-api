package de.verdox.hwapi.client;

import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.model.values.ItemCondition;
import de.verdox.hwapi.priceapi.model.ListingEnums;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PriceSeriesDTO(ItemCondition condition, boolean areCompletedListings, Map<Currency, List<PricePointDTO>> prices) {

    public record PricePointDTO(ListingEnums.Country listingRegion, String marketName, String marketURL, String marketLogo, String marketPlaceDomain, String marketPlaceItemId, Instant date, BigDecimal price, Currency currency) {
    }
}
