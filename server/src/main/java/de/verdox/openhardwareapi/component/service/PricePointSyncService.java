package de.verdox.openhardwareapi.component.service;

import de.verdox.openhardwareapi.client.HardwareSpecClient;
import de.verdox.openhardwareapi.configuration.SynchronizationConfig;
import de.verdox.openhardwareapi.model.RemoteSoldItem;
import de.verdox.openhardwareapi.model.dto.PricePointUploadDto;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PricePointSyncService {
    private static final Logger LOGGER = ScrapingService.LOGGER;

    private final SynchronizationConfig configStore;

    /**
     * Aktive Clients (thread-safe über rebuild und danach read-mostly)
     */
    private final Set<HardwareSpecClient> clients = ConcurrentHashMap.newKeySet();

    /**
     * Double-Buffer: aktiver Sammel-Puffer (Key -> HardwareSpec)
     */

    private final AtomicReference<Set<RemoteSoldItem>> soldItemBuffer = new AtomicReference<>(ConcurrentHashMap.newKeySet());

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

    /**
     * Periodischer Flush (Hintergrund-Task)
     */
    @Scheduled(fixedDelayString = "${sync.flush-interval-ms:5000}")
    public void scheduledFlush() {
        triggerFlushAsync();
    }


    // ---------------------------------------------------------------
    // Asynchrones Queueing + Flush
    // ---------------------------------------------------------------

    /**
     * Nicht-blockierend: legt/überschreibt den Eintrag im aktiven Buffer.
     */
    public void addToSyncQueue(RemoteSoldItem soldItem) {
        if (soldItem == null) return;
        soldItemBuffer.get().add(soldItem);

        int size = soldItemBuffer.get().size();
        if (size >= flushThreshold) {
            triggerFlushAsync();
        }
    }

    /**
     * Atomic Swap + Übergabe an Worker-Thread.
     */
    private void triggerFlushAsync() {
        if (clients.isEmpty()) {
            return;
        }
        Set<RemoteSoldItem> drained = soldItemBuffer.getAndSet(ConcurrentHashMap.newKeySet());

        if (drained.isEmpty()) return;
        syncExecutor.submit(() -> flushBuffer(drained));
    }

    @SuppressWarnings("unchecked")
    private void flushBuffer(Set<RemoteSoldItem> batch) {
        long start = System.currentTimeMillis();
        int total = batch.size();

        LOGGER.log(Level.INFO, "Flushing {0} prices (bulk) to {1} clients ...", new Object[]{total, clients.size()});

        var toUpload = batch.stream().map(soldItem -> new PricePointUploadDto(
                soldItem.getEan(),
                soldItem.getMarketPlaceDomain(),
                soldItem.getMarketPlaceItemID(),
                soldItem.getSellPrice(),
                soldItem.getCurrency(),
                soldItem.getSellDate()
        )).collect(Collectors.toSet());

        for (HardwareSpecClient client : clients) {
            client.priceItemUpload(toUpload);
            LOGGER.log(Level.FINE, "\tSynced " + toUpload.size() + " price points to " + client.getUrlApiV1());
        }
    }
}
