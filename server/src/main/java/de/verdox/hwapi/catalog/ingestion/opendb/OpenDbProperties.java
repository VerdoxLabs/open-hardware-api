package de.verdox.hwapi.catalog.ingestion.opendb;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "hwapi.opendb")
public record OpenDbProperties(
        boolean enabled,
        String repository,
        String branch,
        Path checkoutDirectory,
        Path schemaDirectory
) {
    public OpenDbProperties {
        repository = repository == null || repository.isBlank()
                ? "https://github.com/buildcores/buildcores-open-db.git" : repository;
        branch = branch == null || branch.isBlank() ? "main" : branch;
        checkoutDirectory = checkoutDirectory == null ? Path.of("buildcores-open-db-main") : checkoutDirectory;
        schemaDirectory = schemaDirectory == null ? Path.of("schemas") : schemaDirectory;
    }
}
