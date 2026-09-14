package de.verdox.hwapi.catalog.application;

import de.verdox.hwapi.catalog.persistence.*;
import de.verdox.hwapi.catalog.ingestion.ScrapingService;
import de.verdox.hwapi.catalog.ingestion.api.ComponentWebScraper;
import de.verdox.hwapi.catalog.domain.*;
import de.verdox.hwapi.identity.application.ProductRegistryService;
import de.verdox.hwapi.catalog.support.GpuRegexParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.text.Normalizer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class HardwareSpecService implements ComponentWebScraper.ScrapeListener<HardwareSpec<?>> {

    private final Logger LOGGER = Logger.getLogger(HardwareSpecService.class.getName());
    private final HardwareSpecRepository baseRepo;
    private final GPUChipRepository gpuChipRepository;

    /**
     * FIX: RAM leak
     * - not static (static would grow JVM-wide forever)
     * - bounded LRU (prevents unbounded growth)
     * - store normalized values (lowercase/trim)
     */
    private static final int MAX_MANUFACTURER_CACHE_SIZE = 10_000;
    private final Set<String> normalizedManufacturers =
            Collections.newSetFromMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_MANUFACTURER_CACHE_SIZE;
                }
            });

    private final Map<Class<? extends HardwareSpec<?>>, HardwareSpecificRepo<? extends HardwareSpec<?>>> repoByType = new HashMap<>();
    private final Set<String> validTypes;
    private final ProductRegistryService productRegistryService;
    private final HardwareSpecCache cache;

    @Autowired
    public HardwareSpecService(
            HardwareSpecCache cache,
            HardwareSpecRepository baseRepo,
            CPURepository cpuRepository,
            CPUCoolerRepository cpuCoolerRepository,
            GPUChipRepository gpuChipRepository,
            GPURepository gpuRepository,
            MotherboardRepository motherboardRepository,
            PCCaseRepository pcCaseRepository,
            PSURepository psuRepository,
            RAMRepository ramRepository,
            StorageRepository storageRepository,
            DisplayRepository displayRepository,
            FanRepository fanRepository,
            ProductRegistryService productRegistryService
    ) {
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
        repoByType.put(Fan.class, fanRepository);

        this.cache = cache;

        // load normalized manufacturers (defensive: normalize again)
        try {
            baseRepo.findAllManufacturersNormalized().forEach(this::rememberManufacturer);
        } catch (Throwable ignored) {
        }

        this.validTypes = HardwareTypeUtil.getSupportedSpecTypes().stream()
                .map(Class::getSimpleName)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        this.productRegistryService = productRegistryService;
    }

    public Class<? extends HardwareSpec<?>> getType(String type) {
        return HardwareTypeUtil.getSupportedSpecTypes().stream()
                .filter(aClass -> aClass.getSimpleName().toLowerCase().equals(type))
                .findFirst()
                .orElse(null);
    }

    public String getTypeAsString(Class<? extends HardwareSpec<?>> type) {
        return HardwareTypeUtil.getSupportedSpecTypes().stream()
                .filter(aClass -> aClass.equals(type))
                .map(aClass -> aClass.getSimpleName().toLowerCase())
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<HARDWARE>> Page<HARDWARE> findPage(
            Class<HARDWARE> clazz,
            Pageable pageable
    ) {
        HardwareSpecificRepo<HARDWARE> repo = getRepo(clazz);

        Page<Long> idPage = repo.findPageIds(pageable);
        if (idPage.isEmpty()) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }

        List<HARDWARE> items = repo.findAllByIdInOrderByIdAsc(idPage.getContent());
        return new PageImpl<>(items, pageable, idPage.getTotalElements());
    }

    @SuppressWarnings("unchecked")
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

    @Transactional(readOnly = true)
    public long countAllHardware() {
        return baseRepo.count();
    }

    /**
     * JOINED inheritance:
     * Delete children first (type repos), parent last (base repo) to avoid FK issues.
     * Also: clear cache after commit.
     */
    @Transactional
    public void deleteAllHardwareData() {
        for (var repo : repoByType.values()) {
            repo.deleteAll();
        }
        baseRepo.deleteAll();

        afterCommit(cache::clear);
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

    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<HARDWARE>> HARDWARE findByEAN(Class<HARDWARE> clazz, String EAN) {
        String normalized = HardwareSpec.normalizeEan(EAN);
        if (normalized == null) return null;
        return getRepo(clazz).findByEan(normalized).orElse(null);
    }

    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<HARDWARE>> HARDWARE findByEANOrMPN(String input) {
        if (input == null || input.isBlank()) return null;
        HardwareSpec<?> cached = cache.getByKey(input);
        if (cached != null) {
            return (HARDWARE) cached;
        }

        String normalizedEan = HardwareSpec.normalizeEan(input);
        String normalizedMpn = HardwareSpec.normalizeMpn(input);
        String normalized = normalizedEan != null ? normalizedEan : normalizedMpn;
        if (normalized == null) return null;
        HARDWARE fromDb = (HARDWARE) baseRepo.findByEanOrMpn(normalized).orElse(null);
        if (fromDb != null) {
            cache.put(fromDb);
        }
        return fromDb;
    }

    @Transactional(readOnly = true)
    public List<HardwareSpec<?>> findAllByEANOrMPN(List<String> decodedKeys) {
        if (decodedKeys == null || decodedKeys.isEmpty()) {
            return List.of();
        }

        final int KEY_CHUNK = 100;

        // Ergebnis dedupen (ein Spec kann über mehrere Keys gefunden werden)
        Map<Long, HardwareSpec<?>> resultById = new LinkedHashMap<>();

        // Misses sammeln
        List<String> misses = new ArrayList<>(decodedKeys.size());

        for (String key : decodedKeys) {
            if (key == null || key.isBlank()) continue;

            HardwareSpec<?> cached = cache.getByKey(key);
            if (cached != null) {
                resultById.putIfAbsent(cached.getId(), cached);
            } else {
                misses.add(key);
            }
        }

        if (misses.isEmpty()) {
            return new ArrayList<>(resultById.values());
        }

        // DB nur für Misses, gechunked
        for (int i = 0; i < misses.size(); i += KEY_CHUNK) {
            int end = Math.min(i + KEY_CHUNK, misses.size());
            List<String> chunk = misses.subList(i, end);

            List<HardwareSpec<?>> found = baseRepo.findAllByEanOrMpn(chunk);
            for (HardwareSpec<?> spec : found) {
                if (spec == null) continue;
                resultById.putIfAbsent(spec.getId(), spec);
                cache.put(spec);
            }
        }

        return new ArrayList<>(resultById.values());
    }

    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<HARDWARE>> HARDWARE findById(long id) {
        HardwareSpec<?> cached = cache.getById(id);
        if (cached != null) {
            return (HARDWARE) cached;
        }
        HARDWARE fromDb = (HARDWARE) baseRepo.findById(id).orElse(null);
        if (fromDb != null) {
            cache.put(fromDb);
        }
        return fromDb;
    }

    @Transactional(readOnly = true)
    public <HARDWARE extends HardwareSpec<HARDWARE>> HARDWARE findByMPN(String MPN) {
        String normalized = HardwareSpec.normalizeMpn(MPN);
        return normalized == null ? null : (HARDWARE) baseRepo.findByMPN(normalized).orElse(null);
    }

    @Transactional(readOnly = true)
    public HardwareSpec<?> findByMPN(Class<? extends HardwareSpec<?>> clazz, String mpn) {
        String normalized = HardwareSpec.normalizeMpn(mpn);
        if (normalized == null) return null;
        @SuppressWarnings("rawtypes")
        HardwareSpecificRepo repo = repoByType.get(clazz);
        return repo == null ? null : (HardwareSpec<?>) repo.findByMPN(normalized).orElse(null);
    }

    @Transactional(readOnly = true)
    public HardwareSpec<?> findAnyByEAN(String EAN) {
        String normalized = HardwareSpec.normalizeEan(EAN);
        return normalized == null ? null : baseRepo.findByEan(normalized).orElse(null);
    }

    /**
     * Findet alle Entities (alle Subtypen) über die Subtyp-Repos,
     * damit EntityGraphs korrekt greifen (JOINED + Graphs je Subrepo).
     */
    @Transactional(readOnly = true)
    public List<HardwareSpec<?>> findAll() {
        return repoByType.values().stream()
                .flatMap(repo -> {
                    List<HardwareSpec<?>> list = new ArrayList<>();
                    @SuppressWarnings("unchecked")
                    Iterable<HardwareSpec<?>> it = (Iterable<HardwareSpec<?>>) repo.findAll();
                    it.forEach(list::add);
                    return list.stream();
                })
                .collect(Collectors.toList());
    }

    /**
     * Instance method now (not static), uses bounded manufacturer cache.
     */
    public boolean sanitizeBeforeSave(HardwareSpec<?> hardwareSpec) {
        if (hardwareSpec == null) {
            throw new IllegalArgumentException("hardwareSpec darf nicht null sein");
        }

        String normalizedModel = normalizeModel(hardwareSpec.getModel());
        if (hardwareSpec.getModel() == null || hardwareSpec.getModel().isBlank()
                || normalizedModel == null || normalizedModel.isBlank()) {
            ScrapingService.LOGGER.log(Level.FINE, "Hardware model cannot be null.");
            return false;
        }

        hardwareSpec.setModel(normalizedModel);

        if (hardwareSpec.getManufacturer() == null || hardwareSpec.getManufacturer().isBlank()) {
            String lc = normalizedModel.toLowerCase(Locale.ROOT);
            hardwareSpec.setManufacturer(
                    normalizedManufacturers.stream()
                            .filter(lc::contains)
                            .findAny()
                            .orElse(null)
            );
        }

        if (hardwareSpec.getMPNs().isEmpty()) {
            ScrapingService.LOGGER.log(Level.FINE, "Hardware mpn cannot be null for " + hardwareSpec.getModel());
            return false;
        }

        if (hardwareSpec.getManufacturer() == null || hardwareSpec.getManufacturer().isBlank()) {
            ScrapingService.LOGGER.log(Level.FINE, "Hardware manufacturer cannot be null for " + hardwareSpec.getModel());
            return false;
        }

        hardwareSpec.checkIfLegal();
        return true;
    }

    /**
     * Speichert eine Hardware-Instanz im passenden Repository.
     * JOINED: baseRepo speichert Parent; specificRepo speichert Child.
     * Cache wird nach Commit aktualisiert.
     */
    @Transactional
    public void saveHardware(HardwareSpec<?> incoming) {
        if (incoming == null) return;

        rememberManufacturer(incoming.getManufacturer());

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

            afterCommit(() -> cache.put(incoming));
            return;
        }

        Set<HardwareSpec<?>> sameTypeMatches = matches.stream()
                .filter(e -> e.getClass().equals(incoming.getClass()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (sameTypeMatches.isEmpty()) {
            LOGGER.warning("Identifier conflict across hardware types; refusing to merge " + incoming.displayName());
            return;
        }

        HardwareSpec<?> target = sameTypeMatches.iterator().next();

        target.tryMerge(incoming);

        for (HardwareSpec<?> other : sameTypeMatches) {
            if (other.getId() != target.getId()) {
                target.tryMerge(other);
                deleteWithBothRepos(other);
                afterCommit(() -> cache.evict(other));
            }
        }

        // BUGFIX: sanitize target, not incoming
        if (!sanitizeBeforeSave(target)) {
            return;
        }

        baseRepo.save(target);
        saveWithSpecificRepo(target);

        afterCommit(() -> cache.put(target));
    }

    @Transactional
    public void saveHardwareBatch(Set<? extends HardwareSpec<?>> incomingSet) {
        if (incomingSet == null || incomingSet.isEmpty()) {
            return;
        }

        final int SPEC_CHUNK = 100;
        final int KEY_CHUNK  = 100;

        final Map<Long, HardwareSpec<?>> targetsById = new LinkedHashMap<>();
        final Map<String, HardwareSpec<?>> byEan = new HashMap<>();
        final Map<String, HardwareSpec<?>> byMpn = new HashMap<>();

        List<HardwareSpec<?>> batch = new ArrayList<>(SPEC_CHUNK);
        for (HardwareSpec<?> incoming : incomingSet) {
            rememberManufacturer(incoming.getManufacturer());
            batch.add(incoming);

            if (batch.size() >= SPEC_CHUNK) {
                processIncomingChunk(batch, targetsById, byEan, byMpn, KEY_CHUNK);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            processIncomingChunk(batch, targetsById, byEan, byMpn, KEY_CHUNK);
        }
    }

    private void processIncomingChunk(
            List<HardwareSpec<?>> incomingChunk,
            Map<Long, HardwareSpec<?>> targetsById,
            Map<String, HardwareSpec<?>> byEan,
            Map<String, HardwareSpec<?>> byMpn,
            int keyChunkSize
    ) {
        Set<String> eans = new HashSet<>();
        Set<String> mpns = new HashSet<>();

        for (HardwareSpec<?> in : incomingChunk) {
            if (in.getEANs() != null) {
                for (String e : in.getEANs()) {
                    String ne = HardwareSpec.normalizeEan(e);
                    if (ne != null) eans.add(ne);
                }
            }
            if (in.getMPNs() != null) {
                for (String m : in.getMPNs()) {
                    String nm = HardwareSpec.normalizeMpn(m);
                    if (nm != null) mpns.add(nm);
                }
            }
        }

        if (!eans.isEmpty() || !mpns.isEmpty()) {
            List<HardwareSpec<?>> existingMatches = findExistingMatchesChunked(eans, mpns, keyChunkSize);

            for (HardwareSpec<?> ex : existingMatches) {
                HardwareSpec<?> already = targetsById.putIfAbsent(ex.getId(), ex);
                HardwareSpec<?> target = (already != null) ? already : ex;

                indexKeys(target, byEan, byMpn);
            }
        }

        Set<HardwareSpec<?>> toPersist = new LinkedHashSet<>();

        for (HardwareSpec<?> incoming : incomingChunk) {
            if (!sanitizeBeforeSave(incoming)) {
                continue;
            }

            HardwareSpec<?> target = findTargetForIncoming(incoming, byEan, byMpn);

            if (target == null) {
                target = incoming;
                if (!sanitizeBeforeSave(target)) {
                    continue;
                }
                toPersist.add(target);
                indexKeys(target, byEan, byMpn);
            } else {
                target.tryMerge(incoming);

                if (!sanitizeBeforeSave(target)) {
                    continue;
                }
                toPersist.add(target);

                indexKeys(target, byEan, byMpn);
            }
        }

        if (toPersist.isEmpty()) {
            return;
        }

        baseRepo.saveAll(toPersist);
        for (HardwareSpec<?> spec : toPersist) {
            saveWithSpecificRepo(spec);
            if (spec.getId() != 0) {
                targetsById.putIfAbsent(spec.getId(), spec);
            }
        }

        Set<HardwareSpec<?>> persistedSnapshot = new LinkedHashSet<>(toPersist);
        afterCommit(() -> {
            for (HardwareSpec<?> spec : persistedSnapshot) {
                cache.put(spec);
            }
        });
    }

    private List<HardwareSpec<?>> findExistingMatchesChunked(Set<String> eans, Set<String> mpns, int chunkSize) {
        Map<Long, HardwareSpec<?>> byId = new LinkedHashMap<>();

        if (eans != null && !eans.isEmpty()) {
            List<String> list = new ArrayList<>(eans);
            for (int i = 0; i < list.size(); i += chunkSize) {
                int end = Math.min(i + chunkSize, list.size());
                Set<String> eChunk = new HashSet<>(list.subList(i, end));

                List<HardwareSpec<?>> found = baseRepo.findAllByAnyEanOrMpnIn(
                        eChunk,
                        Collections.emptySet(),
                        true,
                        false
                );
                for (HardwareSpec<?> s : found) {
                    byId.putIfAbsent(s.getId(), s);
                }
            }
        }

        if (mpns != null && !mpns.isEmpty()) {
            List<String> list = new ArrayList<>(mpns);
            for (int i = 0; i < list.size(); i += chunkSize) {
                int end = Math.min(i + chunkSize, list.size());
                Set<String> mChunk = new HashSet<>(list.subList(i, end));

                // nur MPNs
                List<HardwareSpec<?>> found = baseRepo.findAllByAnyEanOrMpnIn(
                        Collections.emptySet(),
                        mChunk,
                        false,
                        true
                );
                for (HardwareSpec<?> s : found) {
                    byId.putIfAbsent(s.getId(), s);
                }
            }
        }

        return new ArrayList<>(byId.values());
    }

    private void indexKeys(HardwareSpec<?> spec,
                           Map<String, HardwareSpec<?>> byEan,
                           Map<String, HardwareSpec<?>> byMpn) {
        if (spec == null) return;

        if (spec.getEANs() != null) {
            for (String e : spec.getEANs()) {
                String ne = HardwareSpec.normalizeEan(e);
                if (ne != null) byEan.putIfAbsent(ne, spec);
            }
        }
        if (spec.getMPNs() != null) {
            for (String m : spec.getMPNs()) {
                String nm = HardwareSpec.normalizeMpn(m);
                if (nm != null) byMpn.putIfAbsent(nm, spec);
            }
        }
    }

    private HardwareSpec<?> findTargetForIncoming(
            HardwareSpec<?> incoming,
            Map<String, HardwareSpec<?>> byEan,
            Map<String, HardwareSpec<?>> byMpn
    ) {
        HardwareSpec<?> anyMatch = null;

        if (incoming.getEANs() != null) {
            for (String ean : incoming.getEANs()) {
                if (ean == null) continue;
                HardwareSpec<?> candidate = byEan.get(ean);
                if (candidate != null) {
                    if (candidate.getClass().equals(incoming.getClass())) {
                        return candidate;
                    }
                    if (anyMatch == null) anyMatch = candidate;
                }
            }
        }

        if (incoming.getMPNs() != null) {
            for (String mpn : incoming.getMPNs()) {
                if (mpn == null) continue;
                HardwareSpec<?> candidate = byMpn.get(mpn);
                if (candidate != null) {
                    if (candidate.getClass().equals(incoming.getClass())) {
                        return candidate;
                    }
                    if (anyMatch == null) anyMatch = candidate;
                }
            }
        }

        return anyMatch != null && anyMatch.getClass().equals(incoming.getClass()) ? anyMatch : null;
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
        // For JOINED this is safe (Hibernate knows concrete type and deletes child+parent),
        // but we keep both to match your previous behavior and avoid edge cases.
        baseRepo.delete(entity);
        @SuppressWarnings("unchecked")
        CrudRepository<HardwareSpec<?>, Long> specific =
                (CrudRepository<HardwareSpec<?>, Long>) repoByType.get(entity.getClass());
        if (specific != null) {
            specific.delete(entity);
        }
    }

    private void rememberManufacturer(String manufacturer) {
        if (manufacturer == null) return;
        String norm = manufacturer.trim().toLowerCase(Locale.ROOT);
        if (!norm.isBlank()) {
            normalizedManufacturers.add(norm);
        }
    }

    public Set<String> getAllKnownManufacturersSnapshot() {
        // defensive copy for callers
        return Set.copyOf(normalizedManufacturers);
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
    public void onScrape(HardwareSpec<?> scrapedHardware) {
        if (scrapedHardware != null && scrapedHardware.getManufacturer() != null) {
            rememberManufacturer(scrapedHardware.getManufacturer());
        }
    }

    @Override
    @Transactional
    public void onScrapeMulti(Set<? extends HardwareSpec<?>> scrapedHardware) {
        LOGGER.info("\tSaving " + scrapedHardware.size() + " hardware specs to database");
        long start = System.currentTimeMillis();
        try {
            saveHardwareBatch(scrapedHardware);
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
        LOGGER.info("\tTook " + (System.currentTimeMillis() - start) + " ms");
    }

    public Long countByType(Class<? extends HardwareSpec<?>> hardwareType) {
        return baseRepo.countByType(hardwareType);
    }

    private static void afterCommit(Runnable r) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            r.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                r.run();
            }
        });
    }
}
