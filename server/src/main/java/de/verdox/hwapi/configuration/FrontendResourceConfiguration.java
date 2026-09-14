package de.verdox.hwapi.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.stream.Stream;

/** Allows the dev profile to use the Next export without copying it into the JAR. */
@Configuration
@Profile("dev")
public class FrontendResourceConfiguration implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String[] devExportLocations = Stream.of(
                        Path.of("database-frontend", "out"),
                        Path.of("..", "database-frontend", "out"),
                        Path.of("..", "..", "database-frontend", "out"))
                .map(Path::toAbsolutePath)
                .map(path -> path.toUri().toString())
                .toArray(String[]::new);

        registry.addResourceHandler("/**")
                .addResourceLocations(Stream.concat(Stream.of("classpath:/static/"),
                                Stream.of(devExportLocations))
                        .toArray(String[]::new));
    }
}
