package com.nuaa.gameai.controller;

import com.nuaa.gameai.dto.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ApiResult<Map<String, String>> health() {
        return ApiResult.ok(Map.of("status", "UP", "service", "game-ai-backend"));
    }
}
