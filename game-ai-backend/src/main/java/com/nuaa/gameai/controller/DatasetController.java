package com.nuaa.gameai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nuaa.gameai.dto.ApiResult;
import com.nuaa.gameai.dto.DatasetIngestRequest;
import com.nuaa.gameai.entity.DatasetImport;
import com.nuaa.gameai.entity.DatasetValueRow;
import com.nuaa.gameai.service.DatasetImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetImportService datasetImportService;

    @PostMapping("/ingest")
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ApiResult<DatasetImport> ingest(@RequestBody DatasetIngestRequest req) {
        return ApiResult.ok(datasetImportService.ingestFromApi(req.getName(), req.getRows()));
    }

    @GetMapping
    public ApiResult<Page<DatasetImport>> page(@RequestParam(defaultValue = "1") int current,
                                               @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(datasetImportService.page(current, size));
    }

    @GetMapping("/{importId}/rows")
    public ApiResult<List<DatasetValueRow>> rows(@PathVariable Long importId,
                                                 @RequestParam(defaultValue = "500") int limit) {
        return ApiResult.ok(datasetImportService.rows(importId, limit));
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ApiResult<DatasetImport> upload(@RequestParam(required = false) String name,
                                           @RequestPart("file") MultipartFile file) throws Exception {
        return ApiResult.ok(datasetImportService.upload(name, file));
    }
}
