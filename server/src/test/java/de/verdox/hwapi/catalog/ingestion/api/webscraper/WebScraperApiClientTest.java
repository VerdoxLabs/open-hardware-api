package de.verdox.hwapi.catalog.ingestion.api.webscraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WebScraperApiClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsAuthenticatedRawRequestAndReturnsHtml() throws Exception {
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/raw", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "<html><body>component</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        WebScraperApiClient client = new WebScraperApiClient(
                "http://localhost:" + server.getAddress().getPort(), "secret", "auto",
                Duration.ofSeconds(5), new ObjectMapper());

        assertThat(client.fetchHtml("https://example.com/part"))
                .isEqualTo("<html><body>component</body></html>");
        assertThat(apiKey.get()).isEqualTo("secret");
        assertThat(requestBody.get()).contains("https://example.com/part").contains("auto");
    }

    @Test
    void requestsCatalogRowsThroughTheGenericWaitForSelectorOption() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/raw", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "<html><body>component</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        WebScraperApiClient client = new WebScraperApiClient(
                "http://localhost:" + server.getAddress().getPort(), "secret", "auto",
                Duration.ofSeconds(5), new ObjectMapper());

        client.fetchHtml("https://pcpartpicker.com/products/cpu/");

        assertThat(requestBody.get())
                .contains("\"engine\":\"js\"")
                .contains("\"waitForSelector\":\"#category_content tr.tr__product\"");
    }
}
