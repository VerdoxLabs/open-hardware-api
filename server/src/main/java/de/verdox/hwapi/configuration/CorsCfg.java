package de.verdox.hwapi.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsCfg implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    public CorsCfg(@Value("${hwapi.cors.allowed-origins:https://pc-flipping.com,https://www.pc-flipping.com,http://localhost:*}") String origins) {
        this.allowedOrigins = origins.split(",");
    }

    @Override public void addCorsMappings(CorsRegistry r) {
        r.addMapping("/api/v1/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
