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

    public String getTypeAsString(Class<? extends HardwareSpec> type) {
        return HardwareTypeUtil.getSupportedSpecTypes().stream().filter(aClass -> aClass.equals(type)).map(aClass -> aClass.getSimpleName().toLowerCase()).findFirst().orElse(null);
    }

    public <HARDWARE extends HardwareSpec<HARDWARE>> HardwareSpecificRepo<HARDWARE> getRepo(Class<HARDWARE> type) {
        if (type == null) return null;
        return (HardwareSpecificRepo<HARDWARE>) repoByType.get(type);
    }

    public Set<String> getAllValidTypes() {
        return validTypes;
    }

    public boolean isValidType(String type) {
        return validTypes.contains(type.toLowerCase());
    }

    public <HARDWARE extends HardwareSpec<HARDWARE>> HARDWARE findByExample(Class<HARDWARE> type, Example<HARDWARE> example) {
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
        } else if (upcValid && mpnValid) {
            return baseRepo.findByMPNIgnoreCaseAndUPCIgnoreCase(MPN, UPC);
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

    @Transactional(readOnly = true)
    public void sanitizeBeforeSave(HardwareSpec<?> hardwareSpec) {
        if (hardwareSpec == null) {
            throw new IllegalArgumentException("hardwareSpec darf nicht null sein");
        }
        // Model normalisieren (Trim + Mehrfachspaces zu einem Space)
        String normalizedModel = normalizeModel(hardwareSpec.getModel());
        if (normalizedModel == null || normalizedModel.isBlank()) {
            throw new IllegalArgumentException("Hardware model cannot be null.");
        }

        hardwareSpec.setModel(normalizedModel);

        if (hardwareSpec.getManufacturer() == null || hardwareSpec.getManufacturer().isBlank()) {
            hardwareSpec.setManufacturer(getAllKnownManufacturers().stream().filter(s -> normalizedModel.toLowerCase().contains(s)).findAny().orElse(null));
        }

        if (hardwareSpec.getManufacturer() == null || hardwareSpec.getManufacturer().isBlank()) {
            throw new IllegalArgumentException("Hardware manufacturer cannot be null for " + hardwareSpec.getModel());
        }
        hardwareSpec.checkIfLegal();
    }

    /**
     * Speichert eine Hardware-Instanz im passenden Repository.
     */
    @Transactional
    public void saveHardware(HardwareSpec<?> hardwareSpec) {
        sanitizeBeforeSave(hardwareSpec);
        Optional<HardwareSpec> opt = findLightByEanMPNUPCSN(hardwareSpec.getClass(), hardwareSpec.getEAN(), hardwareSpec.getMPN(), hardwareSpec.getMPN());
        if (opt.isPresent()) {
            if (opt.getClass().equals(hardwareSpec.getClass())) {
                opt.get().merge(hardwareSpec);
            }
        } else {
            Class<? extends HardwareSpec> actualType = hardwareSpec.getClass();

            @SuppressWarnings("unchecked") CrudRepository<HardwareSpec, Long> repo = (CrudRepository<HardwareSpec, Long>) repoByType.get(actualType);

            baseRepo.save(hardwareSpec);

            if (repo != null) {
                repo.save(hardwareSpec);
            }
        }
    }

    @Transactional
    public <HARDWARE extends HardwareSpec<HARDWARE>> HARDWARE merge(HARDWARE entity) {
        // Normalisierte Kennungen aus dem eingehenden Entity
        final String ean = HardwareSpecificRepo.normalizeEANMPNUPC(entity.getEAN());
        final String upc = HardwareSpecificRepo.normalizeEANMPNUPC(entity.getUPC());
        final String mpn = HardwareSpecificRepo.normalizeEANMPNUPC(entity.getMPN());

        HardwareSpecificRepo<HARDWARE> repo = getRepo(entity.getClass());
        if (repo == null) {
            return null;
        }

        HARDWARE byEan = null, byUpc = null, byMpn = null;

        if (notBlank(ean)) byEan = repo.findByEANIgnoreCase(ean).orElse(null);
        if (notBlank(upc)) byUpc = repo.findByUPCIgnoreCase(upc).orElse(null);
        if (notBlank(mpn)) byMpn = repo.findByMPNIgnoreCase(mpn).orElse(null);

        // Prüfe, ob mehrere unterschiedliche Treffer existieren
        HARDWARE found = firstNonNull(byEan, byUpc, byMpn);
        if (found != null) {
            if ((byEan != null && (byEan.getId() != found.getId()))
                    || (byUpc != null && (byUpc.getId() != found.getId()))
                    || (byMpn != null && (byMpn.getId() != found.getId()))) {
                throw new IllegalStateException("Conflict in mergeAll: EAN/UPC/MPN are referencing distinct data entries.");
            }
            // Domain-spezifisches Merge am Aggregat
            found.merge(entity);
            saveHardware(found);
            return found;
        } else {
            // Vor dem Speichern die normalisierten Kennungen setzen (empfohlen)
            entity.setEAN(ean);
            entity.setUPC(upc);
            entity.setMPN(mpn);
            saveHardware(entity);
            return entity;
        }
    }

    @Transactional
    public <HARDWARE extends HardwareSpec<HARDWARE>> List<HARDWARE> mergeAll(Class<HARDWARE> clazz, Iterable<HARDWARE> entities) {
        List<HARDWARE> input = new ArrayList<>();
        for (HARDWARE e : entities) input.add(e);

        HardwareSpecificRepo<HARDWARE> repo = getRepo(clazz);
        if (repo == null) {
            return input;
        }

        // 1) Alle normalisierten Kennungen einsammeln
        Set<String> eans = new HashSet<>(), upcs = new HashSet<>(), mpns = new HashSet<>();
        for (HARDWARE e : input) {
            String ean = HardwareSpecificRepo.normalizeEANMPNUPC(e.getEAN());
            String upc = HardwareSpecificRepo.normalizeEANMPNUPC(e.getUPC());
            String mpn = HardwareSpecificRepo.normalizeEANMPNUPC(e.getMPN());
            if (notBlank(ean)) eans.add(ean);
            if (notBlank(upc)) upcs.add(upc);
            if (notBlank(mpn)) mpns.add(mpn);
        }

        // 2) Bulk-Fetch aller existierenden Datensätze
        Map<String, HARDWARE> byEanMap = repo.findAllByEANInNormalized(eans); // Map<EAN, HW>
        Map<String, HARDWARE> byUpcMap = repo.findAllByUPCInNormalized(upcs);
        Map<String, HARDWARE> byMpnMap = repo.findAllByMPNInNormalized(mpns);

        // 3) Mergen
        List<HARDWARE> toSave = new ArrayList<>();
        for (HARDWARE e : input) {
            String ean = HardwareSpecificRepo.normalizeEANMPNUPC(e.getEAN());
            String upc = HardwareSpecificRepo.normalizeEANMPNUPC(e.getUPC());
            String mpn = HardwareSpecificRepo.normalizeEANMPNUPC(e.getMPN());

            HARDWARE a = notBlank(ean) ? byEanMap.get(ean) : null;
            HARDWARE b = notBlank(upc) ? byUpcMap.get(upc) : null;
            HARDWARE c = notBlank(mpn) ? byMpnMap.get(mpn) : null;

            HARDWARE found = firstNonNull(a, b, c);
            if (found != null) {
                // Konflikt prüfen
                if ((a != null && !(a.getId() == found.getId()))
                        || (b != null && !(b.getId() == found.getId()))
                        || (c != null && !(c.getId() == found.getId()))) {
                    throw new IllegalStateException("Conflict in mergeAll: EAN/UPC/MPN are referencing distinct data entries.");
                }
                found.merge(e);
                sanitizeBeforeSave(found);
                toSave.add(found);
            } else {
                e.setEAN(ean);
                e.setUPC(upc);
                e.setMPN(mpn);
                sanitizeBeforeSave(e);
                toSave.add(e);
            }
        }

        // 4) Bulk-save
        return repo.saveAll(toSave);
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


    private static <T> T firstNonNull(T... ts) {
        for (T t : ts) if (t != null) return t;
        return null;
    }

    public static boolean notBlank(String s) {
        return s != null && !s.isBlank();
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
