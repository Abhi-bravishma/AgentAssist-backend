package com.agentassist.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Agent Assist API",
                version = "1.0",
                description = "Real-time Multilingual Sentiment & Summary Analysis using OpenAI",
                contact = @Contact(name = "Akash", email = "support@agentanalysis.com")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local Development"),
                @Server(url = "http://74.225.250.214:8085", description = "VM Server"),
                @Server(url = "https://bravishma-sovereign.eastus.cloudapp.azure.com/agent-assist", description = "Azure Server"),
                @Server(url = "https://agent-assist.demosaiportal.com", description = "Azure Server"),
                @Server(url = "https://infobip.lab.bravishma.com/agentassist", description = "Infobip Server")
        },
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
