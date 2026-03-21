package com.nuaa.gameai.controller;

import com.nuaa.gameai.dto.ApiResult;
import com.nuaa.gameai.service.CompareReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/compare")
@RequiredArgsConstructor
public class CompareController {

    private final CompareReportService compareReportService;

    @PostMapping
    public ApiResult<Map<String, Object>> compare(@RequestBody List<String> taskIds) {
        return ApiResult.ok(compareReportService.compare(taskIds));
    }

    @GetMapping("/export")
    public ApiResult<String> export(@RequestParam("taskIds") List<String> taskIds) {
        return ApiResult.ok(compareReportService.buildHtmlReport(taskIds));
    }
}
