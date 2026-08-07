package com.agentassist.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the knowledge base admin portal.
 * <p>
 * The page itself is a static file; every data call it makes goes through
 * {@link KnowledgeBaseController}. Mapped explicitly because Spring's static
 * resource handler will not resolve the extensionless path {@code /admin}.
 */
@Hidden
@Controller
public class AdminPortalController {

    @GetMapping("/admin")
    public String adminPortal() {
        return "forward:/admin.html";
    }
}
