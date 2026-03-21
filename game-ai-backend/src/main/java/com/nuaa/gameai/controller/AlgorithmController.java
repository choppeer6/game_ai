package com.nuaa.gameai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nuaa.gameai.dto.ApiResult;
import com.nuaa.gameai.entity.AlgorithmConfig;
import com.nuaa.gameai.service.AlgorithmConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/algorithms")
@RequiredArgsConstructor
public class AlgorithmController {

    private final AlgorithmConfigService algorithmConfigService;

    @GetMapping
    public ApiResult<Page<AlgorithmConfig>> page(@RequestParam(defaultValue = "1") int current,
                                                 @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(algorithmConfigService.page(current, size));
    }

    @GetMapping("/{id}")
    public ApiResult<AlgorithmConfig> get(@PathVariable Long id) {
        return ApiResult.ok(algorithmConfigService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ApiResult<Void> save(@RequestBody AlgorithmConfig cfg) {
        algorithmConfigService.save(cfg);
        return ApiResult.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        algorithmConfigService.delete(id);
        return ApiResult.ok();
    }
}
