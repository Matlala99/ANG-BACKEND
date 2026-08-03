package com.agc.cms.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        return Map.of(
            "status", "ok",
            "database", "connected",
            "backend", "Spring Boot (Java 17)"
        );
    }
}
