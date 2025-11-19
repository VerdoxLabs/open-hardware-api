package de.verdox.hwapi.util;

import java.net.URI;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

public class PoliteHttpGate {
    public static class Policy {
        public final int maxGlobalConcurrent;
        public final int maxPerHostConcurrent;
        public final Duration minDelayPerHost;
        public final int maxRetries;
        public final Duration baseBackoff;     // für 429/5xx

        public Policy(int maxGlobalConcurrent, int maxPerHostConcurrent,
                      Duration minDelayPerHost, int maxRetries, Duration baseBackoff) {
            this.maxGlobalConcurrent = maxGlobalConcurrent;
            this.maxPerHostConcurrent = maxPerHostConcurrent;
            this.minDelayPerHost = minDelayPerHost;
            this.maxRetries = maxRetries;
            this.baseBackoff = baseBackoff;
        }
    }

    private final Policy policy;
    private final Semaphore globalSem;
    private final ConcurrentMap<String, Semaphore> hostSem = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> hostNextAllowed = new ConcurrentHashMap<>();
    private final Random rnd = new Random();

    public PoliteHttpGate(Policy policy) {
        this.policy = policy;
        this.globalSem = new Semaphore(policy.maxGlobalConcurrent);
    }

    public interface NetCall<T> { T call() throws Exception; }

    public <T> T run(URI uri, NetCall<T> call) throws Exception {
        String host = uri.getHost();
        hostSem.putIfAbsent(host, new Semaphore(policy.maxPerHostConcurrent));
        Semaphore perHost = hostSem.get(host);

        int attempt = 0;
        while (true) {
            globalSem.acquire();
            perHost.acquire();
            try {
                waitIfNeeded(host);
                T res = call.call();
                markUsed(host);
                return res;
            } catch (Exception e) {
                markUsed(host);
                if (shouldRetry(e) && attempt < policy.maxRetries) {
                    attempt++;
                    sleepBackoff(attempt);
                    // retry
                } else {
                    throw e;
                }
            } finally {
                perHost.release();
                globalSem.release();
            }
        }
    }

    private void waitIfNeeded(String host) {
        long now = System.currentTimeMillis();
        long next = hostNextAllowed.getOrDefault(host, 0L);
        long sleep = next - now;
        if (sleep > 0) {
            try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
        }
    }

    private void markUsed(String host) {
        long next = System.currentTimeMillis() + policy.minDelayPerHost.toMillis();
        hostNextAllowed.put(host, next);
    }

    private boolean shouldRetry(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.contains("429") || msg.contains("503") || msg.contains("timed out") || msg.contains("refused");
    }

    private void sleepBackoff(int attempt) {
        long base = policy.baseBackoff.toMillis();
        long expo = (long) (base * Math.pow(2, attempt - 1));
        long jitter = rnd.nextInt((int) Math.min(500, expo / 4));
        try { Thread.sleep(expo + jitter); } catch (InterruptedException ignored) {}
    }
}
