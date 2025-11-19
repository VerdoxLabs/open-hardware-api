package de.verdox.hwapi.configuration;

import de.verdox.hwapi.hardwareapi.component.service.ScrapingService;

import java.nio.file.Path;

public class DataStorage {
    private static final String OS = System.getProperty("os.name").toLowerCase();
    public static boolean isWindows = OS.contains("win");
    public static boolean isLinux = OS.contains("nix") || OS.contains("nux") || OS.contains("aix");

    static {
        if(isLinux) {
            ScrapingService.LOGGER.info("Found Linux OS");
        }
        else if(isWindows) {
            ScrapingService.LOGGER.info("Found Windows OS");
        }
    }

    public static Path resolve(String subPath) {
        if(isLinux) {
            return Path.of("/var/lib/open-hardware-api/"+subPath);
        }
        else {
            return Path.of("./open-hardware-api/"+subPath);
        }
    }
}
