package de.verdox.hwapi.catalog.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.verdox.hwapi.catalog.domain.HardwareSpec;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Service
public class HardwareSpecCache {

    private final Cache<Long, HardwareSpec<?>> byId;
    private final Cache<String, HardwareSpec<?>> byKey;

    public HardwareSpecCache() {
        this.byId = Caffeine.newBuilder()
                .maximumSize(200_000)
                .expireAfterAccess(Duration.ofMinutes(15))
                .build();

        this.byKey = Caffeine.newBuilder()
                .maximumSize(400_000)
                .expireAfterAccess(Duration.ofMinutes(15))
                .build();
    }

    /* ---------------- Reads ---------------- */

    public HardwareSpec<?> getById(long id) {
        return byId.getIfPresent(id);
    }

    public HardwareSpec<?> getByKey(String key) {
        return byKey.getIfPresent(norm(key));
    }

    /* ---------------- Writes ---------------- */

    public void put(HardwareSpec<?> spec) {
        if (spec == null) return;
        byId.put(spec.getId(), spec);

        if (spec.getEANs() != null) {
            for (String e : spec.getEANs()) {
                if (e != null) byKey.put(norm(e), spec);
            }
        }
        if (spec.getMPNs() != null) {
            for (String m : spec.getMPNs()) {
                if (m != null) byKey.put(norm(m), spec);
            }
        }
    }

    public void evict(HardwareSpec<?> spec) {
        if (spec == null) return;
        byId.invalidate(spec.getId());

        if (spec.getEANs() != null) {
            for (String e : spec.getEANs()) {
                if (e != null) byKey.invalidate(norm(e));
            }
        }
        if (spec.getMPNs() != null) {
            for (String m : spec.getMPNs()) {
                if (m != null) byKey.invalidate(norm(m));
            }
        }
    }

    public void clear() {
        byId.invalidateAll();
        byKey.invalidateAll();
    }

    private static String norm(String s) {
        return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
    }
}

