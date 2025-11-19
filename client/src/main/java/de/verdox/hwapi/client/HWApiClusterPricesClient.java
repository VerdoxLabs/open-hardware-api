package de.verdox.hwapi.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.hwapi.benchmarkapi.entity.CPUBenchmarkResults;
import de.verdox.hwapi.model.HardwareTypes;
import de.verdox.hwapi.model.dto.PricePointUploadDto;
import de.verdox.hwapi.model.values.Currency;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class HWApiClusterPricesClient extends HWApiClient{
    public HWApiClusterPricesClient(String baseUrl) {
        super(baseUrl);
    }

    public HWApiClusterPricesClient(String baseUrl, ObjectMapper mapper) {
        super(baseUrl, mapper);
    }

    public Optional<BigDecimal> getMedianPerGbRam(HardwareTypes.RamType type, Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/ram/median-per-gb", b -> {
            b.queryParam("type", type.name());
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        byte[] bytes = http.get().uri(uri)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .blockOptional().orElse(null);
        if (bytes == null || bytes.length == 0) return Optional.empty();
        try {
            var node = om.readTree(bytes);
            if (node.isNull()) return Optional.empty();
            return Optional.of(om.treeToValue(node, BigDecimal.class));
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse median-per-gb RAM response", e);
        }
    }

    public Optional<BigDecimal> getEstimatedRamStickPrice(
            HardwareTypes.RamType type,
            long speedMtps,
            int capacityGb,
            boolean isKit,
            boolean ecc,
            Currency currency,
            int monthsSince
    ) {
        String uri = uriBuilder("/cluster/prices/ram/estimate", b -> {
            b.queryParam("type", type.name());
            b.queryParam("speed", speedMtps);
            b.queryParam("capacityGb", capacityGb);
            b.queryParam("isKit", isKit);
            b.queryParam("ecc", ecc);
            if (currency != null)
                b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    public Optional<BigDecimal> getMedianPerGbRamBySpeed(HardwareTypes.RamType type, long speedMtps, Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/ram/median-per-gb/by-speed", b -> {
            b.queryParam("type", type.name());
            b.queryParam("speed", speedMtps);
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    public Optional<BigDecimal> getMedianMainboard(HardwareTypes.CpuSocket socket, HardwareTypes.Chipset chipset,
                                                   HardwareTypes.MotherboardFormFactor formFactor,
                                                   Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/mainboard/median", b -> {
            b.queryParam("socket", socket.name());
            b.queryParam("chipset", chipset.name());
            b.queryParam("formFactor", formFactor.name());
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    // --- AVG per-GB RAM ---
    public Optional<BigDecimal> getAveragePerGbRam(HardwareTypes.RamType type, Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/ram/avg-per-gb", b -> {
            b.queryParam("type", type.name());
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    public Optional<BigDecimal> getAveragePerGbRamBySpeed(HardwareTypes.RamType type, long speedMtps, Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/ram/avg-per-gb/by-speed", b -> {
            b.queryParam("type", type.name());
            b.queryParam("speed", speedMtps);
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    // --- AVG Mainboard ---
    public Optional<BigDecimal> getAverageMainboard(HardwareTypes.CpuSocket socket, HardwareTypes.Chipset chipset,
                                                    HardwareTypes.MotherboardFormFactor formFactor,
                                                    Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/mainboard/avg", b -> {
            b.queryParam("socket", socket.name());
            b.queryParam("chipset", chipset.name());
            b.queryParam("formFactor", formFactor.name());
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    // --- AVG PSU ---
    public Optional<BigDecimal> getAveragePsu(long wattage, HardwareTypes.PsuEfficiencyRating rating,
                                              HardwareTypes.PSU_MODULARITY modularity,
                                              Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/psu/avg", b -> {
            b.queryParam("wattage", wattage);
            b.queryParam("rating", rating.name());
            b.queryParam("modularity", modularity.name());
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    // --- AVG GPU ---
    public Optional<BigDecimal> getAverageGpu(String gpuName, Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/gpu/avg", b -> {
            b.queryParam("gpuName", gpuName);
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }


    public Optional<BigDecimal> getMedianPsu(long wattage, HardwareTypes.PsuEfficiencyRating rating,
                                             HardwareTypes.PSU_MODULARITY modularity,
                                             Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/psu/median", b -> {
            b.queryParam("wattage", wattage);
            b.queryParam("rating", rating.name());
            b.queryParam("modularity", modularity.name());
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    public Optional<BigDecimal> getMedianGpu(String gpuName,
                                             Currency currency, int monthsSince) {
        String uri = uriBuilder("/cluster/prices/gpu/median", b -> {
            b.queryParam("gpuName", gpuName);
            if (currency != null) b.queryParam("currency", currency.name());
            b.queryParam("monthsSince", monthsSince);
        });
        return getPriceDto(uri);
    }

    private Optional<BigDecimal> getPriceDto(String uri) {
        byte[] bytes = http.get().uri(uri)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .blockOptional().orElse(null);
        if (bytes == null || bytes.length == 0) return Optional.empty();
        try {
            var node = om.readTree(bytes);
            if (node.isMissingNode() || node.isNull()) return Optional.empty();
            return Optional.of(om.treeToValue(node, BigDecimal.class));
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse price dto: " + new String(bytes, StandardCharsets.UTF_8), e);
        }
    }


}
