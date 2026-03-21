package com.nuaa.gameai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nuaa.gameai.dto.ApiResult;
import com.nuaa.gameai.entity.SceneConfig;
import com.nuaa.gameai.service.SceneConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scenes")
@RequiredArgsConstructor
public class SceneController {

    private final SceneConfigService sceneConfigService;

    @GetMapping
    public ApiResult<Page<SceneConfig>> page(@RequestParam(defaultValue = "1") int current,
                                             @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(sceneConfigService.page(current, size));
    }

    @GetMapping("/{id}")
    public ApiResult<SceneConfig> get(@PathVariable Long id) {
        return ApiResult.ok(sceneConfigService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ApiResult<Void> save(@RequestBody SceneConfig scene) {
        sceneConfigService.save(scene);
        return ApiResult.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        sceneConfigService.delete(id);
        return ApiResult.ok();
    }
}
