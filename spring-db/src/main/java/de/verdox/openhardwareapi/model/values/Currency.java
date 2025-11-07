package de.verdox.openhardwareapi.model.values;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

@Getter

@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum Currency {
    EURO("EUR", "€"),
    US_DOLLAR("USD", "$"),
    UK_POUND("GBP", "£"),
    AUSTRALIAN_DOLLAR("AUD", "AUD"),
    CANADIAN_DOLLAR("CAD", "CAD"),
    SWISS_FRANKEN("CHF", "CHF"),
    POLAND_ZLOTY("PLN", "PLN"),
    ;
    private final String name;
    private final String symbol;

    Currency(String name, String symbol) {
        this.name = name;
        this.symbol = symbol;
    }

    public static Currency findCurrency(String currency) {
        for (Currency value : Currency.values()) {
            if (value.name().equalsIgnoreCase(currency)) {
                return value;
            } else if (value.name.equalsIgnoreCase(currency)) {
                return value;
            } else if (value.symbol.equalsIgnoreCase(currency)) {
                return value;
            }
        }
        return Currency.US_DOLLAR;
    }
}
