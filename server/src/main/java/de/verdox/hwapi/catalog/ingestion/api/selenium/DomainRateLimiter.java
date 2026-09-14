package de.verdox.hwapi.catalog.ingestion.api.selenium;

import de.verdox.hwapi.catalog.ingestion.ScrapingService;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/** JVM-wide spacing for requests to one external site, including its CDN assets. */
public final class DomainRateLimiter {
    private static final ConcurrentMap<String, Object> LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Instant> LAST_REQUESTS = new ConcurrentHashMap<>();

    private DomainRateLimiter() {
    }

    public static void await(String domain, Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()) return;
        String key = domain.toLowerCase(Locale.ROOT);
        Object lock = LOCKS.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            Instant previous = LAST_REQUESTS.get(key);
            if (previous != null) {
                Duration remaining = interval.minus(Duration.between(previous, Instant.now()));
                if (!remaining.isNegative() && !remaining.isZero()) {
                    try {
                        ScrapingService.LOGGER.log(Level.INFO,
                                "Rate limit for {0}: waiting {1} seconds before the next live request",
                                new Object[]{domain, remaining.toSeconds()});
                        Thread.sleep(remaining.toMillis());
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting for the rate limit of " + domain, interrupted);
                    }
                }
            }
            LAST_REQUESTS.put(key, Instant.now());
        }
    }
}
