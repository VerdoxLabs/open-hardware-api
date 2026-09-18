package de.verdox.hwapi.catalog.ingestion.opendb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.hwapi.catalog.application.HardwareSpecService;
import de.verdox.hwapi.catalog.domain.*;
import de.verdox.hwapi.catalog.domain.values.DimensionsMm;
import de.verdox.hwapi.catalog.domain.values.FanSpec;
import de.verdox.hwapi.pricing.application.RemoteActiveListingWriterService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** Imports OpenDB JSON records into the existing catalog model. */
@Service
@EnableConfigurationProperties(OpenDbProperties.class)
public class OpenDbImportService {
    private final OpenDbProperties properties;
    private final OpenDbRepository repository;
    private final HardwareSpecService hardwareSpecService;
    private final RemoteActiveListingWriterService listingWriter;
    private final ObjectMapper objectMapper;
    private final Path resultFile;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<Instant> startedAt = new AtomicReference<>();
    private final AtomicReference<Instant> finishedAt = new AtomicReference<>();
    private final AtomicReference<ImportResult> lastResult = new AtomicReference<>();
    private final AtomicReference<String> phase = new AtomicReference<>("IDLE");
    private final AtomicReference<String> currentCategory = new AtomicReference<>();
    private final AtomicReference<String> currentFile = new AtomicReference<>();
    private final AtomicInteger processedFiles = new AtomicInteger();
    private final AtomicInteger totalFiles = new AtomicInteger();
    private final AtomicLong downloadedBytes = new AtomicLong();
    private final AtomicLong downloadTotalBytes = new AtomicLong();
    private final AtomicLong phaseCurrent = new AtomicLong();
    private final AtomicLong phaseTotal = new AtomicLong();

    public OpenDbImportService(OpenDbProperties properties, OpenDbRepository repository,
                               HardwareSpecService hardwareSpecService, RemoteActiveListingWriterService listingWriter,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.repository = repository;
        this.hardwareSpecService = hardwareSpecService;
        this.listingWriter = listingWriter;
        this.objectMapper = objectMapper;
        this.resultFile = properties.checkoutDirectory().toAbsolutePath().normalize().getParent()
                .resolve(".opendb-last-import.json");
        restoreLastResult();
    }

    public ImportResult importLatest() {
        if (!running.compareAndSet(false, true)) {
            return new ImportResult(properties.enabled(), 0, 0, "Ein OpenDB-Import läuft bereits.");
        }
        startedAt.set(Instant.now());
        resetProgress();
        try {
            ImportResult result = doImport();
            publishResult(result);
            return result;
        } finally {
            finishedAt.set(Instant.now());
            running.set(false);
        }
    }

    public boolean startAsyncImport() {
        if (!properties.enabled() || !running.compareAndSet(false, true)) return false;
        startedAt.set(Instant.now());
        resetProgress();
        CompletableFuture.runAsync(() -> {
            try {
                publishResult(doImport());
            } finally {
                finishedAt.set(Instant.now());
                running.set(false);
            }
        });
        return true;
    }

    public OpenDbStatus status() {
        return new OpenDbStatus(properties.enabled(), properties.repository(), properties.branch(),
                properties.checkoutDirectory().toString(), properties.schemaDirectory().toString(),
                running.get(), phase.get(), processedFiles.get(), totalFiles.get(), downloadedBytes.get(),
                downloadTotalBytes.get(), phaseCurrent.get(), phaseTotal.get(), currentCategory.get(), currentFile.get(), startedAt.get(),
                finishedAt.get(), lastResult.get());
    }

