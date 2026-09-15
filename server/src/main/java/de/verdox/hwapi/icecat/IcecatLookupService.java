package de.verdox.hwapi.icecat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Small, deliberately read-only Icecat Live adapter for the admin test console. */
@Service
@RequiredArgsConstructor
public class IcecatLookupService {
    private final IcecatProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public IcecatStatus status() {
        return new IcecatStatus(properties.isConfigured(), properties.getLanguage(), properties.getBaseUrl());
    }

    public IcecatLookupResponse lookup(IcecatLookupRequest request) throws IOException, InterruptedException {
        if (!properties.isConfigured()) throw new IllegalStateException("Icecat is not configured. Set ICECAT_ENABLED, ICECAT_SHOP_NAME and ICECAT_API_TOKEN.");
        String gtin = normalizeGtin(request.gtin());
        String brand = text(request.brand());
        String mpn = text(request.mpn());
        if (gtin == null && (brand == null || mpn == null)) {
            throw new IllegalArgumentException("Bitte EAN/GTIN oder Marke und MPN angeben.");
        }

        ApiResult result = null;
        if (gtin != null) result = fetch("GTIN", Map.of("GTIN", gtin));
        if ((result == null || result.product() == null) && brand != null && mpn != null) {
            result = fetch("BRAND_MPN", Map.of("Brand", brand, "ProductCode", mpn));
        }
        if (result == null || result.product() == null) {
            return new IcecatLookupResponse("NOT_FOUND", result == null ? null : result.matchType(), null, null, List.of(), null,
                    result == null ? null : result.rawPreview(), "Kein passendes Icecat-Datenblatt gefunden.");
        }
        JsonNode product = result.product();
        String title = value(product, "title", "Title", "product_name", "ProductName", "name", "Name");
        String description = value(product, "description", "Description", "long_description", "LongDescription");
        JsonNode specs = first(product, "specifications", "Specifications", "features", "FeatureGroups");
        return new IcecatLookupResponse("MATCHED", result.matchType(), title, description, imageUrls(product),
                specs == null ? null : mapper.writeValueAsString(specs), result.rawPreview(), null);
    }

    private ApiResult fetch(String matchType, Map<String, String> search) throws IOException, InterruptedException {
        StringBuilder query = new StringBuilder("lang=").append(encode(properties.getLanguage()))
                .append("&shopname=").append(encode(properties.getShopName())).append("&content=");
        search.forEach((key, value) -> query.append('&').append(encode(key)).append('=').append(encode(value)));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + "?" + query))
                .timeout(Duration.ofSeconds(30)).header("Accept", "application/json").header("api-token", properties.getApiToken()).GET();
        if (!properties.getContentToken().isBlank()) request.header("content-token", properties.getContentToken());
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) return new ApiResult(matchType, null, preview(response.body()));
        if (response.statusCode() / 100 != 2) throw new IOException("Icecat returned HTTP " + response.statusCode() + ": " + preview(response.body()));
        JsonNode raw = mapper.readTree(response.body());
        return new ApiResult(matchType, unwrap(raw), preview(response.body()));
    }

    private JsonNode unwrap(JsonNode root) {
        if (root == null || root.isNull()) return null;
        for (String key : List.of("product", "Product", "data", "Data")) {
            JsonNode candidate = root.get(key);
            if (candidate == null || candidate.isNull()) continue;
            if (candidate.isArray()) return candidate.isEmpty() ? null : candidate.get(0);
            if (candidate.isObject()) return candidate.has("product") ? candidate.get("product") : candidate;
        }
        return root.isObject() && root.size() == 0 ? null : root;
    }

    private List<String> imageUrls(JsonNode product) {
        Set<String> urls = new LinkedHashSet<>();
        collectImages(first(product, "images", "Images", "image", "Image", "gallery", "Gallery"), urls);
        return List.copyOf(urls);
    }

    private void collectImages(JsonNode node, Set<String> urls) {
        if (node == null || node.isNull()) return;
        if (node.isTextual() && node.asText().startsWith("http")) { urls.add(node.asText()); return; }
        if (node.isObject()) {
            String url = value(node, "url", "Url", "URL", "original", "Original");
            if (url != null && url.startsWith("http")) urls.add(url);
            node.elements().forEachRemaining(child -> collectImages(child, urls));
        } else if (node.isArray()) node.forEach(child -> collectImages(child, urls));
    }

    private static JsonNode first(JsonNode node, String... names) { for (String name : names) if (node.has(name)) return node.get(name); return null; }
    private static String value(JsonNode node, String... names) { JsonNode found = first(node, names); return found != null && found.isValueNode() && !found.asText().isBlank() ? found.asText().trim() : null; }
    private static String text(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String normalizeGtin(String value) { String digits = value == null ? "" : value.replaceAll("[^0-9]", ""); return digits.length() >= 8 && digits.length() <= 14 ? digits : null; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String preview(String body) { return body == null ? "" : body.substring(0, Math.min(body.length(), 30_000)); }

    private record ApiResult(String matchType, JsonNode product, String rawPreview) { }
    public record IcecatStatus(boolean configured, String language, String baseUrl) { }
    public record IcecatLookupRequest(String gtin, String brand, String mpn) { }
    public record IcecatLookupResponse(String status, String matchType, String title, String description, List<String> images,
                                       String specificationsJson, String rawPreview, String message) { }
}
