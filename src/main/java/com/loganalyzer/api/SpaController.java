package com.loganalyzer.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {

    // Forward all non-API paths without a file extension to index.html (e.g. /errors, /stats)
    // Paths with dots (index.html, main.js, etc.) are excluded to avoid infinite forward loops
    @RequestMapping(value = "/{path:^(?!api|actuator)[^\\.]*}")
    public String forwardSingleLevel() {
        return "forward:/index.html";
    }

    @RequestMapping(value = "/{path:^(?!api|actuator)[^\\.]*}/**")
    public String forwardMultiLevel() {
        return "forward:/index.html";
    }
}
