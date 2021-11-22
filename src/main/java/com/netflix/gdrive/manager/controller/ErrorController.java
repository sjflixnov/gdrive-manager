package com.netflix.gdrive.manager.controller;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * A generic error page
 */
@Controller
public class ErrorController {

    @GetMapping({"/error" })
    public String files(Model model, OAuth2AuthenticationToken token) {
        model.addAttribute("userName", token.getPrincipal().getAttribute("name"));
        return "errorPage";
    }
}
