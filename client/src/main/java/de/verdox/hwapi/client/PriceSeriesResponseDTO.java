package de.verdox.hwapi.client;

import java.util.List;

public record PriceSeriesResponseDTO(
        boolean refreshStarted,
        List<PriceSeriesDTO> series
) {
}
