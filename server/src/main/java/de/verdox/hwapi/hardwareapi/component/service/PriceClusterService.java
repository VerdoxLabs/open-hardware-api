package de.verdox.hwapi.hardwareapi.component.service;

import de.verdox.hwapi.component.repository.*;
import de.verdox.hwapi.io.api.Price;
import de.verdox.hwapi.model.*;
import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.priceapi.repository.RemoteSoldItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class PriceClusterService {
    private final RemoteSoldItemRepository repo;
    private final HardwareSpecService hardwareSpecService;


    public Price getMedianPricePerGBForRamWithType(HardwareTypes.RamType ramType, Currency currency, int monthSince) {
        RAMRepository repository = (RAMRepository) hardwareSpecService.getRepo(RAM.class);
        List<RAM> ramList = repository.findByType(ramType);
        return getMedianPricePerGB(ramList, currency, monthSince);
    }

    public Price getMedianPricePerGBForRamWithTypeAndSpeed(HardwareTypes.RamType ramType, long speed, Currency currency, int monthSince) {
        RAMRepository repository = (RAMRepository) hardwareSpecService.getRepo(RAM.class);
        List<RAM> ramList = repository.findByTypeAndSpeedMtpsEquals(ramType, (int) speed);
        return getMedianPricePerGB(ramList, currency, monthSince);
    }

    public Price getMedianPriceForMainboardWithChipsetSocketAndFormFactor(HardwareTypes.CpuSocket cpuSocket, HardwareTypes.Chipset chipset, HardwareTypes.MotherboardFormFactor formFactor, Currency currency, int monthSince) {
        MotherboardRepository repository = (MotherboardRepository) hardwareSpecService.getRepo(Motherboard.class);
        return getMedianPrice(repository.findByChipsetAndSocketAndFormFactor(chipset, cpuSocket, formFactor), currency, monthSince);
    }

    public Price getMedianPriceForPSUWithWattsAndRatingAndModularity(long wattage, HardwareTypes.PsuEfficiencyRating rating, HardwareTypes.PSU_MODULARITY psuModularity, Currency currency, int monthSince) {
        PSURepository repository = (PSURepository) hardwareSpecService.getRepo(PSU.class);
        Price price = getMedianPrice(repository.findByWattageAndEfficiencyRating((int) wattage, rating), currency, monthSince);
        if (psuModularity.equals(HardwareTypes.PSU_MODULARITY.FULL_MODULAR)) {
            return new Price(price.value().multiply(BigDecimal.valueOf(1.1d)), currency);
        } else if (psuModularity.equals(HardwareTypes.PSU_MODULARITY.SEMI_MODULAR)) {
            return new Price(price.value().multiply(BigDecimal.valueOf(1.05d)), currency);
        }
        return price;
    }

    public Price getMedianPriceForGPUChip(String gpuName, Currency currency, int monthSince) {
        gpuName = gpuName.trim().replaceAll("\\s+", " ");
        GPURepository repository = (GPURepository) hardwareSpecService.getRepo(GPU.class);
        return getMedianPrice(repository.findByGpuCanonicalNameContainingIgnoreCase(gpuName.trim()), currency, monthSince);
    }

    // --- NEU: AVG per-GB (RAM) ---
    public Price getAveragePricePerGBForRamWithType(HardwareTypes.RamType ramType, Currency currency, int monthSince) {
        RAMRepository repository = (RAMRepository) hardwareSpecService.getRepo(RAM.class);
        List<RAM> ramList = repository.findByType(ramType);
        return getAveragePricePerGB(ramList, currency, monthSince);
    }

    public Price getAveragePricePerGBForRamWithTypeAndSpeed(HardwareTypes.RamType ramType, long speed, Currency currency, int monthSince) {
        RAMRepository repository = (RAMRepository) hardwareSpecService.getRepo(RAM.class);
        List<RAM> ramList = repository.findByTypeAndSpeedMtpsEquals(ramType, (int) speed);
        return getAveragePricePerGB(ramList, currency, monthSince);
    }

    // --- NEU: AVG gesamt (Mainboard/PSU/GPU) ---
    public Price getAveragePriceForMainboardWithChipsetSocketAndFormFactor(HardwareTypes.CpuSocket cpuSocket, HardwareTypes.Chipset chipset, HardwareTypes.MotherboardFormFactor formFactor, Currency currency, int monthSince) {
        MotherboardRepository repository = (MotherboardRepository) hardwareSpecService.getRepo(Motherboard.class);
        return getAveragePrice(repository.findByChipsetAndSocketAndFormFactor(chipset, cpuSocket, formFactor), currency, monthSince);
    }

    public Price getAveragePriceForPSUWithWattsAndRatingAndModularity(long wattage, HardwareTypes.PsuEfficiencyRating rating, HardwareTypes.PSU_MODULARITY psuModularity, Currency currency, int monthSince) {
        PSURepository repository = (PSURepository) hardwareSpecService.getRepo(PSU.class);
        Price price = getAveragePrice(repository.findByWattageAndEfficiencyRating((int) wattage, rating), currency, monthSince);

        if (psuModularity.equals(HardwareTypes.PSU_MODULARITY.FULL_MODULAR)) {
            return new Price(price.value().multiply(BigDecimal.valueOf(1.1d)), currency);
        } else if (psuModularity.equals(HardwareTypes.PSU_MODULARITY.SEMI_MODULAR)) {
            return new Price(price.value().multiply(BigDecimal.valueOf(1.05d)), currency);
        }
        return price;
    }

    public Price getAveragePriceForGPUChip(String gpuName, Currency currency, int monthSince) {
        gpuName = gpuName.trim().replaceAll("\\s+", " ");
        GPURepository repository = (GPURepository) hardwareSpecService.getRepo(GPU.class);
        return getAveragePrice(repository.findByGpuCanonicalNameContainingIgnoreCase(gpuName), currency, monthSince);
    }

// ---------- Helper für AVG ----------

    private Price getAveragePricePerGB(List<RAM> ramList, Currency currency, int monthSince) {
        if (ramList.isEmpty()) return new Price(BigDecimal.ZERO, currency);

        Map<String, Integer> eanToGb = ramList.stream()
                .filter(Objects::nonNull)
                .filter(r -> r.getEANs() != null)
                .filter(r -> r.getTotalSizeGB() > 0)
                .collect(Collectors.toMap(ram -> ram.getEANs().stream().findFirst().orElse(""), RAM::getTotalSizeGB, (a, b) -> a));

        if (eanToGb.isEmpty()) return new Price(BigDecimal.ZERO, currency);

        LocalDate since = LocalDate.now().minusMonths(monthSince);
        List<RemoteSoldItemRepository.EANPricePoint> rows =
                repo.findUnitPricesSince(eanToGb.keySet(), since, currency);

        List<BigDecimal> perGb = rows.stream()
                .map(row -> {
                    Integer gb = eanToGb.get(row.getEan());
                    if (gb == null || gb <= 0) return null;
                    return row.getPrice().divide(BigDecimal.valueOf(gb), 4, RoundingMode.HALF_UP);
                })
                .filter(Objects::nonNull)
                .toList();

        if (perGb.isEmpty()) return new Price(BigDecimal.ZERO, currency);

        BigDecimal avg = perGb.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(perGb.size()), 4, RoundingMode.HALF_UP);

        return new Price(avg, currency);
    }

    private Price getAveragePrice(List<? extends HardwareSpec<?>> specs, Currency currency, int monthSince) {
        if (specs.isEmpty()) return new Price(BigDecimal.ZERO, currency);

        Set<String> eans = specs.stream()
                .flatMap(hardwareSpec -> hardwareSpec.getEANs().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (eans.isEmpty()) return new Price(BigDecimal.ZERO, currency);

        LocalDate since = LocalDate.now().minusMonths(monthSince);
        List<RemoteSoldItemRepository.EANPricePoint> rows =
                repo.findUnitPricesSince(eans, since, currency);

        if (rows.isEmpty()) return new Price(BigDecimal.ZERO, currency);

        BigDecimal sum = rows.stream()
                .map(RemoteSoldItemRepository.EANPricePoint::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avg = sum.divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP);
        return new Price(avg, currency);
    }

    public Price estimateRamStickPrice(
            HardwareTypes.RamType ramType,
            int speedMtps,
            int capacityGb,
            boolean isKit,
            boolean ecc,
            Currency currency,
            int monthsSince
    ) {
        RAMRepository repository = (RAMRepository) hardwareSpecService.getRepo(RAM.class);
        List<RAM> ramList = repository.findByTypeAndSpeedMtpsEquals(ramType, speedMtps);

        Price perGb = getMedianPricePerGB(ramList, currency, monthsSince);

        // Heuristiken (konservativ start, später lernbar machen)
        BigDecimal capAdj = switch (capacityGb) {
            case 8 -> bd(1.25);
            case 16 -> bd(1.00);
            case 32 -> bd(0.94);
            default -> bd(1.00);
        };
        BigDecimal kitAdj = isKit ? bd(0.97) : bd(1.05);
        BigDecimal eccAdj = ecc ? bd(0.85) : bd(1.00);

        BigDecimal base = perGb.value().multiply(bd(capacityGb));
        BigDecimal withAdj = base.multiply(capAdj).multiply(kitAdj).multiply(eccAdj);

        BigDecimal result = withAdj.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new Price(result, currency);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    // --- NEU: per-GB ---
    private Price getMedianPricePerGB(List<RAM> ramList, Currency currency, int monthSince) {
        if (ramList == null || ramList.isEmpty()) return new Price(BigDecimal.ZERO, currency);

        Map<String, Integer> eanToGb = ramList.stream()
                .filter(Objects::nonNull)
                .filter(r -> r.getEANs() != null)
                .filter(r -> r.getTotalSizeGB() > 0)
                .collect(Collectors.toMap(ram -> ram.getEANs().stream().findFirst().orElse(""), RAM::getTotalSizeGB, (a, b) -> a));

        if (eanToGb.isEmpty()) return new Price(BigDecimal.ZERO, currency);

        LocalDate since = LocalDate.now().minusMonths(monthSince);
        List<RemoteSoldItemRepository.EANPricePoint> rows =
                repo.findUnitPricesSince(eanToGb.keySet(), since, currency);

        // (pricePerGb, capacityGb)
        List<BigDecimal[]> samples = rows.stream()
                .map(row -> {
                    Integer gb = eanToGb.get(row.getEan());
                    if (gb == null || gb <= 0) return null;
                    BigDecimal perGb = row.getPrice().divide(BigDecimal.valueOf(gb), 4, RoundingMode.HALF_UP);
                    return new BigDecimal[]{perGb, BigDecimal.valueOf(gb)};
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(a -> a[0])) // sort by €/GB
                .toList();

        if (samples.isEmpty()) return new Price(BigDecimal.ZERO, currency);

        // IQR-Outlier-Filter
        List<BigDecimal> perGbSorted = samples.stream().map(a -> a[0]).toList();
        BigDecimal q1 = percentile(perGbSorted, 25);
        BigDecimal q3 = percentile(perGbSorted, 75);
        BigDecimal iqr = q3.subtract(q1);
        BigDecimal lo = q1.subtract(iqr.multiply(new BigDecimal("1.5")));
        BigDecimal hi = q3.add(iqr.multiply(new BigDecimal("1.5")));

        List<BigDecimal[]> filtered = samples.stream()
                .filter(a -> a[0].compareTo(lo) >= 0 && a[0].compareTo(hi) <= 0)
                .toList();
        if (filtered.isEmpty()) filtered = samples; // fallback

        // Leicht kapazitäts-gegengewichtet: Gewicht = 1 / GB
        List<BigDecimal> values = new ArrayList<>();
        List<BigDecimal> weights = new ArrayList<>();
        for (BigDecimal[] a : filtered) {
            BigDecimal v = a[0];
            BigDecimal gb = a[1];
            BigDecimal w = BigDecimal.ONE.divide(gb, 6, RoundingMode.HALF_UP); // 8GB > 16GB > 32GB
            values.add(v);
            weights.add(w);
        }
        BigDecimal wMedian = weightedMedian(values, weights); // robusterer €/GB

        return new Price(wMedian.setScale(4, RoundingMode.HALF_UP), currency);
    }

    // Bestehende Gesamt-Median-Funktion (unverändert)
    private Price getMedianPrice(List<? extends HardwareSpec<?>> specs, Currency currency, int monthSince) {
        String[] eans = specs.stream().map(HardwareSpec::getEANs).toArray(String[]::new);
        java.math.BigDecimal value = repo.medianPriceForEansSince(List.of(eans), LocalDate.now().minusMonths(monthSince), currency);
        return new Price(value, currency);
    }

    private static BigDecimal median(List<BigDecimal> sorted) {
        int n = sorted.size();
        if (n == 0) return BigDecimal.ZERO;
        if (n % 2 == 1) return sorted.get(n / 2);
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentile(List<BigDecimal> sorted, int p) {
        int n = sorted.size();
        if (n == 0) return BigDecimal.ZERO;
        if (p <= 0) return sorted.get(0);
        if (p >= 100) return sorted.get(n - 1);
        double rank = (p / 100.0) * (n - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) return sorted.get(lo);
        BigDecimal a = sorted.get(lo), b = sorted.get(hi);
        BigDecimal frac = BigDecimal.valueOf(rank - lo);
        return a.add(b.subtract(a).multiply(frac));
    }

    /**
     * Werte sind bereits gemeinsam sortiert.
     */
    private static BigDecimal weightedMedian(List<BigDecimal> values, List<BigDecimal> weights) {
        int n = values.size();
        BigDecimal total = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal half = total.divide(new BigDecimal("2"), 6, RoundingMode.HALF_UP);
        BigDecimal cum = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            cum = cum.add(weights.get(i));
            if (cum.compareTo(half) >= 0) return values.get(i);
        }
        return values.get(n - 1);
    }
}
