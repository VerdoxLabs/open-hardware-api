package de.verdox.hwapi.integration.client;

import java.util.List;

public record PriceSeriesResponseDTO(
        boolean refreshStarted,
        List<PriceSeriesDTO> series
) {
}
