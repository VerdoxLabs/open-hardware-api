package de.verdox.hwapi.hardwareapi.scraping.api;

import de.verdox.hwapi.model.values.Currency;

import java.math.BigDecimal;

public record Price(BigDecimal value, Currency currency) {
}
