package de.verdox.openhardwareapi.component.service;

import de.verdox.openhardwareapi.client.HardwareSpecClient;
import de.verdox.openhardwareapi.configuration.SynchronizationConfig;
import de.verdox.openhardwareapi.model.HardwareSpec;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

@Slf4j
@Service
@RequiredArgsConstructor
public class SynchronizationService {

    private final HardwareSpecService hardwareSpecService;
    private final SynchronizationConfig configStore;

    private final Set<HardwareSpecClient> clients = new HashSet<>();

    @PostConstruct
    public void initClients() {
        rebuildClients();
    }

    public synchronized void rebuildClients() {
        clients.clear();
        for (String url : configStore.get().getClients()) {
            clients.add(new HardwareSpecClient(url));
            log.info("Registered sync client: {}", url);
        }
        log.info("Initialized {} sync clients.", clients.size());
    }

    public <HARDWARE extends HardwareSpec<HARDWARE>> void syncSingleToNodes(HARDWARE hardwareSpec) {
        String type = hardwareSpecService.getTypeAsString(hardwareSpec.getClass());
        Class<HARDWARE> clazz = (Class<HARDWARE>) hardwareSpec.getClass();
        ScrapingService.LOGGER.log(Level.INFO, "Syncing " + hardwareSpec.getEAN() + " to remote nodes");
        for (HardwareSpecClient client : clients) {
            try {
                client.uploadOne(type, hardwareSpec, clazz);
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        }
    }

    public <HARDWARE extends HardwareSpec<HARDWARE>> void addToSyncQueue(HARDWARE hardwareSpec) {

    }
}

