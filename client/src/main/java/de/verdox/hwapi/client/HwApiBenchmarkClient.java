package de.verdox.hwapi.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.hwapi.benchmarkapi.entity.CPUBenchmarkResults;
import de.verdox.hwapi.benchmarkapi.entity.GPUBenchmarkResults;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class HwApiBenchmarkClient extends HWApiClient {
    public HwApiBenchmarkClient(String baseUrl) {
        super(baseUrl);
    }

    public HwApiBenchmarkClient(String baseUrl, ObjectMapper mapper) {
        super(baseUrl, mapper);
    }

    public Optional<CPUBenchmarkResults> getCpuBenchmark(String cpuModelName) {
        String uri = uriBuilder("/benchmark/cpu", b -> {
            if (cpuModelName != null && !cpuModelName.isBlank()) {
                b.queryParam("cpuModelName", cpuModelName);
            }
        });

        byte[] bytes = http.get()
                .uri(uri)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .blockOptional()
                .orElse(null);

        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }

        try {
            JsonNode node = om.readTree(bytes);
            if (node == null || node.isNull() || node.isMissingNode()) {
                return Optional.empty();
            }
            return Optional.of(om.treeToValue(node, CPUBenchmarkResults.class));
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse CPU benchmark response: " +
                    new String(bytes, StandardCharsets.UTF_8), e);
        }
    }

    public Optional<GPUBenchmarkResults> getGpuBenchmark(String gpuCanonicalName) {
        String uri = uriBuilder("/benchmark/gpu", b -> {
            if (gpuCanonicalName != null && !gpuCanonicalName.isBlank()) {
                b.queryParam("gpuCanonicalName", gpuCanonicalName);
            }
        });

        byte[] bytes = http.get()
                .uri(uri)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), this::toProblem)
                .bodyToMono(byte[].class)
                .blockOptional()
                .orElse(null);

        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }

        try {
            JsonNode node = om.readTree(bytes);
            if (node == null || node.isNull() || node.isMissingNode()) {
                return Optional.empty();
            }
            return Optional.of(om.treeToValue(node, GPUBenchmarkResults.class));
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse GPU benchmark response: " +
                    new String(bytes, StandardCharsets.UTF_8), e);
        }
    }
}