    private ImportResult doImport() {
        if (!properties.enabled()) return ImportResult.disabled();
        try {
            phase.set("REPOSITORY_SYNC");
            Path checkout = repository.sync(this::updateDownloadProgress);
            Path data = checkout.resolve("open-db");
            if (!Files.isDirectory(data)) throw new IOException("OpenDB checkout has no open-db directory: " + data);

            Set<HardwareSpec<?>> records = new LinkedHashSet<>();
            Map<String, Integer> importedByCategory = new TreeMap<>();
            int invalid = 0;
            List<Path> filesToParse = new ArrayList<>();
            phase.set("SCANNING");
            try (Stream<Path> categories = Files.list(data)) {
                for (Path category : categories.filter(Files::isDirectory).toList()) {
                    try (Stream<Path> files = Files.list(category)) {
                        filesToParse.addAll(files.filter(p -> p.getFileName().toString().endsWith(".json")).toList());
                    }
                }
            }
            totalFiles.set(filesToParse.size());
            phase.set("PARSING");
            int parserThreads = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
            ExecutorService parserPool = Executors.newFixedThreadPool(parserThreads);
            CompletionService<ParsedFile> completed = new ExecutorCompletionService<>(parserPool);
            try {
                for (Path file : filesToParse) completed.submit(() -> parseFile(checkout, file));
                for (int i = 0; i < filesToParse.size(); i++) {
                    ParsedFile parsed = completed.take().get();
                    currentCategory.set(parsed.category());
                    currentFile.set(parsed.fileName());
                    if (parsed.spec() != null) {
                        records.add(parsed.spec());
                        importedByCategory.merge(parsed.category(), 1, Integer::sum);
                    }
                    if (parsed.invalid()) invalid++;
                    processedFiles.incrementAndGet();
                }
            } finally {
                parserPool.shutdownNow();
            }
            phase.set("PERSISTING");
            List<HardwareSpec<?>> recordsToPersist = new ArrayList<>(records);
            phaseCurrent.set(0);
            phaseTotal.set(recordsToPersist.size());
            List<String> persistenceErrors = new ArrayList<>();
            final int persistenceBatchSize = 100;
            for (int start = 0; start < recordsToPersist.size(); start += persistenceBatchSize) {
                int end = Math.min(start + persistenceBatchSize, recordsToPersist.size());
                try {
                    // Each service call gets its own transaction through Spring's proxy.
                    // A bad batch therefore cannot roll back all previously saved batches.
                    hardwareSpecService.onScrapeMulti(new LinkedHashSet<>(recordsToPersist.subList(start, end)));
                } catch (Throwable persistFailure) {
                    persistenceErrors.add("Batch " + (start + 1) + "-" + end + ": "
                            + rootMessage(persistFailure));
                } finally {
                    phaseCurrent.set(end);
                }
            }
            try {
                listingWriter.attachImagesForHardware(records);
            } catch (Exception imageFailure) {
                persistenceErrors.add("Awin-Bilder: " + rootMessage(imageFailure));
            }
            phase.set("COMPLETE");
            String persistenceError = persistenceErrors.isEmpty() ? null
                    : persistenceErrors.size() + " Speicher-Batch(es) fehlgeschlagen: " + persistenceErrors.getFirst();
            return new ImportResult(true, records.size(), invalid, persistenceError, importedByCategory);
        } catch (Exception e) {
            phase.set("ERROR");
            return new ImportResult(false, 0, 0, e.getMessage());
        }
    }

    private void publishResult(ImportResult result) {
        lastResult.set(result);
        try {
            Files.createDirectories(resultFile.getParent());
            objectMapper.writeValue(resultFile.toFile(), new ImportSnapshot(Instant.now(), result));
        } catch (IOException ignored) {
            // A failed status snapshot must never invalidate a successful import.
        }
    }

    private void restoreLastResult() {
        if (!Files.isRegularFile(resultFile)) return;
        try {
            ImportSnapshot snapshot = objectMapper.readValue(resultFile.toFile(), ImportSnapshot.class);
            if (snapshot != null) {
                lastResult.set(snapshot.result());
                finishedAt.set(snapshot.finishedAt());
            }
        } catch (IOException ignored) {
            // Ignore an incomplete snapshot and let the next import replace it.
        }
    }

