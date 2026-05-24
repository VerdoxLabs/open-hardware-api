package de.verdox.hwapi.priceapi.component.service.awin;

import de.verdox.hwapi.priceapi.component.dto.AwinProductRecord;
import de.verdox.hwapi.priceapi.component.util.AwinProductFeedParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

@Service
public class AwinFeedService {

    private static final Logger log = LoggerFactory.getLogger(AwinFeedService.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AwinFeedOverviewService feedOverviewService;

    public AwinFeedService(AwinFeedOverviewService feedOverviewService) {
        this.feedOverviewService = feedOverviewService;
    }

    /**
     * Optional: wenn du weiterhin einen einzelnen Feed über die alte URL laden willst,
     * kannst du diese Methode behalten/anpassen.
     */
    public void downloadAndParseSingleFeed(AwinFeed awinFeed, Consumer<AwinProductRecord> consumer) throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(awinFeed.feedUrlDownloadLink()))
                .GET()
                .build();


        HttpResponse<InputStream> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("AWIN Feed download failed. HTTP status: " + response.statusCode());
        }

        // Ordner definieren, in dem immer die "jüngste" CSV liegt
        Path storageDir = de.verdox.hwapi.util.DataStorage.resolve("awin-feeds");
        Files.createDirectories(storageDir);

        // Dateiname z.B. pro Feed
        String safeAdvertiser = sanitizeFileName(awinFeed.advertiser());
        String feedKey = shortSha256(awinFeed.feedUrlDownloadLink());
        String fileName = "awin-feed-" + safeAdvertiser + "-" + feedKey + "-latest.csv";
        Path csvFile = storageDir.resolve(fileName);

        // GZIP -> CSV entpacken und speichern
        try (InputStream bodyStream = response.body();
             GZIPInputStream gzIn = new GZIPInputStream(bodyStream)) {

            // Überschreibt immer die vorherige Datei → damit ist diese hier immer die jüngste
            Files.copy(gzIn, csvFile, StandardCopyOption.REPLACE_EXISTING);
        }

        // Jetzt aus der gespeicherten CSV parsen
        try (InputStream csvIn = Files.newInputStream(csvFile)) {
            AwinProductFeedParser.parse(csvIn, consumer);
        }
    }

    private static String sanitizeFileName(String input) {
        if (input == null || input.isBlank()) {
            return "unknown";
        }

        return input
                .trim()
                // verbotene Zeichen (Windows + Unix)
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                // alles andere Unsichere
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String shortSha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
            // 12 hex chars reichen i.d.R. für Kollisionsarmut bei wenigen 1000 Feeds
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (Exception e) {
            return "nohash";
        }
    }
}
