package de.verdox.openhardwareapi.controller;

import de.verdox.openhardwareapi.component.service.PriceClusterService;
import de.verdox.openhardwareapi.io.api.Price;
import de.verdox.openhardwareapi.model.HardwareTypes;
import de.verdox.openhardwareapi.model.values.Currency;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Cache;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/cluster/prices")
@Validated
public class PriceClusterController {

    private final PriceClusterService service;

    public PriceClusterController(PriceClusterService service) {
        this.service = service;
    }

    @GetMapping("/ram/estimate")
    public ResponseEntity<BigDecimal> estimateRamStick(
            @RequestParam(value = "type", defaultValue = "DDR4") HardwareTypes.RamType ramType,
            @RequestParam("speed") int speedMtps,
            @RequestParam("capacityGb") @Min(1) int capacityGb,
            @RequestParam(value = "isKit", defaultValue = "false") boolean isKit,
            @RequestParam(value = "ecc", defaultValue = "false") boolean ecc,
            @RequestParam(value = "currency", defaultValue = "EURO") String currency,
            @RequestParam(value = "monthsSince", defaultValue = "2") @Min(0) int monthsSince
    ) {
        Price p = service.estimateRamStickPrice(
                ramType, speedMtps, capacityGb, isKit, ecc,
                Currency.findCurrency(currency), monthsSince
        );
        return ResponseEntity.ok(p.value());
    }

    // --- RAM AVG ---
    @GetMapping("/ram/avg-per-gb")
    public ResponseEntity<BigDecimal> avgPerGbRam(
            @RequestParam("type") @NotNull HardwareTypes.RamType type,
            @RequestParam(value = "currency", defaultValue = "EURO") String currency,
            @RequestParam(value = "monthsSince", defaultValue = "3") @Min(0) int monthsSince
    ) {
        var price = service.getAveragePricePerGBForRamWithType(type, Currency.findCurrency(currency), monthsSince);
        return ResponseEntity.ok(price.value());
    }

    @GetMapping("/ram/avg-per-gb/by-speed")
    public ResponseEntity<BigDecimal> avgPerGbRamBySpeed(
            @RequestParam("type") @NotNull HardwareTypes.RamType type,
            @RequestParam("speed") long speedMtps,
            @RequestParam(value = "currency", defaultValue = "EURO") String currency,
            @RequestParam(value = "monthsSince", defaultValue = "3") @Min(0) int monthsSince
    ) {
        var price = service.getAveragePricePerGBForRamWithTypeAndSpeed(type, speedMtps, Currency.findCurrency(currency), monthsSince);
        return ResponseEntity.ok(price.value());
    }

    // --- Mainboard AVG ---
    @GetMapping("/mainboard/avg")
    public ResponseEntity<BigDecimal> avgMainboard(
            @RequestParam("socket") @NotNull HardwareTypes.CpuSocket socket,
            @RequestParam("chipset") @NotNull HardwareTypes.Chipset chipset,
            @RequestParam("formFactor") @NotNull HardwareTypes.MotherboardFormFactor formFactor,
            @RequestParam(value = "currency", defaultValue = "EURO") String currency,
            @RequestParam(value = "monthsSince", defaultValue = "3") @Min(0) int monthsSince
    ) {
        var price = service.getAveragePriceForMainboardWithChipsetSocketAndFormFactor(socket, chipset, formFactor, Currency.findCurrency(currency), monthsSince);
        return ResponseEntity.ok(price.value());
    }

    // --- PSU AVG ---
    @GetMapping("/psu/avg")
    public ResponseEntity<BigDecimal> avgPsu(
            @RequestParam("wattage") long wattage,
            @RequestParam("rating") @NotNull HardwareTypes.PsuEfficiencyRating rating,
            @RequestParam("modularity") @NotNull HardwareTypes.PSU_MODULARITY modularity,
            @RequestParam(value = "currency", defaultValue = "EURO") String currency,
            @RequestParam(value = "monthsSince", defaultValue = "3") @Min(0) int monthsSince
    ) {
        var price = service.getAveragePriceForPSUWithWattsAndRatingAndModularity(wattage, rating, modularity, Currency.findCurrency(currency), monthsSince);
        return ResponseEntity.ok(price.value());
    }

