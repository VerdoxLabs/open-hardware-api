package de.verdox.hwapi.pricing.sources.ebay;

import java.util.Map;

/** Structured data extracted from an individual eBay listing page. */
public record EbayListingDetails(String title, Map<String, String> itemSpecifics) {
}
