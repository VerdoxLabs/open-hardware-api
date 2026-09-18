package de.verdox.hwapi.catalog.ingestion.opendb;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Keeps a local, reproducible checkout of the upstream OpenDB repository. */
@Component
public class OpenDbRepository {
    private final OpenDbProperties properties;

    public OpenDbRepository(OpenDbProperties properties) {
        this.properties = properties;
    }

    public Path sync() throws IOException, InterruptedException {
        return sync((phase, current, total) -> { });
    }

    public Path sync(ProgressListener progress) throws IOException, InterruptedException {
        Path checkout = properties.checkoutDirectory().toAbsolutePath().normalize();
        Files.createDirectories(checkout.getParent());
        cleanupTemporaryArtifacts(checkout.getParent());
        try {
            progress.update("REPOSITORY_SYNC", 0, 0);
            syncWithGit(checkout, progress);
        } catch (IOException gitFailure) {
            // The production image intentionally does not need the git CLI. GitHub's
            // source archive gives us the same open-db and schemas directories.
            syncFromArchive(checkout, gitFailure, progress);
        }
        return checkout;
    }

    private void syncWithGit(Path checkout, ProgressListener progress) throws IOException, InterruptedException {
        if (!Files.isDirectory(checkout.resolve(".git"))) {
            progress.update("REPOSITORY_SYNC", 0, 3);
            run(checkout.getParent(), List.of("git", "clone", "--branch", properties.branch(),
                    "--filter=blob:none", "--no-checkout", properties.repository(), checkout.toString()));
            progress.update("REPOSITORY_SYNC", 1, 3);
            run(checkout, List.of("git", "sparse-checkout", "set", "open-db", "schemas"));
            progress.update("REPOSITORY_SYNC", 2, 3);
            run(checkout, List.of("git", "checkout", properties.branch()));
            progress.update("REPOSITORY_SYNC", 3, 3);
        } else {
            progress.update("REPOSITORY_SYNC", 0, 2);
            run(checkout, List.of("git", "fetch", "--prune", "origin", properties.branch()));
            progress.update("REPOSITORY_SYNC", 1, 2);
            run(checkout, List.of("git", "reset", "--hard", "origin/" + properties.branch()));
            progress.update("REPOSITORY_SYNC", 2, 2);
        }
    }

    private void syncFromArchive(Path checkout, IOException gitFailure, ProgressListener progress) throws IOException, InterruptedException {
        URI archive = archiveUri();
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(archive)
                .header("User-Agent", "open-hardware-api-opendb-import")
                .GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("OpenDB konnte weder per git noch als Archiv geladen werden. Git: "
                    + gitFailure.getMessage() + "; Archiv HTTP " + response.statusCode());
        }

        Path extraction = checkout.getParent().resolve(".opendb-extract-" + UUID.randomUUID());
        Path archiveFile = Files.createTempFile(checkout.getParent(), ".opendb-", ".zip");
        try {
            long total = response.headers().firstValueAsLong("content-length").orElse(0L);
            long current = 0;
            progress.update("DOWNLOAD", 0, total);
            try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(archiveFile)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                    current += read;
                    progress.update("DOWNLOAD", current, total);
                }
            }
            progress.update("EXTRACT", current, total);
            unzip(archiveFile, extraction, progress);
            Path extractedRoot;
            try (var entries = Files.list(extraction)) {
                extractedRoot = entries.filter(Files::isDirectory).findFirst().orElse(extraction);
            }
            if (!Files.isDirectory(extractedRoot.resolve("open-db")) || !Files.isDirectory(extractedRoot.resolve("schemas"))) {
                throw new IOException("OpenDB-Archiv enthält nicht die erwarteten open-db- und schemas-Verzeichnisse.");
            }
            deleteRecursively(checkout);
            Files.move(extractedRoot, checkout);
        } finally {
            Files.deleteIfExists(archiveFile);
            deleteRecursively(extraction);
            cleanupTemporaryArtifacts(checkout.getParent());
        }
    }

    private URI archiveUri() {
        String repository = properties.repository().trim();
        if (repository.endsWith(".zip")) return URI.create(repository);
        String github = repository.replaceFirst("^https?://github\\.com/", "");
        if (github.equals(repository)) {
            throw new IllegalArgumentException("Archiv-Fallback unterstützt derzeit GitHub-Repositories oder .zip-URLs: " + repository);
        }
        if (github.endsWith(".git")) github = github.substring(0, github.length() - 4);
        String branch = URLEncoder.encode(properties.branch(), StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create("https://github.com/" + github + "/archive/refs/heads/" + branch + ".zip");
    }

    private static void unzip(Path archive, Path destination, ProgressListener progress) throws IOException {
        Files.createDirectories(destination);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            int total = zip.size();
            int current = 0;
            progress.update("EXTRACT", 0, total);
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.startsWith(destination)) throw new IOException("Unsicherer OpenDB-Archivpfad: " + entry.getName());
                if (entry.isDirectory()) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    try (InputStream input = zip.getInputStream(entry)) {
                        Files.copy(input, target);
                    }
                }
                progress.update("EXTRACT", ++current, total);
            }
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var paths = Files.walk(path)) {
            for (Path entry : paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static void cleanupTemporaryArtifacts(Path parent) throws IOException {
        if (!Files.isDirectory(parent)) return;
        try (var entries = Files.list(parent)) {
            for (Path entry : entries.filter(OpenDbRepository::isTemporaryArtifact).toList()) {
                deleteRecursively(entry);
            }
        }
    }

    private static boolean isTemporaryArtifact(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(".opendb-extract-")
                || name.startsWith(".opendb-checkout-")
                || (name.startsWith(".opendb-") && name.endsWith(".zip"));
    }

    private void run(Path workingDirectory, List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int code = process.waitFor();
        if (code != 0) {
            throw new IOException("OpenDB git command failed (" + code + "): " + output.trim());
        }
    }

    @FunctionalInterface
    public interface ProgressListener {
        void update(String phase, long current, long total);
    }
}
