package de.verdox.hwapi.catalog.ingestion.api.selenium;

import org.openqa.selenium.WebDriver;

public interface BeforeSaveOperation {
    void beforeSave(WebDriver driver);
}
