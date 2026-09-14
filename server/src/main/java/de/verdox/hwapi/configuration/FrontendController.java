package de.verdox.hwapi.configuration;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the static Next export packaged into Spring's classpath. */
@Controller
public class FrontendController {
    @GetMapping(value = {"/", "/{path:^(?!api$)[^\\.]*}"})
    public String frontend() {
        return "forward:/index.html";
    }
}