    private ParsedFile parseFile(Path checkout, Path file) {
        String category = file.getParent().getFileName().toString();
        try {
            JsonNode node = objectMapper.readTree(file.toFile());
            validateAgainstSchema(checkout, category, node);
            return new ParsedFile(category, file.getFileName().toString(), parse(category, node), false);
        } catch (Exception ignored) {
            return new ParsedFile(category, file.getFileName().toString(), null, true);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private void resetProgress() {
        phase.set("STARTING");
        currentCategory.set(null);
        currentFile.set(null);
        processedFiles.set(0);
        totalFiles.set(0);
        downloadedBytes.set(0);
        downloadTotalBytes.set(0);
        phaseCurrent.set(0);
        phaseTotal.set(0);
    }

    private void updateDownloadProgress(String currentPhase, long current, long total) {
        phase.set(currentPhase);
        phaseCurrent.set(current);
        phaseTotal.set(total);
        downloadedBytes.set(current);
        downloadTotalBytes.set(total);
    }

    HardwareSpec<?> parse(String category, JsonNode node) throws Exception {
        Class<? extends HardwareSpec<?>> type = switch (category.toLowerCase(Locale.ROOT)) {
            case "cpu" -> CPU.class;
            case "gpu" -> GPU.class;
            case "ram" -> RAM.class;
            case "storage" -> Storage.class;
            case "motherboard" -> Motherboard.class;
            case "psu" -> PSU.class;
            case "pccase" -> PCCase.class;
            case "cpucooler" -> CPUCooler.class;
            case "casefan" -> Fan.class;
            case "monitor" -> Display.class;
            case "headphones" -> Headphones.class;
            case "keyboard" -> Keyboard.class;
            case "mouse" -> Mouse.class;
            case "speakers" -> Speakers.class;
            case "webcam" -> Webcam.class;
            case "soundcard" -> SoundCard.class;
            case "networkcard" -> node.has("wireless") || node.has("wifi")
                    ? WirelessNetworkCard.class : WiredNetworkCard.class;
            case "thermalcompound" -> ThermalCompound.class;
            case "os" -> OperatingSystem.class;
            default -> null;
        };
        if (type == null) return null;
        HardwareSpec<?> spec = type.getDeclaredConstructor().newInstance();
        JsonNode metadata = node.path("metadata");
        String name = text(metadata, "name");
        String manufacturer = text(metadata, "manufacturer");
        if (name.isBlank() || manufacturer.isBlank()) return null;
        spec.setManufacturer(manufacturer);
        spec.setModel(name);
        addIdentifiers(spec, node.path("identifiers").path("identifiers"));
        addStrings(spec, metadata.path("part_numbers"), false);
        String releaseYear = text(metadata, "releaseYear");
        if (!releaseYear.isBlank()) {
            try { spec.setLaunchDate(LocalDate.of(Integer.parseInt(releaseYear), 1, 1)); } catch (RuntimeException ignored) { }
        }
        mapDetails(category, node, spec);
        return spec;
    }

    /**
     * The OpenDB schemas are the source contract.  We intentionally keep the
     * validator dependency-free: all schemas use draft-07 and the importer
     * needs the required-field/type checks, not a second object model.
     */
    private void validateAgainstSchema(Path checkout, String category, JsonNode record) throws IOException {
        Path schemaFile = properties.schemaDirectory().isAbsolute()
                ? properties.schemaDirectory().resolve(category + ".schema.json")
                : checkout.resolve(properties.schemaDirectory()).resolve(category + ".schema.json");
        if (!Files.isRegularFile(schemaFile)) return; // older checkouts may omit schemas
        JsonNode schema = objectMapper.readTree(schemaFile.toFile());
        validateRequired(schema, record, "$" + category);
    }

    private void validateRequired(JsonNode schema, JsonNode value, String path) throws IOException {
        if (schema.path("type").isTextual() && "object".equals(schema.path("type").asText()) && !value.isObject()) {
            throw new IOException(path + " must be an object");
        }
        JsonNode required = schema.path("required");
        if (required.isArray() && value.isObject()) {
            for (JsonNode field : required) {
                if (!value.has(field.asText()) || value.get(field.asText()).isMissingNode()) {
                    throw new IOException(path + " is missing required field " + field.asText());
                }
            }
        }
        JsonNode properties = schema.path("properties");
        if (!properties.isObject() || !value.isObject()) return;
        properties.fields().forEachRemaining(entry -> {
            JsonNode child = value.get(entry.getKey());
            if (child == null || child.isNull()) return;
            try {
                validateRequired(entry.getValue(), child, path + "." + entry.getKey());
            } catch (IOException e) {
                throw new SchemaValidationException(e);
            }
        });
    }

    private static final class SchemaValidationException extends RuntimeException {
        SchemaValidationException(IOException cause) { super(cause); }
    }

    private void addIdentifiers(HardwareSpec<?> spec, JsonNode identifiers) {
        if (!identifiers.isArray()) return;
        for (JsonNode identifier : identifiers) {
            String type = text(identifier, "type").toLowerCase(Locale.ROOT);
            String value = text(identifier, "value");
            if (value.isBlank()) continue;
            if (type.equals("ean") || type.equals("gtin") || type.equals("upc")) spec.addEAN(value);
            else if (type.equals("mpn")) spec.addMPN(value);
        }
    }

    private void addStrings(HardwareSpec<?> spec, JsonNode values, boolean ean) {
        if (!values.isArray()) return;
        values.forEach(v -> { if (!v.asText().isBlank()) { if (ean) spec.addEAN(v.asText()); else spec.addMPN(v.asText()); } });
    }

    private void mapDetails(String category, JsonNode n, HardwareSpec<?> raw) {
        switch (category.toLowerCase(Locale.ROOT)) {
            case "cpu" -> mapCpu(n, (CPU) raw);
            case "gpu" -> mapGpu(n, (GPU) raw);
            case "ram" -> mapRam(n, (RAM) raw);
            case "storage" -> mapStorage(n, (Storage) raw);
            case "motherboard" -> mapMotherboard(n, (Motherboard) raw);
            case "psu" -> mapPsu(n, (PSU) raw);
            case "pccase" -> mapCase(n, (PCCase) raw);
            case "cpucooler" -> mapCooler(n, (CPUCooler) raw);
            case "casefan" -> mapFan(n, (Fan) raw);
            case "monitor" -> mapMonitor(n, (Display) raw);
            default -> { if (raw instanceof CatalogAccessory accessory) mapAccessory(n, accessory); }
        }
    }

    /**
     * Accessory schemas intentionally evolve faster than the relational model.
     * CatalogAccessory therefore stores every declared OpenDB field losslessly
     * as a string while identity and metadata remain first-class fields.
     */
    private void mapAccessory(JsonNode n, CatalogAccessory<?> accessory) {
        n.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (key.equals("opendb_id") || key.equals("metadata") || key.equals("identifiers")) return;
            accessory.getSpecifications().put(key, entry.getValue().isValueNode()
                    ? entry.getValue().asText("") : entry.getValue().toString());
        });
    }

