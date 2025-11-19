package de.verdox.hwapi.model.dto;

import de.verdox.hwapi.model.values.Currency;
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