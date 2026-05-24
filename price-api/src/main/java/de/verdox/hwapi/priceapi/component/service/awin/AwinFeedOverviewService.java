package de.verdox.hwapi.priceapi.component.service.awin;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class AwinFeedOverviewService {

    private static final String AWIN_FEED_OVERVIEW_URL = "https://ui.awin.com/productdata-darwin-download/publisher/2681304/5ba1438d62883e73ead57420c5861121/1/feedList";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public List<AwinFeed> loadActiveFeeds() throws IOException, InterruptedException {
        // 2: URL bereinigen
        String cleanUrl = AWIN_FEED_OVERVIEW_URL
                .trim()
                .replace(" ", "%20")
                ;

        URI uri = URI.create(cleanUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        HttpResponse<InputStream> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("AWIN Feed overview download failed. HTTP status: " + response.statusCode());
        }

        try (InputStream bodyStream = response.body();
             Reader reader = new InputStreamReader(bodyStream, StandardCharsets.UTF_8)) {

            CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .build();

            Iterable<CSVRecord> records = csvFormat.parse(reader);
            List<AwinFeed> feeds = new ArrayList<>();

            for (CSVRecord record : records) {
                String membershipStatus = record.get("Membership Status");

                if (!"Joined".equalsIgnoreCase(membershipStatus)
                        && !"Active".equalsIgnoreCase(membershipStatus)) {
                    continue;
                }

                String advertiserName = record.get("Advertiser Name");
                String primaryRegion  = record.get("Primary Region");
                String language       = record.get("Language");
                String url            = record.get("URL"); // Download-Link für .csv.gz

                if (url == null || url.isBlank()) {
                    continue;
                }

                feeds.add(new AwinFeed(
                        advertiserName,
                        url,
                        primaryRegion,
                        language
                ));
            }

            return feeds;
        }
    }
}
