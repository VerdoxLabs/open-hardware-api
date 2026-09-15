package de.verdox.hwapi.pricing.model;

/** Confidence of the product identity of a sold eBay listing. */
public enum EbayMatchStatus {
    /** Structured eBay item specifics contain an exact known EAN/GTIN or MPN. */
    VERIFIED,
    /** Title/model is plausible, but no exact structured identifier was available. */
    HIGH_CONFIDENCE,
    /** Conflicting identifier, bundle/system listing, or insufficient evidence. */
    REJECTED
}
