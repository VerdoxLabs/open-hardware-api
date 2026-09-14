package de.verdox.hwapi.catalog.ingestion.api;

import de.verdox.hwapi.catalog.domain.values.Currency;

import java.math.BigDecimal;

public record Price(BigDecimal value, Currency currency) {
}
