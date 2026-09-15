package de.verdox.hwapi.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.hwapi.catalog.application.HardwareSpecService;
import de.verdox.hwapi.catalog.domain.*;
import de.verdox.hwapi.catalog.persistence.GPUChipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Portable, mergeable backup format for the hardware catalog. */
@Service
public class HardwareBackupService {
    private static final String FORMAT = "open-hardware-api-hardware-catalog";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_ENTRIES = 100_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 200L * 1024 * 1024;
    private static final Map<String, Class<? extends HardwareSpec<?>>> TYPES = Map.ofEntries(
            Map.entry("cpu", CPU.class), Map.entry("gpu", GPU.class), Map.entry("gpuchip", GPUChip.class),
            Map.entry("ram", RAM.class), Map.entry("cpucooler", CPUCooler.class), Map.entry("pccase", PCCase.class),
            Map.entry("psu", PSU.class), Map.entry("motherboard", Motherboard.class), Map.entry("storage", Storage.class),
            Map.entry("display", Display.class), Map.entry("fan", Fan.class)
    );

    private final HardwareSpecService hardwareSpecService;
    private final GPUChipRepository gpuChipRepository;
    private final ObjectMapper objectMapper;

    public HardwareBackupService(HardwareSpecService hardwareSpecService, GPUChipRepository gpuChipRepository,
                                 ObjectMapper objectMapper) {
        this.hardwareSpecService = hardwareSpecService;
        this.gpuChipRepository = gpuChipRepository;
        this.objectMapper = objectMapper;
    }

    /** Streams one JSON document per hardware entity into a ZIP archive. */
    @Transactional(readOnly = true)
    public void exportTo(OutputStream output) throws IOException {
        List<HardwareSpec<?>> specs = hardwareSpecService.findAll();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("format", FORMAT);
            manifest.put("formatVersion", FORMAT_VERSION);
            manifest.put("createdAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
            manifest.put("entityCount", specs.size());
            manifest.put("description", "One hardware entity per JSON file. File names are derived from the hardware name; IDs are informational and are not reused on import.");
            writeEntry(zip, "manifest.json", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            Set<String> usedFileNames = new HashSet<>();
            for (HardwareSpec<?> spec : specs) {
                String fileName = uniqueFileName(sanitizeFileName(spec.displayName()), usedFileNames);
                writeEntry(zip, "hardware/" + typeName(spec.getClass()) + "/" + fileName + ".json",
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(spec));
            }
        }
    }

    /** Validates the complete archive before merging any entity into the current database. */
    @Transactional
    public ImportResult importBackup(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Bitte eine nicht-leere ZIP-Datei auswählen.");
        Map<String, byte[]> files = readZip(file.getInputStream());
        validateManifest(files.remove("manifest.json"));
        List<ImportedSpec> imported = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String[] parts = entry.getKey().split("/");
            if (parts.length != 3 || !"hardware".equals(parts[0]) || !isHardwareFileName(parts[2])) throw new IllegalArgumentException("Ungültiger Dateipfad im Backup: " + entry.getKey());
            Class<? extends HardwareSpec<?>> type = TYPES.get(parts[1]);
            if (type == null) throw new IllegalArgumentException("Unbekannter Hardwaretyp im Backup: " + parts[1]);
            // Exported entities also contain read-only presentation fields (for example displayPictureUrl).
            // Ignore those while keeping the concrete type constrained by the ZIP path.
            HardwareSpec<?> spec = objectMapper.readerFor(type)
                    .without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(entry.getValue());
            long sourceId = spec.getId();
            spec.setId(0); // source IDs must never collide with local IDs
            imported.add(new ImportedSpec(sourceId, spec));
        }
        Map<Long, GPUChip> chipsBySourceId = new HashMap<>();
        for (ImportedSpec item : imported) {
            if (item.spec() instanceof GPUChip chip) {
                hardwareSpecService.saveHardware(chip);
                gpuChipRepository.findByCanonicalModelIgnoreCase(chip.getCanonicalModel()).ifPresent(saved -> chipsBySourceId.put(item.sourceId(), saved));
            }
        }
        for (ImportedSpec item : imported) {
            if (item.spec() instanceof GPU gpu) {
                GPUChip referencedChip = gpu.getChip();
                gpu.setChip(referencedChip == null ? null : chipsBySourceId.get(referencedChip.getId()));
            }
            if (!(item.spec() instanceof GPUChip)) hardwareSpecService.saveHardware(item.spec());
        }
        return new ImportResult(imported.size(), files.size(), "Backup wurde in die bestehende Datenbank eingefügt.");
    }

    private Map<String, byte[]> readZip(InputStream input) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>(); long totalBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("..") || files.containsKey(name)) throw new IllegalArgumentException("Unsicherer oder doppelter ZIP-Eintrag: " + name);
                if (files.size() >= MAX_ENTRIES) throw new IllegalArgumentException("Das Backup enthält zu viele Dateien.");
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(); byte[] chunk = new byte[8192];
                for (int read; (read = zip.read(chunk)) != -1;) { totalBytes += read; if (totalBytes > MAX_UNCOMPRESSED_BYTES) throw new IllegalArgumentException("Das entpackte Backup ist zu groß."); buffer.write(chunk, 0, read); }
                files.put(name, buffer.toByteArray());
            }
        }
        return files;
    }

    private void validateManifest(byte[] rawManifest) throws IOException {
        if (rawManifest == null) throw new IllegalArgumentException("manifest.json fehlt im Backup.");
        JsonNode manifest = objectMapper.readTree(rawManifest);
        if (!FORMAT.equals(manifest.path("format").asText()) || manifest.path("formatVersion").asInt() != FORMAT_VERSION) throw new IllegalArgumentException("Dieses Backup hat ein nicht unterstütztes Format oder eine nicht unterstützte Version.");
    }

    private String typeName(Class<?> type) {
        return TYPES.entrySet().stream().filter(entry -> entry.getValue().equals(type)).map(Map.Entry::getKey).findFirst().orElseThrow(() -> new IllegalArgumentException("Nicht exportierbarer Hardwaretyp: " + type.getName()));
    }

    private static String uniqueFileName(String baseName, Set<String> usedFileNames) {
        String candidate = baseName;
        int suffix = 2;
        while (!usedFileNames.add(candidate)) {
            candidate = baseName + "-" + suffix++;
        }
        return candidate;
    }

    private static boolean isHardwareFileName(String fileName) {
        if (fileName == null || !fileName.endsWith(".json")) return false;
        String baseName = fileName.substring(0, fileName.length() - ".json".length());
        return !baseName.isBlank()
                && !baseName.equals(".")
                && !baseName.equals("..")
                && !fileName.contains("\\")
                && sanitizeFileName(baseName).equals(baseName);
    }

    private static String sanitizeFileName(String name) {
        String sanitized = name == null ? "" : name
                .replaceAll("[^\\p{L}\\p{N}._-]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^[._-]+|[._-]+$", "");
        return sanitized.isBlank() ? "hardware" : sanitized;
    }

    private void writeEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry(); }
    private record ImportedSpec(long sourceId, HardwareSpec<?> spec) {}
    public record ImportResult(int importedEntities, int filesRead, String message) {}
}
