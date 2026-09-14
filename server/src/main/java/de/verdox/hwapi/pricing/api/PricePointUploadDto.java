package de.verdox.hwapi.pricing.api;

import de.verdox.hwapi.catalog.domain.values.Currency;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PricePointUploadDto(
        @NotNull String EAN,
        @NotNull String marketPlaceDomain,
        @NotNull String marketPlaceItemID,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal sellPrice,
        @NotNull Currency currency,
        @NotNull @PastOrPresent LocalDate sellDate
) {}