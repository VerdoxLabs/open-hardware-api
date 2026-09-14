package de.verdox.hwapi.configuration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Builds the static Next export automatically for a local dev start. */
@Component
@Profile("dev")
public class FrontendBuildRunner implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        Path frontend = findFrontendDirectory();
        if (frontend == null) {
            System.err.println("Frontend build skipped: database-frontend directory not found");
            return;
        }
        if (Files.isRegularFile(frontend.resolve("out/index.html"))) return;

        try {
            if (!Files.isDirectory(frontend.resolve("node_modules"))) {
                run(frontend, npmCommand(), "ci");
            }
            run(frontend, npmCommand(), "run", "build");
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not build database-frontend. Run 'npm run build' in database-frontend.", e);
        }
    }

    private static void run(Path directory, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(directory.toFile()).inheritIO().start();
        if (process.waitFor() != 0) {
            throw new IOException("Frontend command failed: " + String.join(" ", command));
        }
    }

    private static String npmCommand() {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "npm.cmd" : "npm";
    }

    private static Path findFrontendDirectory() {
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 4 && current != null; i++, current = current.getParent()) {
            Path candidate = current.resolve("database-frontend");
            if (Files.isDirectory(candidate)) return candidate;
        }
        return null;
    }
}
