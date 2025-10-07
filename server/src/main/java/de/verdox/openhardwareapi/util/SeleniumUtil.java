package de.verdox.openhardwareapi.util;

import de.verdox.openhardwareapi.component.service.ScrapingService;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.logging.Level;

public class SeleniumUtil {
    public static WebDriver create(ChromeOptions capabilities) throws MalformedURLException {
        try {
            ScrapingService.LOGGER.log(Level.INFO, "Trying to create chrome driver");
            return new ChromeDriver(capabilities);
        } catch (Exception e) {
            var hub = System.getenv().getOrDefault("SELENIUM_REMOTE_URL", "http://localhost:4444");
            try {
                ScrapingService.LOGGER.log(Level.INFO, "Creating remote selenium driver at " + hub);
                return new RemoteWebDriver(URI.create(hub).toURL(), capabilities);
            } catch (Throwable ex) {
                ScrapingService.LOGGER.log(Level.WARNING, "Could not connect to remote URL " + hub + ".", ex);
                return null;
            }
        }
    }
}
