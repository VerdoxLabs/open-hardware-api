package de.verdox.hwapi.benchmarkapi;

import de.verdox.hwapi.benchmarkapi.entity.BenchmarkResults;
import de.verdox.hwapi.benchmarkapi.entity.CPUBenchmarkResults;
import de.verdox.hwapi.benchmarkapi.entity.GPUBenchmarkResults;
import de.verdox.hwapi.benchmarkapi.repository.BenchmarkResultRepository;
import de.verdox.hwapi.benchmarkapi.repository.CPUBenchmarkRepository;
import de.verdox.hwapi.benchmarkapi.repository.GPUBenchmarkRepository;
import de.verdox.hwapi.io.api.selenium.SeleniumBasedWebScraper;
import de.verdox.hwapi.util.QueryUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class BenchmarkService {
    private final Logger LOGGER = Logger.getLogger(BenchmarkService.class.getName());
    private final PassmarkDataScraper passmarkDataScraper = new PassmarkDataScraper();
    private final CPUBenchmarkRepository cpuBenchmarkRepository;
    private final GPUBenchmarkRepository gpuBenchmarkRepository;

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void updateDatabase() {
        LOGGER.info("Updating benchmark database");
        try {
            fetchFromPassmark();
        } catch (MalformedURLException | SeleniumBasedWebScraper.ChallengeFoundException e) {
            LOGGER.log(Level.SEVERE, "Could not fetch data from passmark", e);
        }
    }

    @Transactional
    protected void fetchFromPassmark() throws MalformedURLException, SeleniumBasedWebScraper.ChallengeFoundException {
        String source = "passmark";

        passmarkDataScraper.tryScrapeCPUData((cpuModel, cpuMarkScore, threadMarkScore) -> {
            saveResults(source, cpuModel, cpuBenchmarkRepository, CPUBenchmarkResults::new, cpuBenchmarkResults -> {
                cpuBenchmarkResults.setCpuMarkScore(cpuMarkScore);
                cpuBenchmarkResults.setThreadMarkScore(threadMarkScore);
            });
        });

        passmarkDataScraper.tryScrapeGPUData((gpuChip, g3dMark, g2dMark) -> {
            saveResults(source, gpuChip, gpuBenchmarkRepository, GPUBenchmarkResults::new, gpuBenchmarkResults -> {
                gpuBenchmarkResults.setG2DMarkScore(g2dMark);
                gpuBenchmarkResults.setG3DMarkScore(g3dMark);
            });
        });
    }


    public CPUBenchmarkResults getForCpu(String cpuModelName) {
        String source = "passmark";
        return findBestMatch(source, cpuModelName, cpuBenchmarkRepository);
    }

    public GPUBenchmarkResults getForGPU(String gpuCanonicalName) {
        String source = "passmark";
        return findBestMatch(source, gpuCanonicalName, gpuBenchmarkRepository);
    }

    private <BENCHMARK extends BenchmarkResults<?>> void saveResults(String source, String model, BenchmarkResultRepository<BENCHMARK> repo, Supplier<BENCHMARK> constructor, Consumer<BENCHMARK> consumer) {
        repo.findByModelNameAndSource(model.trim(), source.trim()).ifPresentOrElse(results -> {
            consumer.accept((results));
            repo.save(results);
        }, () -> {
            BENCHMARK results = constructor.get();
            results.setIdentifiers(source.trim(), model.trim());
            consumer.accept((results));
            repo.save(results);
        });
    }

    private <BENCHMARK extends BenchmarkResults<?>> BENCHMARK findBestMatch(
            String source,
            String queryModelName,
            BenchmarkResultRepository<BENCHMARK> repo
    ) {
        if (queryModelName == null || queryModelName.isBlank()) {
            return null;
        }

        Optional<BENCHMARK> exact = repo.findByModelNameAndSource(queryModelName, source);
        if (exact.isPresent()) {
            return exact.get();
        }
        Set<BENCHMARK> candidates = repo.findBySource(source);

        return QueryUtil.search(queryModelName, candidates, benchmark -> benchmark.getModelName()).orElse(null);
    }
}
