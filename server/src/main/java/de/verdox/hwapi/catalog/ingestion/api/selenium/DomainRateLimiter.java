package de.verdox.hwapi.catalog.ingestion.api.selenium;

import de.verdox.hwapi.catalog.ingestion.ScrapingService;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;

/** JVM-wide spacing for requests to one external site, including its CDN assets. */
public final class DomainRateLimiter {
    private static final ConcurrentMap<String, Object> LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Instant> LAST_REQUESTS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Instant> BLOCKED_UNTIL = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Semaphore> IN_FLIGHT = new ConcurrentHashMap<>();

    private DomainRateLimiter() {
    }

    /** Acquires the exclusive request slot for a domain and enforces its spacing. */
    public static Permit acquire(String domain, Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()) return new Permit(null);
        String key = domain.toLowerCase(Locale.ROOT);
        throwIfPaused(key, domain);
        Semaphore semaphore = IN_FLIGHT.computeIfAbsent(key, ignored -> new Semaphore(1));
        try {
            semaphore.acquire();
            Object lock = LOCKS.computeIfAbsent(key, ignored -> new Object());
            synchronized (lock) {
                Instant blockedUntil = BLOCKED_UNTIL.get(key);
                if (blockedUntil != null) {
                    Duration blockedRemaining = Duration.between(Instant.now(), blockedUntil);
                    if (!blockedRemaining.isNegative() && !blockedRemaining.isZero()) {
                        semaphore.release();
                        throw new DomainPausedException(domain, blockedRemaining);
                    }
                    BLOCKED_UNTIL.remove(key, blockedUntil);
                }
                Instant previous = LAST_REQUESTS.get(key);
                if (previous != null) {
                    Duration remaining = interval.minus(Duration.between(previous, Instant.now()));
                    if (!remaining.isNegative() && !remaining.isZero()) {
                        ScrapingService.LOGGER.log(Level.INFO,
                                "Rate limit for {0}: waiting {1} seconds before the next live request",
                                new Object[]{domain, remaining.toSeconds()});
                        Thread.sleep(remaining.toMillis());
                    }
                }
                LAST_REQUESTS.put(key, Instant.now());
            }
            return new Permit(semaphore);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            semaphore.release();
            throw new IllegalStateException("Interrupted while waiting for the rate limit of " + domain, interrupted);
        }
    }

    /** Returns true without waiting when a domain is in its block cooldown. */
    public static boolean isPaused(String domain) {
        if (domain == null || domain.isBlank()) return false;
        Instant until = BLOCKED_UNTIL.get(domain.toLowerCase(Locale.ROOT));
        if (until == null) return false;
        if (Instant.now().isBefore(until)) return true;
        BLOCKED_UNTIL.remove(domain.toLowerCase(Locale.ROOT), until);
        return false;
    }

    public static Instant pausedUntil(String domain) {
        if (domain == null || domain.isBlank()) return null;
        String key = domain.toLowerCase(Locale.ROOT);
        Instant until = BLOCKED_UNTIL.get(key);
        if (until == null || !Instant.now().isBefore(until)) {
            if (until != null) BLOCKED_UNTIL.remove(key, until);
            return null;
        }
        return until;
    }

    private static void throwIfPaused(String key, String domain) {
        Instant until = BLOCKED_UNTIL.get(key);
        if (until != null) {
            Duration remaining = Duration.between(Instant.now(), until);
            if (!remaining.isNegative() && !remaining.isZero()) {
                throw new DomainPausedException(domain, remaining);
            }
            BLOCKED_UNTIL.remove(key, until);
        }
    }

    /**
     * Pauses all live requests for a domain after an explicit rate-limit/block page.
     * The longest currently active pause wins, so concurrent detections cannot shorten it.
     */
    public static void pause(String domain, Duration duration) {
        if (domain == null || domain.isBlank() || duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        String key = domain.toLowerCase(Locale.ROOT);
        Instant until = Instant.now().plus(duration);
        BLOCKED_UNTIL.merge(key, until, (existing, candidate) -> existing.isAfter(candidate) ? existing : candidate);
        ScrapingService.LOGGER.log(Level.WARNING,
                "Detected explicit rate-limit page for {0}; pausing live requests until {1}",
                new Object[]{domain, BLOCKED_UNTIL.get(key)});
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean released;

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (!released && semaphore != null) {
                released = true;
                semaphore.release();
            }
        }
    }

    public static final class DomainPausedException extends RuntimeException {
        private final Duration remaining;

        public DomainPausedException(String domain, Duration remaining) {
            super("Domain " + domain + " is paused for another " + remaining.toSeconds() + " seconds");
            this.remaining = remaining;
        }

        public Duration remaining() {
            return remaining;
        }
    }
}
