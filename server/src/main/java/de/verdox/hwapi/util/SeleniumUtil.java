package de.verdox.hwapi.util;

import de.verdox.hwapi.hardwareapi.component.service.ScrapingService;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class SeleniumUtil {
    private static final Map<String, WebDriver> instances = new HashMap<>();

    public synchronized static void cleanUp() {
        instances.forEach((s, webDriver) -> webDriver.quit());
        instances.clear();
    }

    public synchronized static void cleanup(String id) {
        try {
            WebDriver d = instances.remove(id);
            if (d != null) d.quit();
        } catch (Exception ignore) {}
    }

    public synchronized static WebDriver create(String id, ChromeOptions capabilities) throws MalformedURLException {
        String pathAsString = System.getenv("SELENIUM_PROFILE");
        if (pathAsString == null) {
            return create(id, capabilities, null);
        }
        Path path = Path.of(pathAsString);
        if (path.toFile().isDirectory()) {
            return create(id, capabilities, path);
        } else {
            return create(id, capabilities, null);
        }
    }

    public synchronized static WebDriver create(String id, ChromeOptions capabilities, Path profileToCopyAndUse) throws MalformedURLException {
        if (instances.containsKey(id)) {
            return instances.get(id);
        }
        // Standard-Hardening-Flags (wie bei dir)
        capabilities.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        capabilities.setExperimentalOption("useAutomationExtension", false);
        capabilities.addArguments("--disable-blink-features=AutomationControlled");

        // Wenn ein Profil übergeben wurde: kopieren und ChromeOptions anpassen
        if (profileToCopyAndUse != null) {
            try {
                Path tmpUserDataDir = createTempProfileCopy(profileToCopyAndUse);
                // profileToCopyAndUse.getFileName() ist z.B. "Default" oder "Profile 1"
                String profileDirName = profileToCopyAndUse.getFileName().toString();

                // Chrome erwartet: --user-data-dir=<pfad zum user data ordner>
                // und --profile-directory=<Profil-Ordner-Name> (Default, Profile 1, ...)
                capabilities.addArguments("--user-data-dir=" + tmpUserDataDir.toAbsolutePath());
                capabilities.addArguments("--profile-directory=" + profileDirName);

                ScrapingService.LOGGER.log(Level.INFO, "Using temporary copied profile at: " + tmpUserDataDir);
            } catch (IOException e) {
                ScrapingService.LOGGER.log(Level.WARNING, "Could not copy profile to temp folder, continuing without profile copy.", e);
            }
        }

        try {
            ScrapingService.LOGGER.log(Level.INFO, "Trying to create chrome driver");
            instances.put(id, new ChromeDriver(capabilities));
        } catch (Exception e) {
            ScrapingService.LOGGER.log(Level.INFO, "Chrome driver not found on this system");
            var hub = System.getenv().getOrDefault("SELENIUM_REMOTE_URL", "http://localhost:4444");
            try {
                ScrapingService.LOGGER.log(Level.INFO, "Creating remote selenium driver at " + hub);
                instances.put(id, new RemoteWebDriver(URI.create(hub).toURL(), capabilities));
            } catch (Throwable ex) {
                ScrapingService.LOGGER.log(Level.WARNING, "Could not connect to remote URL " + hub + ".", ex);
            }
        }
        return instances.getOrDefault(id, null);
    }

    /**
     * Kopiert das angegebene Profil-Verzeichnis rekursiv in ein temporäres Verzeichnis.
     * Rückgabe: Pfad zum temporären "User Data" Verzeichnis, in dem das Profil-Ordner-Name
     * als Unterordner existiert (z.B. /tmp/chrome-userdata123/Profile 1).
     */
    private static Path createTempProfileCopy(Path profileToCopyAndUse) throws IOException {
        if (!Files.exists(profileToCopyAndUse) || !Files.isDirectory(profileToCopyAndUse)) {
            throw new IllegalArgumentException("profileToCopyAndUse must be an existing directory: " + profileToCopyAndUse);
        }

        // Erzeuge temporäres "User Data" Verzeichnis (Chrome erwartet darin die Profile als Unterordner)
        Path tmpUserDataDir = Files.createTempDirectory("chrome-user-data-");
        Path destinationProfileDir = tmpUserDataDir.resolve(profileToCopyAndUse.getFileName());

        // Rekursive Kopie (robust)
        copyRecursively(profileToCopyAndUse, destinationProfileDir);

        // Beim JVM-Shutdown das Temp-Verzeichnis löschen (besser wäre kontrolliertes Cleanup)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                deleteRecursively(tmpUserDataDir);
                ScrapingService.LOGGER.log(Level.INFO, "Deleted temporary profile directory: " + tmpUserDataDir);
            } catch (IOException ex) {
                ScrapingService.LOGGER.log(Level.WARNING, "Failed to delete temporary profile directory: " + tmpUserDataDir, ex);
            }
        }));

        return tmpUserDataDir;
    }

    private static void copyRecursively(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = dst.resolve(src.relativize(dir));
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = dst.resolve(src.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
