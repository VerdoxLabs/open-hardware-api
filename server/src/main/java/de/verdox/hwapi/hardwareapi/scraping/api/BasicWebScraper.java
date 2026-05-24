package de.verdox.hwapi.hardwareapi.scraping.api;

import org.jsoup.nodes.Document;

public interface BasicWebScraper {
    Document scrape(String url);
}
