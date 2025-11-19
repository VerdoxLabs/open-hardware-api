package de.verdox.hwapi.io.api;

import org.jsoup.nodes.Document;

public interface BasicWebScraper {
    Document scrape(String url);
}
