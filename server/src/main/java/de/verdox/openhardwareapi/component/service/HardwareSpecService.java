package de.verdox.openhardwareapi.component.service;

import de.verdox.openhardwareapi.component.repository.*;
import de.verdox.openhardwareapi.io.api.ComponentWebScraper;
import de.verdox.openhardwareapi.model.*;
import de.verdox.openhardwareapi.util.GpuRegexParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HardwareSpecService implements ComponentWebScraper.ScrapeListener<HardwareSpec> {

    private final HardwareSpecRepository baseRepo;
    private final GPUChipRepository gpuChipRepository;
    private final Set<String> normalizedManufacturers = new HashSet<>();

    private final Map<Class<? extends HardwareSpec>, HardwareSpecificRepo<? extends HardwareSpec>> repoByType = new HashMap<>();
    private final Set<String> validTypes;


    @Autowired
    public HardwareSpecService(HardwareSpecRepository baseRepo, CPURepository cpuRepository, CPUCoolerRepository cpuCoolerRepository, GPUChipRepository gpuChipRepository, GPURepository gpuRepository, MotherboardRepository motherboardRepository, PCCaseRepository pcCaseRepository, PSURepository psuRepository, RAMRepository ramRepository, StorageRepository storageRepository, DisplayRepository displayRepository) {
        this.baseRepo = baseRepo;
        this.gpuChipRepository = gpuChipRepository;

        repoByType.put(CPU.class, cpuRepository);
        repoByType.put(CPUCooler.class, cpuCoolerRepository);
        repoByType.put(GPU.class, gpuRepository);
        repoByType.put(GPUChip.class, gpuChipRepository);
        repoByType.put(Motherboard.class, motherboardRepository);
        repoByType.put(PCCase.class, pcCaseRepository);
        repoByType.put(PSU.class, psuRepository);
        repoByType.put(RAM.class, ramRepository);
        repoByType.put(Storage.class, storageRepository);
        repoByType.put(Display.class, displayRepository);

        this.normalizedManufacturers.addAll(baseRepo.findAllManufacturersNormalized());
        this.validTypes = HardwareTypeUtil.getSupportedSpecTypes().stream().map(Class::getSimpleName).map(String::toLowerCase).collect(Collectors.toSet());
    }

    public Class<? extends HardwareSpec> getType(String type) {
        return HardwareTypeUtil.getSupportedSpecTypes().stream().filter(aClass -> {
            return aClass.getSimpleName().toLowerCase().equals(type);
        }).findFirst().orElse(null);
    }

    public <HARDWARE extends HardwareSpec> HardwareSpecificRepo<HARDWARE> getRepo(Class<HARDWARE> type) {
        if (type == null) return null;
        return (HardwareSpecificRepo<HARDWARE>) repoByType.get(type);
    }

    public Set<String> getAllValidTypes() {
        return validTypes;
    }

    public boolean isValidType(String type) {
        return validTypes.contains(type.toLowerCase());
    }

    public <HARDWARE extends HardwareSpec> HARDWARE findByExample(Class<HARDWARE> type, Example<HARDWARE> example) {
        HardwareSpecificRepo<HARDWARE> repository = (HardwareSpecificRepo<HARDWARE>) repoByType.get(type);
        if (repository == null) return null;

        return repository.findAll(example).getFirst();
    }

    @Transactional(readOnly = true)
    public Optional<GPUChip> findGPUModel(GpuRegexParser.ParsedGpu parsedGpu) {
        try {
            return gpuChipRepository.findByCanonicalModelIgnoreCase(parsedGpu.canonical());
        } catch (Throwable e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<HardwareSpec> findLightByEanMPNUPCSN(String input) {
        input = input.isBlank() ? "---" : input;
        return baseRepo.findByEANIgnoreCaseOrMPNIgnoreCaseOrUPCIgnoreCase(input, input, input);
    }

    @Transactional(readOnly = true)
    public Optional<HardwareSpec> findLightByEanMPNUPCSN(Class<? extends HardwareSpec> type, String EAN, String UPC, String MPN) {

        boolean eanValid = EAN != null && !EAN.isBlank();
        boolean upcValid = UPC != null && !UPC.isBlank();
        boolean mpnValid = MPN != null && !MPN.isBlank();

        if (eanValid && upcValid && mpnValid) {
            return baseRepo.findByEANIgnoreCaseAndMPNIgnoreCaseAndUPCIgnoreCase(EAN, MPN, UPC);
        } else if (eanValid && upcValid) {
            return baseRepo.findByEANIgnoreCaseAndUPCIgnoreCase(EAN, UPC);
        } else if (eanValid && mpnValid) {
            return baseRepo.findByEANIgnoreCaseAndMPNIgnoreCase(EAN, MPN);
        } else if (eanValid) {
            return baseRepo.findByEANIgnoreCase(EAN);
        } else if (upcValid) {
            return baseRepo.findByUPCIgnoreCase(UPC);
        } else if (mpnValid) {
            return baseRepo.findByMPNIgnoreCase(MPN);
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<GPUChip> findGPUModel(GpuRegexParser.ParsedGpu parsedGpu, HardwareTypes.VRAM_TYPE vramType) {
        try {
            return gpuChipRepository.findFirstByCanonicalModelIgnoreCaseAndVramType(parsedGpu.canonical(), vramType);
        } catch (Throwable e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<GPUChip> findGPUModel(GpuRegexParser.ParsedGpu parsedGpu, HardwareTypes.VRAM_TYPE vramType, double vramGb) {
        try {
            return gpuChipRepository.findFirstByCanonicalModelIgnoreCaseAndVramTypeAndVramGb(parsedGpu.canonical(), vramType, vramGb);
        } catch (Throwable e) {
            return Optional.empty();
        }
    }

    /**
     * Liefert alle unterstützten Hardware-Typen (aus eurer Utility).
     */
    public Collection<Class<? extends HardwareSpec>> getSupportedSpecTypes() {
        return HardwareTypeUtil.getSupportedSpecTypes();
    }

    public boolean knowsHardware(String model) {
        return baseRepo.existsByModelIgnoreCase(model);
    }

    /**
     * Findet alle Entities eines konkreten Subtyps.
     */
    @Transactional(readOnly = true)
    public List<HardwareSpec> findByType(Class<? extends HardwareSpec> type) {
        CrudRepository<? extends HardwareSpec, Long> repo = repoByType.get(type);
        if (repo == null) {
            throw new IllegalArgumentException("Kein Repository für Typ: " + type.getSimpleName());
        }
        // Spring gibt Iterable zurück → in List casten
        Iterable<? extends HardwareSpec> all = repo.findAll();
        List<HardwareSpec> result = new ArrayList<>();
        all.forEach(result::add);
        return result;
    }

    @Transactional(readOnly = true)
    public HardwareSpec findByEAN(String EAN) {
        return baseRepo.findByEANIgnoreCase(EAN).orElse(null);
    }

    /**
     * Findet alle Entities (alle Subtypen).
     */
    @Transactional(readOnly = true)
    public List<HardwareSpec> findAll() {
        // Variante A: Wenn Basistyp-Repo alle Subtypen kennt (bei JPA-Vererbung üblich):
        // return baseRepo.findAll();

        // Variante B: Aggregation aus allen Subtyp-Repos (robust, falls Basistyp-Repo nicht genutzt werden soll):
        return repoByType.values().stream().flatMap(repo -> {
            List<HardwareSpec> list = new ArrayList<>();
            @SuppressWarnings("unchecked") Iterable<HardwareSpec> it = (Iterable<HardwareSpec>) repo.findAll();
            it.forEach(list::add);
            return list.stream();
        }).collect(Collectors.toList());
    }

    /**
     * Speichert eine Hardware-Instanz im passenden Repository.
     */
    @Transactional
    public void saveHardware(HardwareSpec hardwareSpec) {
        if (hardwareSpec == null) {
            throw new IllegalArgumentException("hardwareSpec darf nicht null sein");
        }
        // Model normalisieren (Trim + Mehrfachspaces zu einem Space)
        String normalizedModel = normalizeModel(hardwareSpec.getModel());
        if (normalizedModel == null || normalizedModel.isBlank()) {
            throw new IllegalArgumentException("Hardware model cannot be null.");
        }

        if (findLightByEanMPNUPCSN(hardwareSpec.getClass(), hardwareSpec.getEAN(), hardwareSpec.getMPN(), hardwareSpec.getMPN()).isPresent()) {
            return;
        }

        hardwareSpec.setModel(normalizedModel);

        if (hardwareSpec.getManufacturer() == null || hardwareSpec.getManufacturer().isBlank()) {
            hardwareSpec.setManufacturer(getAllKnownManufacturers().stream().filter(s -> normalizedModel.toLowerCase().contains(s)).findAny().orElse(null));
        }

        if (hardwareSpec.getManufacturer() == null || hardwareSpec.getManufacturer().isBlank()) {
            throw new IllegalArgumentException("Hardware manufacturer cannot be null for " + hardwareSpec.getModel());
        }
        hardwareSpec.checkIfLegal();

        Class<? extends HardwareSpec> actualType = hardwareSpec.getClass();

        @SuppressWarnings("unchecked") CrudRepository<HardwareSpec, Long> repo = (CrudRepository<HardwareSpec, Long>) repoByType.get(actualType);

        baseRepo.save(hardwareSpec);

        if (repo != null) {
            repo.save(hardwareSpec);
        }
    }

    public Set<String> getAllKnownManufacturers() {
        return normalizedManufacturers;
    }

    public static String normalizeModel(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC);
        n = n.replace('\u00A0', ' ')
                .replace('\u2007', ' ')
                .replace('\u202F', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return n;
    }

    @Override
    @Transactional
    public void onScrape(HardwareSpec scrapedHardware) {
        if (scrapedHardware.getManufacturer() != null) {
            normalizedManufacturers.add(scrapedHardware.getManufacturer().trim().toLowerCase(Locale.ROOT));
        }
        saveHardware(scrapedHardware);
    }
}
