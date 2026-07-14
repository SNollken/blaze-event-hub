package com.blaze.eventhub;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forward all non-API, non-static requests to the React SPA index.html.
 * Spring Boot serves static resources from /static/ by default.
 */
@Controller
public class SpaController {

    @RequestMapping(value = {"/", "/events/**", "/settings/**", "/studio/**", "/{path:[^\\.]*}"})
    public String forward() {
        return "forward:/index.html";
    }
}
