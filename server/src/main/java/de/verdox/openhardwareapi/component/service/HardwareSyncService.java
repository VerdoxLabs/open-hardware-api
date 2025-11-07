package de.verdox.openhardwareapi.component.service;

import de.verdox.openhardwareapi.client.HardwareSpecClient;
import de.verdox.openhardwareapi.configuration.SynchronizationConfig;
import de.verdox.openhardwareapi.model.HardwareSpec;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class HardwareSyncService {

    private static final Logger LOGGER = ScrapingService.LOGGER;

    private final HardwareSpecService hardwareSpecService;
    private final SynchronizationConfig configStore;

    /**
     * Aktive Clients (thread-safe über rebuild und danach read-mostly)
     */
    private final Set<HardwareSpecClient> clients = ConcurrentHashMap.newKeySet();

    /**
     * Double-Buffer: aktiver Sammel-Puffer (Key -> HardwareSpec)
     */
    private final AtomicReference<ConcurrentMap<String, HardwareSpec<?>>> activeBuffer = new AtomicReference<>(new ConcurrentHashMap<>());

    /**
     * Worker-Executor für Sync (Single-Thread, bewahrt Reihenfolge)
     */
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sync-worker");
        t.setDaemon(true);
        return t;
    });

    // --- Konfigurierbare Parameter ---
    @Value("${sync.flush-interval-ms:5000}")
    private long flushIntervalMs;

    @Value("${sync.flush-threshold:1000}")
    private int flushThreshold;

    @Value("${sync.bulk.all-or-nothing:false}")
    private boolean bulkAllOrNothing;

    @Value("${sync.bulk.max-batch-size:200}")
    private int bulkMaxBatchSize;

    @PostConstruct
    public void initClients() {
        rebuildClients();
        LOGGER.log(Level.INFO, String.format(
                "SyncService initialized: flushInterval=%dms, threshold=%d, bulkBatch=%d, allOrNothing=%s",
                flushIntervalMs, flushThreshold, bulkMaxBatchSize, bulkAllOrNothing));
    }

    @PreDestroy
    public void shutdown() {
        syncExecutor.shutdownNow();
    }

    // ---------------------------------------------------------------
    // Client-Verwaltung
    // ---------------------------------------------------------------

    public synchronized void rebuildClients() {
        clients.clear();
        for (String url : configStore.get().getClients()) {
            clients.add(new HardwareSpecClient(url));
            LOGGER.log(Level.INFO, "Registered sync client: {0}", url);
        }
        LOGGER.log(Level.INFO, "Initialized {0} sync clients.", clients.size());
    }

    // ---------------------------------------------------------------
    // Direkter Einzel-Sync (z. B. on-demand)
    // ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public <HARDWARE extends HardwareSpec<HARDWARE>> void syncSingleToNodes(HARDWARE hardwareSpec) {
        if (clients.isEmpty()) {
            return;
        }
        Class<HARDWARE> clazz = (Class<HARDWARE>) hardwareSpec.getClass();
        String type = hardwareSpecService.getTypeAsString(clazz);
        LOGGER.log(Level.INFO, "Syncing {0} to remote nodes", hardwareSpec.getEANs());
        for (HardwareSpecClient client : clients) {
            try {
                client.uploadHardwareOne(type, hardwareSpec, clazz);
            } catch (Throwable ex) {
                LOGGER.log(Level.WARNING,
                        String.format("Sync to %s failed for %s %s: %s",
                                client, type, safeKeyOf(hardwareSpec), ex), ex);
            }
        }
    }

    // ---------------------------------------------------------------
    // Asynchrones Queueing + Flush
    // ---------------------------------------------------------------

    /**
     * Nicht-blockierend: legt/überschreibt den Eintrag im aktiven Buffer.
     */
    public <HARDWARE extends HardwareSpec<HARDWARE>> void addToSyncQueue(HARDWARE hardwareSpec) {
        if (hardwareSpec == null) return;
        Class<HARDWARE> clazz = (Class<HARDWARE>) hardwareSpec.getClass();
        String key = buildKey(hardwareSpecService.getTypeAsString(clazz), hardwareSpec);
        activeBuffer.get().put(key, hardwareSpec);

        int size = activeBuffer.get().size();
        if (size >= flushThreshold) {
            triggerFlushAsync();
        }
    }

    /**
     * Periodischer Flush (Hintergrund-Task)
     */
    @Scheduled(fixedDelayString = "${sync.flush-interval-ms:5000}")
    public void scheduledFlush() {
        triggerFlushAsync();
    }

    /**
     * Atomic Swap + Übergabe an Worker-Thread.
     */
    private void triggerFlushAsync() {
        if (clients.isEmpty()) {
            return;
        }
        ConcurrentMap<String, HardwareSpec<?>> drained =
                activeBuffer.getAndSet(new ConcurrentHashMap<>());

        if (drained.isEmpty()) return;

        syncExecutor.submit(() -> flushBuffer(drained));
    }

    // ---------------------------------------------------------------
    // BULK Upload Logik
    // ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void flushBuffer(ConcurrentMap<String, HardwareSpec<?>> batch) {
        long start = System.currentTimeMillis();
        int total = batch.size();
        LOGGER.log(Level.FINE, "Flushing {0} specs (bulk) to {1} clients ...", new Object[]{total, clients.size()});

        List<HardwareSpec<?>> all = new ArrayList<>(batch.values());
        Map<String, List<HardwareSpec<?>>> byType = new HashMap<>();


        for (HardwareSpec<?> spec : all) {
            Class<HardwareSpec<?>> clazz = (Class<HardwareSpec<?>>) spec.getClass();
            String type = hardwareSpecService.getTypeAsString(clazz);
            byType.computeIfAbsent(type, t -> new ArrayList<>()).add(spec);
        }

        A:
        for (HardwareSpecClient client : clients) {
            int counter = 0;
            for (Map.Entry<String, List<HardwareSpec<?>>> e : byType.entrySet()) {
                String type = e.getKey();
                List<HardwareSpec<?>> specsOfType = e.getValue();

                for (List<HardwareSpec<?>> chunk : partition(specsOfType, bulkMaxBatchSize)) {
                    for (HardwareSpec<?> hardwareSpec : chunk) {
                        if (hardwareSpec.getMPN() == null || hardwareSpec.getMPN().isBlank()) {
                            continue;
                        }
                        Class<HardwareSpec<?>> clazz = (Class<HardwareSpec<?>>) hardwareSpec.getClass();
                        try {
                            client.uploadHardwareOne(type, hardwareSpec, clazz);
                            counter++;
                        } catch (Throwable ex) {
                            LOGGER.log(Level.SEVERE, String.format(
                                    "Upload failed @ EAN=%s model=%s",
                                    hardwareSpec.getEANs(), hardwareSpec.getModel()), ex.getCause().getMessage());
                            break A;
                        }
                    }
                }
            }
            LOGGER.log(Level.FINE, "\tSynced " + counter + " hardware entities to " + client.getUrlApiV1());
        }

        long dur = System.currentTimeMillis() - start;
        //LOGGER.log(Level.INFO, "Bulk flush done: {0} specs in {1} ms.", new Object[]{total, dur});
    }

    // ---------------------------------------------------------------
    // Hilfsfunktionen
    // ---------------------------------------------------------------

    private static <T> List<List<T>> partition(List<T> list, int size) {
        if (size <= 0) size = 200;
        List<List<T>> parts = new ArrayList<>((list.size() + size - 1) / size);
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return parts;
    }

    private void logBulkResult(HardwareSpecClient client, String type, int chunkSize, HardwareSpecClient.BulkResult result) {
        if (result == null) {
            LOGGER.log(Level.INFO, "Bulk upload @ {0} type={1} chunkSize={2} -> null result (success assumed)",
                    new Object[]{client, type, chunkSize});
            return;
        }
        int success = safeInt(result.savedCount());
        int failed = safeInt(result.failedCount());
        LOGGER.log(Level.INFO, "Bulk upload @ {0} type={1} chunkSize={2} -> success={3}, failed={4}",
                new Object[]{client, type, chunkSize, success, failed});
    }

    private boolean hasFailures(HardwareSpecClient.BulkResult result) {
        return result != null && safeInt(result.failedCount()) > 0;
    }

    private int safeInt(Integer i) {
        return i == null ? 0 : i;
    }

    @SuppressWarnings("unchecked")
    private void fallbackSingleForFailures(HardwareSpecClient client, String type,
                                           List<HardwareSpec<?>> chunk, HardwareSpecClient.BulkResult result) {
        Set<String> failedKeys = extractFailedKeys(result);
        if (failedKeys.isEmpty()) return;

        for (HardwareSpec<?> spec : chunk) {
            if (failedKeys.contains(buildKey(type, spec))) {
                try {
                    uploadOneUnchecked(client, type, spec);
                } catch (Throwable ex) {
                    LOGGER.log(Level.WARNING, String.format(
                            "Fallback single upload failed for %s @ %s: %s",
                            safeKeyOf(spec), client, ex), ex);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void fallbackSingleAll(HardwareSpecClient client, String type, List<HardwareSpec<?>> chunk) {
        for (HardwareSpec<?> spec : chunk) {
            try {
                uploadOneUnchecked(client, type, spec);
            } catch (Throwable ex) {
                LOGGER.log(Level.WARNING, String.format(
                        "Fallback single upload (all) failed for %s @ %s: %s",
                        safeKeyOf(spec), client, ex), ex);
            }
        }
    }

    private Set<String> extractFailedKeys(HardwareSpecClient.BulkResult result) {
        return Collections.emptySet();
    }

    @SuppressWarnings("unchecked")
    private <H extends HardwareSpec<H>> void uploadOneUnchecked(HardwareSpecClient client, String type, HardwareSpec<?> spec) throws Throwable {
        Class<H> clazz = (Class<H>) spec.getClass();
        client.uploadHardwareOne(type, (H) spec, clazz);
    }

    private String buildKey(String type, HardwareSpec<?> spec) {
        String id = firstNonBlank(spec.getMPN(),
                Integer.toHexString(System.identityHashCode(spec)));
        return type + "|" + id.trim().toLowerCase(Locale.ROOT);
    }

    private String safeKeyOf(HardwareSpec<?> spec) {
        return firstNonBlank(spec.getMPN(), "?");
    }

    private static String firstNonBlank(String... s) {
        if (s == null) return null;
        for (String v : s) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