    private void mapCpu(JsonNode n, CPU cpu) {
        cpu.setSocket(enumValue(HardwareTypes.CpuSocket.class, text(n, "socket"), HardwareTypes.CpuSocket.UNKNOWN));
        cpu.setCodeName(text(n, "microarchitecture"));
        cpu.setCores(intAt(n, "cores", "total"));
        cpu.setPerformanceCores(intAt(n, "cores", "performance"));
        cpu.setEfficiencyCores(intAt(n, "cores", "efficiency"));
        cpu.setThreads(intAt(n, "cores", "threads"));
        cpu.setBaseClockMhz(numberAt(n, "clocks", "performance", "base"));
        cpu.setBoostClockMhz(numberAt(n, "clocks", "performance", "boost"));
        cpu.setL3CacheMb(intAt(n, "cache", "l3"));
        cpu.setTdpWatts(intAt(n, "specifications", "tdp"));
        cpu.setIntegratedGraphics(text(n.path("specifications").path("integratedGraphics"), "model"));
        cpu.setMemoryChannels(intAt(n, "specifications", "memory", "channels"));
        cpu.setMemoryType(enumValue(HardwareTypes.RamType.class, text(n.path("specifications").path("memory"), "types", 0), HardwareTypes.RamType.UNKNOWN));
    }

