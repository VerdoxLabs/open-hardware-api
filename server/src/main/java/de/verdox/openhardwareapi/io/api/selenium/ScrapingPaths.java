package de.verdox.openhardwareapi.io.api.selenium;

import de.verdox.openhardwareapi.configuration.DataStorage;

import java.nio.file.Path;

public final class ScrapingPaths {
    private static final Path ROOT = DataStorage.resolve("/scraping/pages");

    private ScrapingPaths() {}

    public static Path fileFor(String domain, String id, String url) {
        String safeId = id.trim().replaceAll("[/\\\\\\p{Cntrl}]+", "_");
        String hash = sha1(urlCanonical(url));
        return ROOT.resolve(domain).resolve(safeId).resolve(hash + ".html");
    }

    public static Path idFolder(String domain, String id) {
        String safeId = id.trim().replaceAll("[/\\\\\\p{Cntrl}]+", "_");
        return ROOT.resolve(domain).resolve(safeId);
    }

    public static String urlCanonical(String url) {
        // Kanonisierung: ohne Fragment, trim, ggf. trailing slash normalisieren
        // (vereinfachte Variante)
        return url.strip();
    }

    private static String sha1(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
