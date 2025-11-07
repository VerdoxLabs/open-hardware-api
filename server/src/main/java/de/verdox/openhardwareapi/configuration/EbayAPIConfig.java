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
import java.nio.file.StandardCopyOption;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

@Slf4j
@Component
public class EbayAPIConfig {
    private static final File FILE = DataStorage.resolve("ebay_api_config.json").toFile();
    private static final File TMP = DataStorage.resolve("ebay_api_config.json.tmp").toFile();

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    @Getter
    @Setter
    public static class ClientConfig {
        private String ebayAPI = "https://api.ebay.com";
        private String ebayClientID = "YOUR_CLIENT_ID_HERE";
        private String ebayClientSecret = "YOUR_CLIENT_SECRET_HERE";
        private int lastIndex = 0;
    }

    private ClientConfig cached = new ClientConfig();

    @PostConstruct
    public void init() {
        ensureParent();

        if (!FILE.exists()) {
            write(cached);
            ScrapingService.LOGGER.log(Level.INFO, "Created default " + FILE.getAbsolutePath());
        } else {
            cached = read();
            ScrapingService.LOGGER.log(Level.INFO, "Loaded ebay client config for " + cached.getEbayAPI());
        }
    }

    public ClientConfig get() {
        lock.readLock().lock();
        try {
            return copy(cached);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void replace(ClientConfig next) {
        lock.writeLock().lock();
        try {
            write(next);
            cached = copy(next);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private ClientConfig read() {
        lock.writeLock().lock(); // Write-lock, um gleichzeitige Reads beim Laden zu blocken
        try {
            return mapper.readValue(FILE, ClientConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + FILE, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void write(ClientConfig data) {
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

    private static ClientConfig copy(ClientConfig src) {
        ClientConfig c = new ClientConfig();
        c.setEbayClientID(src.getEbayClientID());
        c.setEbayClientSecret(src.getEbayClientSecret());
        c.setEbayAPI(src.getEbayAPI());
        return c;
    }
}
