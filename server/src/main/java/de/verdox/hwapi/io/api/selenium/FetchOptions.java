package de.verdox.hwapi.io.api.selenium;

import lombok.Getter;

import java.time.Duration;

@Getter
public class FetchOptions {
    private boolean skipIfNotCache;
    private boolean tryHeadlessFirst;
    private Duration ttl = Duration.ofDays(10);
    private BeforeSaveOperation beforeSaveOperation = driver -> {};

    public FetchOptions setSkipIfNotCache(boolean skipIfNotCache) {
        this.skipIfNotCache = skipIfNotCache;
        return this;
    }

    public FetchOptions setTtl(Duration ttl) {
        this.ttl = ttl;
        return this;
    }

    public FetchOptions setTryHeadlessFirst(boolean tryHeadlessFirst) {
        this.tryHeadlessFirst = tryHeadlessFirst;
        return this;
    }

    public FetchOptions setBeforeSaveOperation(BeforeSaveOperation beforeSaveOperation) {
        this.beforeSaveOperation = beforeSaveOperation;
        return this;
    }
}
