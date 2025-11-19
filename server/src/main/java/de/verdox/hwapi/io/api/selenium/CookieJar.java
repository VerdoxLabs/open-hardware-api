package de.verdox.hwapi.io.api.selenium;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.Cookie;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;

public final class CookieJar {
    private static final ObjectMapper OM = new ObjectMapper();
    private final Path root;

    public CookieJar(Path root) {
        this.root = root;
    }

    private static String hostKey(String host) {
        // dateinamenfreundlich
        return host.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.-]", "_");
    }

    private Path fileForHost(String host) throws IOException {
        Path dir = root.resolve("cookies");
        Files.createDirectories(dir);
        return dir.resolve(hostKey(host) + ".json");
    }

    public void save(URI uri, Set<Cookie> cookies) {
        try {
            Path f = fileForHost(uri.getHost());
            List<Map<String, Object>> serial = new ArrayList<>();
            for (Cookie c : cookies) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", c.getName());
                m.put("value", c.getValue());
                m.put("domain", c.getDomain());
                m.put("path", c.getPath());
                m.put("expiry", c.getExpiry() != null ? c.getExpiry().toInstant().toEpochMilli() : null);
                m.put("secure", c.isSecure());
                m.put("httpOnly", c.isHttpOnly());
                m.put("sameSite", c.getSameSite()); // benötigt Selenium ≥ 4.7
                serial.add(m);
            }
            Files.writeString(f, OM.writerWithDefaultPrettyPrinter().writeValueAsString(serial), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignore) {
        }
    }

    public Set<Cookie> load(URI uri) {
        try {
            Path f = fileForHost(uri.getHost());
            if (!Files.exists(f)) return Set.of();
            List<Map<String, Object>> serial = OM.readValue(Files.readString(f), new TypeReference<>() {});
            Set<Cookie> out = new LinkedHashSet<>();
            long now = System.currentTimeMillis();
            for (Map<String, Object> m : serial) {
                Long exp = (m.get("expiry") instanceof Number n) ? n.longValue() : null;
                if (exp != null && exp < now) continue;
                Cookie c = new Cookie.Builder((String)m.get("name"), (String)m.get("value"))
                        .domain((String)m.getOrDefault("domain", uri.getHost()))
                        .path((String)m.getOrDefault("path", "/"))
                        .expiresOn(exp != null ? Date.from(Instant.ofEpochMilli(exp)) : null)
                        .isSecure(Boolean.TRUE.equals(m.get("secure")))
                        .isHttpOnly(Boolean.TRUE.equals(m.get("httpOnly")))
                        .sameSite((String)m.get("sameSite"))
                        .build();
                out.add(c);
            }
            return out;
        } catch (Exception e) {
            return Set.of();
        }
    }
}
