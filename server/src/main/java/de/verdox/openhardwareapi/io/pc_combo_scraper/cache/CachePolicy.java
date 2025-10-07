package de.verdox.openhardwareapi.io.pc_combo_scraper.cache;

public enum CachePolicy {
    NETWORK_ONLY,          // nie aus Cache
    CACHE_FIRST,           // wenn vorhanden & frisch -> Cache, sonst Netz
    CACHE_IF_ERROR,        // versuch Netz, bei Fehler Cache
    FORCE_CACHE,           // immer Cache (auch alt)
    REFRESH                // Netz laden, Cache überschreiben
}