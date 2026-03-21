package com.nuaa.gameai.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nuaa.gameai.entity.TrainingTask;
import com.nuaa.gameai.mapper.TrainingTaskMapper;
import com.nuaa.gameai.pythonclient.PythonEngineClient;
import com.nuaa.gameai.pythonclient.dto.TrainStartPayload;
import com.nuaa.gameai.util.TrainingTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingPythonInvoker {

    private final PythonEngineClient pythonEngineClient;
    private final TrainingTaskMapper trainingTaskMapper;

    @Async
    public void startTrainAsync(String taskId, TrainStartPayload payload) {
        try {
            pythonEngineClient.startTrain(payload);
        } catch (Exception e) {
            log.warn("Python 训练启动失败 taskId={}", taskId, e);
            trainingTaskMapper.update(null, new LambdaUpdateWrapper<TrainingTask>()
                    .eq(TrainingTask::getTaskId, taskId)
                    .set(TrainingTask::getStatus, TrainingTaskStatus.FAILED)
                    .set(TrainingTask::getErrorMessage, truncate(e.getMessage()))
                    .set(TrainingTask::getEndTime, LocalDateTime.now()));
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 1800 ? s.substring(0, 1800) : s;
    }
}
