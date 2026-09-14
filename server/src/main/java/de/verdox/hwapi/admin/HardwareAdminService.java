package de.verdox.hwapi.admin;

import de.verdox.hwapi.integration.client.admin.HardwareAdminDtos;

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