    // --- GPU AVG ---
    @GetMapping("/gpu/avg")
    public ResponseEntity<BigDecimal> avgGpu(
            @RequestParam("gpuName") @NotNull String gpuName,
            @RequestParam(value = "currency", defaultValue = "EURO") String currency,
            @RequestParam(value = "monthsSince", defaultValue = "3") @Min(0) int monthsSince
    ) {
        gpuName = URLDecoder.decode(gpuName, StandardCharsets.UTF_8);
        var price = service.getAveragePriceForGPUChip(gpuName, Currency.findCurrency(currency), monthsSince);
        return ResponseEntity.ok(price.value());
    }


    // --- RAM ---

    @GetMapping("/ram/median-per-gb")
    public ResponseEntity<BigDecimal> medianPerGbRam(@RequestParam("type") @NotNull HardwareTypes.RamType type, @RequestParam(value = "currency", defaultValue = "EURO") String currency, @RequestParam(value = "monthsSince", defaultValue = "3") @Min(0) int monthsSince) {
        var price = service.getMedianPricePerGBForRamWithType(type, Currency.findCurrency(currency), monthsSince);
        return ResponseEntity.ok(price.value());
    }

    @GetMapping("/ram/median-per-gb/by-speed")
    public ResponseEntity<BigDecimal> medianPerGbRamBySpeed(@RequestParam("type") @NotNull HardwareTypes.RamType type, @RequestParam("speed") long speedMtps, @RequestParam(value = "currency", defaultValue = "EURO") String currency, @RequestParam(value = "monthsSince", defaultValue = "3") @Min(0) int monthsSince) {
        var price = service.getMedianPricePerGBForRamWithTypeAndSpeed(type, speedMtps, Currency.findCurrency(currency), monthsSince);
        return ResponseEntity.ok(price.value());
    }

    // --- Mainboard ---

    @GetMapping("/mainboard/median")
    public ResponseEntity<BigDecimal> medianMainboard(@RequestParam("socket") @NotNull HardwareTypes.CpuSocket socket, @RequestParam("chipset") @NotNull HardwareTypes.Chipset chipset, @RequestParam("formFactor") @NotNull HardwareTypes.MotherboardFormFactor formFactor, @RequestParam(value = "currency", defaultValue = "EURO") String currency, @RequestParam(value = "monthsSince", defaultValue = "3") @Min(0) int monthsSince) {
        var price = service.getMedianPriceForMainboardWithChipsetSocketAndFormFactor(socket, chipset, formFactor, Currency.findCurrency(currency), monthsSince);
        return ResponseEntity.ok(price.value());
    }

    // --- PSU ---

    @GetMapping("/psu/median")
    public ResponseEntity<BigDecimal> medianPsu(@RequestParam("wattage") long wattage, @RequestParam("rating") @NotNull HardwareTypes.PsuEfficiencyRating rating, @RequestParam("modularity") @NotNull HardwareTypes.PSU_MODULARITY modularity, @RequestParam(value = "currency", defaultValue = "EURO") String currency, @RequestParam(value = "monthsSince", defaultValue = "3") @Min(0) int monthsSince) {
        var price = service.getMedianPriceForPSUWithWattsAndRatingAndModularity(wattage, rating, modularity, Currency.findCurrency(currency), monthsSince);
        return ResponseEntity.ok(price.value());
    }

    // --- GPU ---

    @GetMapping("/gpu/median")
    public ResponseEntity<BigDecimal> medianGpu(@RequestParam("gpuName") @NotNull String gpuName, @RequestParam(value = "currency", defaultValue = "EURO") String currency, @RequestParam(value = "monthsSince", defaultValue = "3") @Min(0) int monthsSince) {
        gpuName = URLDecoder.decode(gpuName, StandardCharsets.UTF_8);

        var price = service.getMedianPriceForGPUChip(gpuName, Currency.findCurrency(currency), monthsSince);
        return ResponseEntity.ok(price.value());
    }
}
