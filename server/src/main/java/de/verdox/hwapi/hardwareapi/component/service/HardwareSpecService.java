package de.verdox.hwapi.hardwareapi.component.service;

import de.verdox.hwapi.component.repository.*;
import de.verdox.hwapi.io.api.ComponentWebScraper;
import de.verdox.hwapi.model.*;
import de.verdox.hwapi.util.GpuRegexParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class HardwareSpecService implements ComponentWebScraper.ScrapeListener<HardwareSpec<?>> {

    private final Logger LOGGER = Logger.getLogger(HardwareSpecService.class.getName());
    private final HardwareSpecRepository baseRepo;
    private final GPUChipRepository gpuChipRepository;
    private static final Set<String> normalizedManufacturers = new HashSet<>();

    private final Map<Class<? extends HardwareSpec<?>>, HardwareSpecificRepo<? extends HardwareSpec<?>>> repoByType = new HashMap<>();
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

        normalizedManufacturers.addAll(baseRepo.findAllManufacturersNormalized());
        this.validTypes = HardwareTypeUtil.getSupportedSpecTypes().stream().map(Class::getSimpleName).map(String::toLowerCase).collect(Collectors.toSet());
    }

    public Class<? extends HardwareSpec<?>> getType(String type) {
        return HardwareTypeUtil.getSupportedSpecTypes().stream().filter(aClass -> {
            return aClass.getSimpleName().toLowerCase().equals(type);
        }).findFirst().orElse(null);
    }

    public String getTypeAsString(Class<? extends HardwareSpec<?>> type) {
        return HardwareTypeUtil.getSupportedSpecTypes().stream().filter(aClass -> aClass.equals(type)).map(aClass -> aClass.getSimpleName().toLowerCase()).findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<HARDWARE>> Page<HARDWARE> findPage(
            Class<HARDWARE> clazz,
            Pageable pageable
    ) {

        HardwareSpecificRepo<HARDWARE> repo = getRepo(clazz);


        // Schritt 1: IDs holen
        Page<Long> idPage = repo.findPageIds(pageable);
        if (idPage.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<HARDWARE> items = repo.findAllByIdInOrderByIdAsc(idPage.getContent());

        // Schritt 3: Page zusammenbauen
        return new PageImpl<>(items, pageable, idPage.getTotalElements());
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
    public Collection<Class<? extends HardwareSpec<?>>> getSupportedSpecTypes() {
        return HardwareTypeUtil.getSupportedSpecTypes();
    }

    public boolean knowsHardware(String model) {
        return baseRepo.existsByModelIgnoreCase(model);
    }

    /**
     * Findet alle Entities eines konkreten Subtyps.
     */
    @Transactional(readOnly = true)
    public List<HardwareSpec<?>> findByType(Class<? extends HardwareSpec<?>> type) {
        CrudRepository<? extends HardwareSpec<?>, Long> repo = repoByType.get(type);
        if (repo == null) {
            throw new IllegalArgumentException("Kein Repository für Typ: " + type.getSimpleName());
        }
        // Spring gibt Iterable zurück → in List casten
        Iterable<? extends HardwareSpec<?>> all = repo.findAll();
        List<HardwareSpec<?>> result = new ArrayList<>();
        all.forEach(result::add);
        return result;
    }

    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<HARDWARE>> HARDWARE findByEAN(Class<HARDWARE> clazz, String EAN) {
        Optional<HARDWARE> found = getRepo(clazz).findByEan(EAN);
        return found.orElse(null);
    }

    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<HARDWARE>> HARDWARE findByEAN(String EAN) {
        return (HARDWARE) baseRepo.findByEan(EAN).orElse(null);
    }

    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<HARDWARE>> HARDWARE findByEANOrMPN(String input) {
        return (HARDWARE) baseRepo.findByEanOrMpn(input).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<HardwareSpec<?>> findAllByEANOrMPN(List<String> decodedKeys) {
        return baseRepo.findAllByEanOrMpn(decodedKeys);
    }

    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<HARDWARE>> HARDWARE findById(long id) {
        return (HARDWARE) baseRepo.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<HARDWARE>> HARDWARE findByMPN(Class<HARDWARE> clazz, String MPN) {
        Optional<HARDWARE> found = getRepo(clazz).findByMPN(MPN);
        return found.orElse(null);
    }

    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<HARDWARE>> HARDWARE findByMPN(String MPN) {
        return (HARDWARE) baseRepo.findByMPN(MPN).orElse(null);
    }

    @Transactional(readOnly = true)
    public HardwareSpec<?> findAnyByEAN(String EAN) {
        return baseRepo.findByEan(EAN).orElse(null);
    }

    /**
     * Findet alle Entities (alle Subtypen).
     */
    @Transactional(readOnly = true)
    public List<HardwareSpec<?>> findAll() {
        // Variante A: Wenn Basistyp-Repo alle Subtypen kennt (bei JPA-Vererbung üblich):
        // return baseRepo.findAll();

        // Variante B: Aggregation aus allen Subtyp-Repos (robust, falls Basistyp-Repo nicht genutzt werden soll):
        return repoByType.values().stream().flatMap(repo -> {
            List<HardwareSpec<?>> list = new ArrayList<>();
            @SuppressWarnings("unchecked") Iterable<HardwareSpec<?>> it = (Iterable<HardwareSpec<?>>) repo.findAll();
            it.forEach(list::add);
            return list.stream();
        }).collect(Collectors.toList());
    }

    public static boolean sanitizeBeforeSave(HardwareSpec<?> hardwareSpec) {
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

        if (hardwareSpec.getMPNs().isEmpty()) {
            throw new IllegalArgumentException("Hardware mpn cannot be null for " + hardwareSpec.getModel());
        }

        if (hardwareSpec.getManufacturer() == null || hardwareSpec.getManufacturer().isBlank()) {
            throw new IllegalArgumentException("Hardware manufacturer cannot be null for " + hardwareSpec.getModel());
        }


        hardwareSpec.checkIfLegal();
        return true;
    }

    /**
     * Speichert eine Hardware-Instanz im passenden Repository.
     */
    @Transactional
    public void saveHardware(HardwareSpec<?> incoming) {
        normalizedManufacturers.add(incoming.getManufacturer());

        final var eans = incoming.getEANs();
        final var mpns = incoming.getMPNs();
        final boolean hasEans = eans != null && !eans.isEmpty();
        final boolean hasMpns = mpns != null && !mpns.isEmpty();

        Set<HardwareSpec<?>> matches = hasEans || hasMpns
                ? new LinkedHashSet<>(baseRepo.findAllByAnyEanOrMpnExists(eans, mpns, hasEans, hasMpns))
                : new LinkedHashSet<>();

        if (matches.isEmpty()) {
            if (!sanitizeBeforeSave(incoming)) {
                return;
            }
            baseRepo.save(incoming);
            saveWithSpecificRepo(incoming);
            return;
        }

        HardwareSpec<?> target = matches.stream()
                .filter(e -> e.getClass().equals(incoming.getClass()))
                .findFirst()
                .orElse(matches.iterator().next());

        target.tryMerge(incoming);

        for (HardwareSpec<?> other : matches) {
            if (other.getId() != target.getId()) {
                target.tryMerge(other);
                deleteWithBothRepos(other);
            }
        }

        baseRepo.save(target);
        saveWithSpecificRepo(target);
    }


    @Transactional
    public void saveHardwareBatch(Set<? extends HardwareSpec<?>> incomingSet) {
        if (incomingSet == null || incomingSet.isEmpty()) {
            return;
        }

        // 1) Hersteller normalisieren
        for (HardwareSpec<?> incoming : incomingSet) {
            normalizedManufacturers.add(incoming.getManufacturer());
        }

        // 2) Alle EANs/MPNs aus dem ganzen Set sammeln
        Set<String> allEans = new HashSet<>();
        Set<String> allMpns = new HashSet<>();

        for (HardwareSpec<?> incoming : incomingSet) {
            if (incoming.getEANs() != null) {
                allEans.addAll(incoming.getEANs());
            }
            if (incoming.getMPNs() != null) {
                allMpns.addAll(incoming.getMPNs());
            }
        }

        boolean hasEans = !allEans.isEmpty();
        boolean hasMpns = !allMpns.isEmpty();

        // 3) Einmalig alle existierenden Matches laden
        List<HardwareSpec<?>> existingMatches = (hasEans || hasMpns)
                ? baseRepo.findAllByAnyEanOrMpnIn(allEans, allMpns, hasEans, hasMpns)
                : List.of();

        // 4) Index im Speicher aufbauen: EAN/MPN → bestehendes HardwareSpec
        Map<String, HardwareSpec<?>> byEan = new HashMap<>();
        Map<String, HardwareSpec<?>> byMpn = new HashMap<>();

        for (HardwareSpec<?> existing : existingMatches) {
            if (existing.getEANs() != null) {
                for (String ean : existing.getEANs()) {
                    byEan.putIfAbsent(ean, existing);
                }
            }
            if (existing.getMPNs() != null) {
                for (String mpn : existing.getMPNs()) {
                    byMpn.putIfAbsent(mpn, existing);
                }
            }
        }

        // 5) Merging im Speicher
        //    Wichtig: wir wollen auch Duplikate innerhalb des incomingSets zusammenführen.
        Set<HardwareSpec<?>> toPersist = new LinkedHashSet<>(existingMatches);

        for (HardwareSpec<?> incoming : incomingSet) {
            if (!sanitizeBeforeSave(incoming)) {
                continue;
            }

            HardwareSpec<?> target = findTargetForIncoming(incoming, byEan, byMpn);

            if (target == null) {
                // Kein Match in DB oder im Index → neues Objekt
                target = incoming;
                toPersist.add(target);
            } else {
                // Gefundenes Target → mergen
                target.tryMerge(incoming);
            }

            // Index aktualisieren, falls neue EANs/MPNs hinzugekommen sind
            if (target.getEANs() != null) {
                for (String ean : target.getEANs()) {
                    byEan.putIfAbsent(ean, target);
                }
            }
            if (target.getMPNs() != null) {
                for (String mpn : target.getMPNs()) {
                    byMpn.putIfAbsent(mpn, target);
                }
            }
        }

        // 6) Persistieren (generisch + spezifische Repos)
        baseRepo.saveAll(toPersist);
        for (HardwareSpec<?> spec : toPersist) {
            saveWithSpecificRepo(spec);
        }
    }

    // Hilfsfunktion: passend zu deiner Einzellogik
    private HardwareSpec<?> findTargetForIncoming(
            HardwareSpec<?> incoming,
            Map<String, HardwareSpec<?>> byEan,
            Map<String, HardwareSpec<?>> byMpn
    ) {
        // Zuerst über EANs versuchen
        if (incoming.getEANs() != null) {
            for (String ean : incoming.getEANs()) {
                HardwareSpec<?> candidate = byEan.get(ean);
                if (candidate != null && candidate.getClass().equals(incoming.getClass())) {
                    return candidate;
                }
            }
        }

        // Dann über MPNs versuchen
        if (incoming.getMPNs() != null) {
            for (String mpn : incoming.getMPNs()) {
                HardwareSpec<?> candidate = byMpn.get(mpn);
                if (candidate != null && candidate.getClass().equals(incoming.getClass())) {
                    return candidate;
                }
            }
        }

        // Wenn kein Typ-exaktes Match gefunden wurde, kannst du – wie in deiner Methode –
        // bei Bedarf noch einen "irgendein Match" Fallback implementieren.
        return null;
    }


    private void saveWithSpecificRepo(HardwareSpec<?> entity) {
        @SuppressWarnings("unchecked")
        CrudRepository<HardwareSpec<?>, Long> specific =
                (CrudRepository<HardwareSpec<?>, Long>) repoByType.get(entity.getClass());
        if (specific != null) {
            specific.save(entity);
        }
    }

    private void deleteWithBothRepos(HardwareSpec<?> entity) {
        baseRepo.delete(entity);
        @SuppressWarnings("unchecked")
        CrudRepository<HardwareSpec<?>, Long> specific =
                (CrudRepository<HardwareSpec<?>, Long>) repoByType.get(entity.getClass());
        if (specific != null) {
            specific.delete(entity);
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    @Transactional
    public <HARDWARE extends HardwareSpec<HARDWARE>> HARDWARE merge(HARDWARE entity) {
        // Normalisierte Kennungen aus dem eingehenden Entity
        HardwareSpecificRepo<HARDWARE> repo = getRepo(entity.getClass());
        if (repo == null) {
            return null;
        }

        HARDWARE byEan = null, byMpn = null;
        if (!entity.getMPNs().isEmpty()) {
            for (String mpn : entity.getMPNs()) {
                byMpn = repo.findByMPN(mpn).orElse(null);
                if (byMpn != null) {
                    break;
                }
            }
        }

        if (!entity.getEANs().isEmpty()) {
            for (String ean : entity.getMPNs()) {
                byEan = repo.findByEan(ean).orElse(null);
                if (byEan != null) {
                    break;
                }
            }
        }

        // Prüfe, ob mehrere unterschiedliche Treffer existieren
        HARDWARE found = firstNonNull(byEan, byMpn);
        if (found != null) {
            if (byMpn != null && (byMpn.getId() != found.getId())) {
                throw new IllegalStateException("Conflict in mergeAll: EAN/UPC/MPN are referencing distinct data entries.");
            }
            // Domain-spezifisches Merge am Aggregat
            found.merge(entity);
            saveHardware(found);
            return found;
        } else {
            saveHardware(entity);
            return entity;
        }
    }

    @Transactional
    public <HARDWARE extends HardwareSpec<HARDWARE>> List<HARDWARE> mergeAll(Class<HARDWARE> clazz, Collection<HARDWARE> input) {
        HardwareSpecificRepo<HARDWARE> repo = getRepo(clazz);
        if (repo == null) {
            return List.copyOf(input);
        }

        Set<String> eans = new HashSet<>(), mpns = new HashSet<>();
        for (HARDWARE e : input) {
            eans.addAll(e.getEANs());
            mpns.addAll(e.getMPNs());
        }

        Map<String, HARDWARE> byEanMap = repo.findAllByEanIn(eans);
        Map<String, HARDWARE> byMpnMap = repo.findAllByMPNInNormalized(mpns);

        List<HARDWARE> toSave = new ArrayList<>();
        for (HARDWARE e : input) {

            for (String mpn : e.getMPNs()) {
                HARDWARE foundByMpn = notBlank(mpn) ? byMpnMap.get(mpn) : null;

                HARDWARE found = firstNonNull(foundByMpn);
                if (found != null) {
                    if (foundByMpn != null && !(foundByMpn.getId() == found.getId())) {
                        throw new IllegalStateException("Conflict in mergeAll: EAN/UPC/MPN are referencing distinct data entries.");
                    }
                    found.merge(e);
                    if (sanitizeBeforeSave(found)) {
                        toSave.add(found);
                    }
                } else {
                    e.addMPN(mpn);
                    if (sanitizeBeforeSave(e)) {
                        toSave.add(e);
                    }
                }
            }

            for (String ean : e.getEANs()) {
                HARDWARE foundByEan = notBlank(ean) ? byEanMap.get(ean) : null;

                HARDWARE found = firstNonNull(foundByEan);
                if (found != null) {
                    if (foundByEan != null && !(foundByEan.getId() == found.getId())) {
                        throw new IllegalStateException("Conflict in mergeAll: EAN/UPC/MPN are referencing distinct data entries.");
                    }
                    found.merge(e);
                    if (sanitizeBeforeSave(found)) {
                        toSave.add(found);
                    }
                } else {
                    e.addEAN(ean);
                    if (sanitizeBeforeSave(e)) {
                        toSave.add(e);
                    }
                }
            }
        }

        // 4) Bulk-save
        return repo.saveAll(toSave);
    }

    public static Set<String> getAllKnownManufacturers() {
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
        //saveHardware(scrapedHardware);
    }

    @Override
    @Transactional
    public void onScrapeMulti(Set<HardwareSpec<?>> scrapedHardware) {
        LOGGER.info("\tSaving " + scrapedHardware.size() + " hardware specs to database");
        long start = System.currentTimeMillis();
        try {
            saveHardwareBatch(scrapedHardware);
        }
        catch (Throwable ex) {
            ex.printStackTrace();
        }
        LOGGER.info("\tTook " + (System.currentTimeMillis() - start) + " ms");
    }


}
