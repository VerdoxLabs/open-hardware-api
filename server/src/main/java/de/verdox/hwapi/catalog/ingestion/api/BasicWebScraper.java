package de.verdox.hwapi.catalog.ingestion.api;

import org.jsoup.nodes.Document;

public interface BasicWebScraper {
    Document scrape(String url);
}
