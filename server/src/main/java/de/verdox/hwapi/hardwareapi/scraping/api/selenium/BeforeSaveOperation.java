package de.verdox.hwapi.hardwareapi.scraping.api.selenium;

import org.openqa.selenium.WebDriver;

public interface BeforeSaveOperation {
    void beforeSave(WebDriver driver);
}
