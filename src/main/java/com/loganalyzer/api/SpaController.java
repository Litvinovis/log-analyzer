package com.loganalyzer.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {

    // Forward all non-API single-level paths to index.html (e.g. /errors, /stats)
    @RequestMapping(value = "/{path:^(?!api|actuator).*}")
    public String forwardSingleLevel() {
        return "forward:/index.html";
    }

    // Forward all non-API multi-level paths to index.html (e.g. /some/nested/route)
    @RequestMapping(value = "/{path:^(?!api|actuator).*}/**")
    public String forwardMultiLevel() {
        return "forward:/index.html";
    }
}