    private void mapGpu(JsonNode n, GPU gpu) {
        gpu.setGpuCanonicalName(text(n, "chipset"));
        gpu.setVramGb(number(n, "memory"));
        gpu.setVramType(enumValue(HardwareTypes.VRAM_TYPE.class, text(n, "memory_type"), HardwareTypes.VRAM_TYPE.UNKNOWN));
        gpu.setBaseClockMhz(number(n, "core_base_clock"));
        gpu.setBoostClockMhz(number(n, "core_boost_clock"));
        gpu.setTdp(number(n, "tdp"));
        gpu.setLengthMm(number(n, "length"));
        gpu.setSlotWidth(number(n, "total_slot_width"));
        gpu.setMemoryBusWidthBit(intValue(n, "memory_bus"));
    }

    private void mapRam(JsonNode n, RAM ram) {
        ram.setType(enumValue(HardwareTypes.RamType.class, text(n, "memory_type"), HardwareTypes.RamType.UNKNOWN));
        ram.setSizeGb(intAt(n, "modules", "capacity_gb"));
        ram.setSpeedMtps(intValue(n, "speed"));
        ram.setSticks(intAt(n, "modules", "quantity"));
        ram.setCasLatency(intValue(n, "cas_latency"));
        ram.setFormFactor(enumValue(HardwareTypes.RamFormFactor.class, text(n, "form_factor").contains("SODIMM") ? "SODIMM" : "DIMM", HardwareTypes.RamFormFactor.UNKNOWN));
    }

    private void mapStorage(JsonNode n, Storage storage) {
        storage.setCapacityGb(intValue(n, "capacity"));
        storage.setReadSpeedMbs(intValue(n, "sequential_read"));
        storage.setWriteSpeedMbs(intValue(n, "sequential_write"));
        storage.setStorageType(enumValue(HardwareTypes.StorageType.class, text(n, "storage_type"), HardwareTypes.StorageType.UNKNOWN));
        storage.setStorageInterface(enumValue(HardwareTypes.StorageInterface.class, text(n, "interface"), HardwareTypes.StorageInterface.UNKNOWN));
        storage.setStorageFormFactor(enumValue(HardwareTypes.StorageFormFactor.class, text(n, "form_factor"), HardwareTypes.StorageFormFactor.UNKNOWN));
    }

    private void mapMotherboard(JsonNode n, Motherboard board) {
        board.setSocket(enumValue(HardwareTypes.CpuSocket.class, text(n, "socket"), HardwareTypes.CpuSocket.UNKNOWN));
        board.setChipset(enumValue(HardwareTypes.Chipset.class, text(n, "chipset").replaceFirst("(?i)^(Intel|AMD)\\s+", ""), HardwareTypes.Chipset.UNKNOWN));
        board.setFormFactor(enumValue(HardwareTypes.MotherboardFormFactor.class, text(n, "form_factor"), HardwareTypes.MotherboardFormFactor.UNKNOWN));
        board.setRamType(enumValue(HardwareTypes.RamType.class, text(n.path("memory"), "ram_type"), HardwareTypes.RamType.UNKNOWN));
        board.setRamSlots(intAt(n, "memory", "slots"));
        board.setRamCapacity(intAt(n, "memory", "max"));
        board.setSataSlots(intAt(n, "storage_devices", "sata_6_gb_s"));
        board.setWlanStandard(enumValue(HardwareTypes.WifiStandard.class, text(n, "wireless_networking"), HardwareTypes.WifiStandard.UNKNOWN));
        board.setHasBluetooth(n.path("wireless_networking").asText("").toLowerCase(Locale.ROOT).contains("bluetooth"));
        addColors(board, n.path("color"));
    }

