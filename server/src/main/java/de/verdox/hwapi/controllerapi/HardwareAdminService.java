package de.verdox.hwapi.controllerapi;

import de.verdox.hwapi.client.admin.HardwareAdminDtos;

public interface HardwareAdminService {

    HardwareAdminDtos.BackendStatus getStatus();

    HardwareAdminDtos.BackendStats getStats();

    /**
     * @return accepted=false => already running
     */
    HardwareAdminDtos.ActionResult restartScraping();

    /**
     * @return accepted=false => z.B. wenn gerade Scrape läuft und du blocken willst
     */
    HardwareAdminDtos.ActionResult deleteAllHardwareData();
}