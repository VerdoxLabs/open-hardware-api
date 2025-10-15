package de.verdox.openhardwareapi.io.api;

import de.verdox.openhardwareapi.model.values.Currency;

import java.math.BigDecimal;

public record Price(BigDecimal value, Currency currency) {
}