    private void mapPsu(JsonNode n, PSU psu) {
        psu.setWattage(intValue(n, "wattage"));
        psu.setSize(enumValue(HardwareTypes.PSUFormFactor.class, text(n, "form_factor"), HardwareTypes.PSUFormFactor.UNKNOWN));
        psu.setEfficiencyRating(enumValue(HardwareTypes.PsuEfficiencyRating.class, text(n, "efficiency_rating").replace("80+", ""), HardwareTypes.PsuEfficiencyRating.UNKNOWN));
        psu.setModularity(enumValue(HardwareTypes.PSU_MODULARITY.class, text(n, "modular"), HardwareTypes.PSU_MODULARITY.UNKNOWN));
        psu.setIsFanless(n.path("fanless").asBoolean(false));
        addColors(psu, n.path("color"));
    }

    private void mapCase(JsonNode n, PCCase pcCase) {
        JsonNode dimensions = n.path("dimensions_mm");
        DimensionsMm d = new DimensionsMm(numberAt(dimensions, "width"), numberAt(dimensions, "height"), numberAt(dimensions, "depth"));
        pcCase.setDimensions(d);
        pcCase.setSizeClass(PCCase.classify(d));
        pcCase.setMaxGpuLengthMm(number(n, "max_video_card_length"));
        pcCase.setMaxCpuCoolerHeightMm(number(n, "max_cpu_cooler_height"));
        for (JsonNode value : n.path("supported_motherboard_form_factors")) {
            pcCase.getMotherboardSupport().add(enumValue(HardwareTypes.MotherboardFormFactor.class, value.asText(), HardwareTypes.MotherboardFormFactor.UNKNOWN));
        }
        pcCase.setHasFrontUsbC(text(n, "front_panel_usb").toLowerCase(Locale.ROOT).contains("usb-c"));
        addColors(pcCase, n.path("color"));
    }

    private void mapCooler(JsonNode n, CPUCooler cooler) {
        cooler.setType(n.path("water_cooled").asBoolean(false) ? HardwareTypes.CoolerType.AIO_LIQUID : HardwareTypes.CoolerType.AIR);
        cooler.setRadiatorLengthMm(number(n, "radiator_size"));
        cooler.setFanRPM((int) average(n, "min_fan_rpm", "max_fan_rpm"));
        cooler.setNoiseLevelDB(average(n, "min_noise_level", "max_noise_level"));
        for (JsonNode value : n.path("cpu_sockets")) {
            cooler.getSupportedSockets().add(enumValue(HardwareTypes.CpuSocket.class, value.asText(), HardwareTypes.CpuSocket.UNKNOWN));
        }
        cooler.setHasRgb(hasLighting(n.path("lighting")));
        addColors(cooler, n.path("color"));
    }

    private void mapFan(JsonNode n, Fan fan) {
        fan.setFanSpec(new FanSpec(intValue(n, "size"), (int) average(n, "min_fan_rpm", "max_fan_rpm"), intValue(n, "quantity")));
        fan.setAirflowCfm(average(n, "min_airflow", "max_airflow"));
        fan.setNoiseLevelDB(average(n, "min_noise_level", "max_noise_level"));
        fan.setStaticPressureMmH2o(number(n, "static_pressure"));
        fan.setConnectorType(n.path("pwm").asBoolean(false) ? HardwareTypes.FanConnectorType.FOUR_PIN_PWM : HardwareTypes.FanConnectorType.THREE_PIN);
        fan.setHasRgb(n.path("led").asBoolean(false));
        addColors(fan, n.path("color"));
    }

