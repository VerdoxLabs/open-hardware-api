package de.verdox.hwapi.infrastructure.storage;

import java.nio.file.Path;
import java.util.logging.Logger;

public class DataStorage {
    public static final Logger LOGGER = Logger.getLogger(DataStorage.class.getName());
    private static final String OS = System.getProperty("os.name").toLowerCase();
    public static boolean isWindows = OS.contains("win");
    public static boolean isLinux = OS.contains("nix") || OS.contains("nux") || OS.contains("aix");

    static {
        if(isLinux) {
            LOGGER.info("Found Linux OS");
        }
        else if(isWindows) {
            LOGGER.info("Found Windows OS");
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
