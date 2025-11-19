package de.verdox.hwapi.io.api;

import de.verdox.hwapi.model.values.Currency;

import java.math.BigDecimal;

public record Price(BigDecimal value, Currency currency) {
}
