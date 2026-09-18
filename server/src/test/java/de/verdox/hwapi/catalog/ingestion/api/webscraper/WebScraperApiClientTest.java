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

    @Test
    void forcesEbayRequestsThroughAkamaiBrowserEngine() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/raw", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "<html><body>results</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        WebScraperApiClient client = new WebScraperApiClient(
                "http://localhost:" + server.getAddress().getPort(), "secret", "auto",
                Duration.ofSeconds(5), new ObjectMapper());

        client.fetchHtml("https://www.ebay.de/sch/164/i.html?_nkw=7800X3D");

        assertThat(requestBody.get()).contains("\"engine\":\"akamai\"");
    }

    @Test
    void forcesKleinanzeigenRequestsThroughBrowserEngine() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/raw", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "<html><body>results</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        WebScraperApiClient client = new WebScraperApiClient(
                "http://localhost:" + server.getAddress().getPort(), "secret", "auto",
                Duration.ofSeconds(5), new ObjectMapper());

        client.fetchHtml("https://www.kleinanzeigen.de/s-AMD+Ryzen+7+7800X3D/k0.html?an=on&px=1");

        assertThat(requestBody.get()).contains("\"engine\":\"cloudflare\"");
    }

    @Test
    void encodesUnsafeCharactersAndUnicodeInProductUrls() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/raw", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "<html><body>product</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        WebScraperApiClient client = new WebScraperApiClient(
                "http://localhost:" + server.getAddress().getPort(), "secret", "auto",
                Duration.ofSeconds(5), new ObjectMapper());

        client.fetchHtml("https://www.pc-kombo.com/us/product/gpu/4250812421364_EVGA GeForce GTX 1080 Ti K|NGP|N Gaming");

        assertThat(requestBody.get()).contains("https://www.pc-kombo.com/us/product/gpu/4250812421364_EVGA%20GeForce%20GTX%201080%20Ti%20K%7CNGP%7CN%20Gaming");
    }
}
