package com.loganalyzer.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {

    // Forward single-level paths without a file extension to index.html (e.g. /errors, /stats).
    // Multi-level paths are intentionally NOT forwarded so that /assets/main.js and other
    // static resources are still served by Spring's resource handler.
    @RequestMapping(value = "/{path:^(?!api|actuator)[^\\.]*}")
    public String forward() {
        return "forward:/index.html";
    }
}
