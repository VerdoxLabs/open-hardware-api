package de.verdox.hwapi.icecat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "icecat")
public class IcecatProperties {
    private boolean enabled;
    private String baseUrl = "https://live.icecat.biz/api";
    private String language = "DE";
    private String shopName = "";
    private String apiToken = "";
    private String contentToken = "";

    public boolean isConfigured() {
        return enabled && !shopName.isBlank() && !apiToken.isBlank();
    }
}
