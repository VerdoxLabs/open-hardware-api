package de.verdox.hwapi.icecat;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/admin/icecat")
@RequiredArgsConstructor
public class IcecatAdminController {
    private final IcecatLookupService service;

    @GetMapping("/status")
    public IcecatLookupService.IcecatStatus status() { return service.status(); }

    @PostMapping("/lookup")
    public IcecatLookupService.IcecatLookupResponse lookup(@RequestBody IcecatLookupService.IcecatLookupRequest request) throws IOException, InterruptedException {
        return service.lookup(request);
    }
}
