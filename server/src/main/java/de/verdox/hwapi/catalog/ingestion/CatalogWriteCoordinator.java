package de.verdox.hwapi.catalog.ingestion;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Coordinates long-running catalog writes so scheduled marketplace jobs do not interfere. */
@Component
public class CatalogWriteCoordinator {
    private final AtomicBoolean openDbImportRunning = new AtomicBoolean();

    public void beginOpenDbImport() { openDbImportRunning.set(true); }

    public void endOpenDbImport() { openDbImportRunning.set(false); }

    public boolean isOpenDbImportRunning() { return openDbImportRunning.get(); }
}
