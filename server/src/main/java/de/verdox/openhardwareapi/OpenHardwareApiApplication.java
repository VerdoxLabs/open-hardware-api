package de.verdox.openhardwareapi;

import de.verdox.openhardwareapi.model.CPU;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpenHardwareApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpenHardwareApiApplication.class, args);
    }
}
