package com.nuaa.gameai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nuaa.gameai.dto.ApiResult;
import com.nuaa.gameai.dto.TrainingCreateRequest;
import com.nuaa.gameai.entity.TrainingLog;
import com.nuaa.gameai.entity.TrainingTask;
import com.nuaa.gameai.service.TrainingTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/training")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingTaskService trainingTaskService;

    @GetMapping("/tasks")
    public ApiResult<Page<TrainingTask>> page(@RequestParam(defaultValue = "1") int current,
                                               @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(trainingTaskService.page(current, size));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResult<TrainingTask> get(@PathVariable String taskId) {
        return ApiResult.ok(trainingTaskService.get(taskId));
    }

    @PostMapping("/tasks")
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ApiResult<TrainingTask> create(@RequestBody TrainingCreateRequest req) {
        return ApiResult.ok(trainingTaskService.createAndMaybeStart(
                req.getSceneId(),
                req.getAlgoTemplateId(),
                req.getAlgoName(),
                req.getHyperParameters(),
                req.isStartImmediately()));
    }

    @PostMapping("/tasks/{taskId}/start")
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ApiResult<Void> start(@PathVariable String taskId) {
        trainingTaskService.start(taskId);
        return ApiResult.ok();
    }

    @PostMapping("/tasks/{taskId}/pause")
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ApiResult<Void> pause(@PathVariable String taskId) {
        trainingTaskService.pause(taskId);
        return ApiResult.ok();
    }

    @PostMapping("/tasks/{taskId}/resume")
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ApiResult<Void> resume(@PathVariable String taskId) {
        trainingTaskService.resume(taskId);
        return ApiResult.ok();
    }

    @PostMapping("/tasks/{taskId}/stop")
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ApiResult<Void> stop(@PathVariable String taskId) {
        trainingTaskService.stop(taskId);
        return ApiResult.ok();
    }

    @GetMapping("/tasks/{taskId}/logs")
    public ApiResult<List<TrainingLog>> logs(@PathVariable String taskId) {
        return ApiResult.ok(trainingTaskService.logs(taskId));
    }
}
