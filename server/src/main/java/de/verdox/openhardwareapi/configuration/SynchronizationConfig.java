package de.verdox.openhardwareapi.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.verdox.openhardwareapi.component.service.ScrapingService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

@Slf4j
@Component
public class SynchronizationConfig {
    private static final File FILE = DataStorage.resolve("synchronization.json").toFile();
    private static final File TMP = DataStorage.resolve("synchronization.json.tmp").toFile();

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    @Getter
    @Setter
    public static class SyncConfig {
        private List<String> clients = new ArrayList<>();
    }

    private SyncConfig cached = new SyncConfig();

    @PostConstruct
    public void init() {
        ensureParent();

        if (!FILE.exists()) {
            cached.getClients().add("http://localhost:8080");
            write(cached);
            ScrapingService.LOGGER.log(Level.INFO, "Created default "+ FILE.getAbsolutePath());
        } else {
            cached = read();
            ScrapingService.LOGGER.log(Level.INFO, "Loaded "+cached.getClients().size()+" clients from "+FILE.getAbsolutePath());
        }
    }

    public SyncConfig get() {
        lock.readLock().lock();
        try {
            return copy(cached);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void replace(SyncConfig next) {
        validate(next);
        lock.writeLock().lock();
        try {
            write(next);
            cached = copy(next);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private SyncConfig read() {
        lock.writeLock().lock(); // Write-lock, um gleichzeitige Reads beim Laden zu blocken
        try {
            return mapper.readValue(FILE, SyncConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + FILE, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void write(SyncConfig data) {
        ensureParent();
        try {
            mapper.writeValue(TMP, data);
            Files.move(TMP.toPath(), FILE.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write " + FILE, e);
        }
    }

    private static void ensureParent() {
        File parent = FILE.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
    }

    private static void validate(SyncConfig m) {
        if (m.getClients() == null) {
            m.setClients(new ArrayList<>());
        }
        for (String url : m.getClients()) {
            if (url == null || url.isBlank() || !url.matches("^https?://.+")) {
                throw new IllegalArgumentException("Invalid client URL: " + url);
            }
        }
    }

    private static SyncConfig copy(SyncConfig src) {
        SyncConfig c = new SyncConfig();
        c.setClients(new ArrayList<>(src.getClients()));
        return c;
    }
}