    private void mapMonitor(JsonNode n, Display display) {
        display.setInchSize(number(n, "screen_size"));
        display.setRefreshRate(intValue(n, "refresh_rate"));
        display.setDisplayPanel(enumValue(HardwareTypes.DisplayPanel.class, text(n, "panel_type"), HardwareTypes.DisplayPanel.UNKNOWN));
        display.setResponseTimeMS(number(n, "response_time"));
        display.setBrightnessNits(intValue(n, "max_brightness"));
        display.setResWidth(intAt(n, "resolution", "horizontalRes"));
        display.setResHeight(intAt(n, "resolution", "verticalRes"));
        String sync = text(n, "adaptive_sync");
        if (sync.toLowerCase(Locale.ROOT).contains("free")) display.getDisplaySyncs().add(HardwareTypes.DisplaySync.FREE_SYNC);
        if (sync.toLowerCase(Locale.ROOT).contains("g-sync")) display.getDisplaySyncs().add(HardwareTypes.DisplaySync.G_SYNC);
        display.setCurved(text(n.path("metadata"), "name").toLowerCase(Locale.ROOT).contains("curved"));
    }

    private static void addColors(HardwareSpec<?> spec, JsonNode colors) {
        if (!colors.isArray()) return;
        Set<String> values = new LinkedHashSet<>();
        colors.forEach(value -> { if (!value.asText().isBlank()) values.add(value.asText()); });
        if (spec instanceof GPU gpu) gpu.setColors(values);
        else if (spec instanceof RAM ram) ram.setColors(values);
        else if (spec instanceof Motherboard board) board.setColors(values);
        else if (spec instanceof PSU psu) psu.setColors(values);
        else if (spec instanceof PCCase pcCase) pcCase.setColors(values);
        else if (spec instanceof CPUCooler cooler) cooler.setColors(values);
    }

    private static boolean hasLighting(JsonNode lighting) {
        for (JsonNode value : lighting) if (!"none".equalsIgnoreCase(value.asText())) return true;
        return false;
    }

    private static double average(JsonNode n, String first, String second) {
        double a = number(n, first), b = number(n, second);
        return a == 0 ? b : b == 0 ? a : (a + b) / 2d;
    }

    private static String text(JsonNode n, String field, int... index) {
        JsonNode value = n.path(field);
        if (index.length > 0 && value.isArray() && value.size() > index[0]) value = value.get(index[0]);
        return value.isValueNode() ? value.asText("") : "";
    }
    private static int intAt(JsonNode n, String... path) { return (int) numberAt(n, path); }
    private static double numberAt(JsonNode n, String... path) { JsonNode v=n; for(String p:path)v=v.path(p); return v.isNumber()?v.asDouble():0; }
    private static double number(JsonNode n, String field) { return numberAt(n, field); }
    private static int intValue(JsonNode n, String field) { return (int) number(n, field); }
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_').replace('.', '_');
        if (type == HardwareTypes.CpuSocket.class) {
            normalized = normalized.replace("LGA_", "LGA").replace("BGA_", "BGA")
                    .replace("AM3+", "AM3_PLUS").replace("STR", "STR");
        }
        try { return Enum.valueOf(type, normalized); } catch (RuntimeException e) { return fallback; }
    }

    public record ImportResult(boolean enabled, int imported, int invalid, String error,
                               Map<String, Integer> importedByCategory) {
        ImportResult(boolean enabled, int imported, int invalid, String error) {
            this(enabled, imported, invalid, error, Map.of());
        }
        ImportResult(boolean enabled, int imported, int invalid) { this(enabled, imported, invalid, null); }
        static ImportResult disabled() { return new ImportResult(false, 0, 0, null); }
    }

    private record ParsedFile(String category, String fileName, HardwareSpec<?> spec, boolean invalid) { }

    private record ImportSnapshot(Instant finishedAt, ImportResult result) { }

    public record OpenDbStatus(boolean enabled, String repository, String branch, String checkoutDirectory,
                               String schemaDirectory, boolean running, String phase, int processedFiles,
                               int totalFiles, long downloadedBytes, long downloadTotalBytes,
                               long phaseCurrent, long phaseTotal, String currentCategory, String currentFile, Instant startedAt, Instant finishedAt,
                               ImportResult lastImport) {
    }
}
