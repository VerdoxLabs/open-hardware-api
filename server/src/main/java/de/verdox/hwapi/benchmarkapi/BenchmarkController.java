package de.verdox.hwapi.benchmarkapi;

import de.verdox.hwapi.benchmarkapi.entity.CPUBenchmarkResults;
import de.verdox.hwapi.benchmarkapi.entity.GPUBenchmarkResults;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/benchmark")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    @GetMapping("/cpu")
    @Transactional(readOnly = true)
    public ResponseEntity<CPUBenchmarkResults> getCPUBenchmarkScores(@RequestParam(value = "cpuModelName", required = false) String cpuModelName) {
        return ResponseEntity.ok(benchmarkService.getForCpu(cpuModelName));
    }

    @GetMapping("/gpu")
    @Transactional(readOnly = true)
    public ResponseEntity<GPUBenchmarkResults> getGPUBenchmarkScores(@RequestParam(value = "gpuCanonicalName", required = false) String gpuCanonicalName) {
        return ResponseEntity.ok(benchmarkService.getForGPU(gpuCanonicalName));
    }
}
