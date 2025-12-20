package de.verdox.hwapi.priceapi.component.service.awin;

import de.verdox.hwapi.priceapi.model.ListingEnums;

public record AwinFeed(String advertiser, String feedUrlDownloadLink, ListingEnums.Country primaryRegion, String language) {
    public AwinFeed(String advertiser, String feedUrlDownloadLink, String primaryRegion, String language) {
        this(advertiser, feedUrlDownloadLink, ListingEnums.Country.fromIso(primaryRegion).orElseThrow(() -> new IllegalArgumentException("no primary region found for code "+primaryRegion)), language);
    }
}
