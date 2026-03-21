package com.nuaa.gameai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuaa.gameai.entity.AlgorithmConfig;
import com.nuaa.gameai.entity.SceneConfig;
import com.nuaa.gameai.entity.TrainingTask;
import com.nuaa.gameai.mapper.AlgorithmConfigMapper;
import com.nuaa.gameai.mapper.SceneConfigMapper;
import com.nuaa.gameai.mapper.TrainingLogMapper;
import com.nuaa.gameai.mapper.TrainingTaskMapper;
import com.nuaa.gameai.pythonclient.PythonEngineClient;
import com.nuaa.gameai.pythonclient.dto.TrainStartPayload;
import com.nuaa.gameai.util.TrainingTaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainingTaskService {

    private final TrainingTaskMapper trainingTaskMapper;
    private final TrainingLogMapper trainingLogMapper;
    private final SceneConfigMapper sceneConfigMapper;
    private final AlgorithmConfigMapper algorithmConfigMapper;
    private final ObjectMapper objectMapper;
    private final TrainingPythonInvoker trainingPythonInvoker;
    private final PythonEngineClient pythonEngineClient;

    public Page<TrainingTask> page(int current, int size) {
        return trainingTaskMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<TrainingTask>()
                .orderByDesc(TrainingTask::getCreateTime));
    }

    public TrainingTask get(String taskId) {
        return trainingTaskMapper.selectById(taskId);
    }

    @Transactional
    public TrainingTask createAndMaybeStart(Long sceneId, Long algoTemplateId, String algoName,
                                            String hyperParametersJson, boolean startImmediately) {
        SceneConfig scene = sceneConfigMapper.selectById(sceneId);
        if (scene == null) {
            throw new IllegalArgumentException("场景不存在");
        }
        String algo = algoName;
        String hyper = hyperParametersJson;
        if (algoTemplateId != null) {
            AlgorithmConfig tpl = algorithmConfigMapper.selectById(algoTemplateId);
            if (tpl != null) {
                algo = tpl.getAlgoName();
                hyper = tpl.getHyperParameters();
            }
        }
        if (!StringUtils.hasText(algo)) {
            throw new IllegalArgumentException("算法名称不能为空");
        }
        if (!StringUtils.hasText(hyper)) {
            hyper = "{}";
        }

        String taskId = "T-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long activeTraining = trainingTaskMapper.selectCount(new LambdaQueryWrapper<TrainingTask>()
                .eq(TrainingTask::getStatus, TrainingTaskStatus.TRAINING));

        TrainingTask task = new TrainingTask();
        task.setTaskId(taskId);
        task.setSceneId(sceneId);
        task.setAlgoTemplateId(algoTemplateId);
        task.setAlgoName(algo);
        task.setHyperParameters(hyper);
        if (!startImmediately) {
            task.setStatus(TrainingTaskStatus.PENDING);
        } else if (activeTraining > 0) {
            task.setStatus(TrainingTaskStatus.QUEUED);
        } else {
            task.setStatus(TrainingTaskStatus.TRAINING);
        }
        task.setQueueOrder(0);
        task.setCurrentEpoch(0);
        if (startImmediately && task.getStatus() == TrainingTaskStatus.TRAINING) {
            task.setStartTime(LocalDateTime.now());
        }
        task.setCreateTime(LocalDateTime.now());
        trainingTaskMapper.insert(task);

        if (startImmediately && task.getStatus() == TrainingTaskStatus.TRAINING) {
            TrainStartPayload payload = buildPayload(taskId, scene, algo, hyper);
            trainingPythonInvoker.startTrainAsync(taskId, payload);
        }
        return task;
    }

    public void start(String taskId) {
        TrainingTask task = trainingTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (task.getStatus() != TrainingTaskStatus.PENDING && task.getStatus() != TrainingTaskStatus.QUEUED) {
            throw new IllegalStateException("当前状态不可启动");
        }
        SceneConfig scene = sceneConfigMapper.selectById(task.getSceneId());
        if (scene == null) {
            throw new IllegalArgumentException("场景不存在");
        }
        long active = trainingTaskMapper.selectCount(new LambdaQueryWrapper<TrainingTask>()
                .eq(TrainingTask::getStatus, TrainingTaskStatus.TRAINING));
        if (active > 0) {
            trainingTaskMapper.update(null, new LambdaUpdateWrapper<TrainingTask>()
                    .eq(TrainingTask::getTaskId, taskId)
                    .set(TrainingTask::getStatus, TrainingTaskStatus.QUEUED));
            return;
        }
        trainingTaskMapper.update(null, new LambdaUpdateWrapper<TrainingTask>()
                .eq(TrainingTask::getTaskId, taskId)
                .set(TrainingTask::getStatus, TrainingTaskStatus.TRAINING)
                .set(TrainingTask::getStartTime, LocalDateTime.now())
                .set(TrainingTask::getErrorMessage, null));
        TrainStartPayload payload = buildPayload(taskId, scene, task.getAlgoName(), task.getHyperParameters());
        trainingPythonInvoker.startTrainAsync(taskId, payload);
    }

    public void pause(String taskId) {
        trainingTaskMapper.update(null, new LambdaUpdateWrapper<TrainingTask>()
                .eq(TrainingTask::getTaskId, taskId)
                .set(TrainingTask::getStatus, TrainingTaskStatus.PAUSED));
        // Python 侧
        try {
            pythonEngineClient.pauseTrain(taskId);
        } catch (Exception ignored) {
        }
    }

    public void resume(String taskId) {
        trainingTaskMapper.update(null, new LambdaUpdateWrapper<TrainingTask>()
                .eq(TrainingTask::getTaskId, taskId)
                .set(TrainingTask::getStatus, TrainingTaskStatus.TRAINING));
        try {
            pythonEngineClient.resumeTrain(taskId);
        } catch (Exception ignored) {
        }
    }

    public void stop(String taskId) {
        trainingTaskMapper.update(null, new LambdaUpdateWrapper<TrainingTask>()
                .eq(TrainingTask::getTaskId, taskId)
                .set(TrainingTask::getStatus, TrainingTaskStatus.STOPPED)
                .set(TrainingTask::getEndTime, LocalDateTime.now()));
        try {
            pythonEngineClient.stopTrain(taskId);
        } catch (Exception ignored) {
        }
    }

    private TrainStartPayload buildPayload(String taskId, SceneConfig scene, String algoName, String hyperJson) {
        try {
            Map<String, Object> sceneMap = new HashMap<>();
            sceneMap.put("scene_id", scene.getId());
            sceneMap.put("map_width", scene.getMapWidth());
            sceneMap.put("map_height", scene.getMapHeight());
            sceneMap.put("red_count", scene.getRedDroneCount());
            sceneMap.put("blue_count", scene.getBlueDroneCount());
            if (StringUtils.hasText(scene.getSceneJson())) {
                sceneMap.put("extra", objectMapper.readValue(scene.getSceneJson(), new TypeReference<Map<String, Object>>() {
                }));
            }
            Map<String, Object> algoMap = objectMapper.readValue(hyperJson, new TypeReference<Map<String, Object>>() {
            });
            algoMap.put("algo_name", algoName);
            return TrainStartPayload.builder()
                    .taskId(taskId)
                    .sceneConfig(sceneMap)
                    .algoConfig(algoMap)
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("配置解析失败: " + e.getMessage());
        }
    }

    public List<com.nuaa.gameai.entity.TrainingLog> logs(String taskId) {
        return trainingLogMapper.selectList(new LambdaQueryWrapper<com.nuaa.gameai.entity.TrainingLog>()
                .eq(com.nuaa.gameai.entity.TrainingLog::getTaskId, taskId)
                .orderByAsc(com.nuaa.gameai.entity.TrainingLog::getEpoch));
    }

    /**
     * 当前无训练中任务时，启动下一条排队任务（由 Redis 训练完成消息触发）。
     */
    public void tryStartNextQueued() {
        long active = trainingTaskMapper.selectCount(new LambdaQueryWrapper<TrainingTask>()
                .eq(TrainingTask::getStatus, TrainingTaskStatus.TRAINING));
        if (active > 0) {
            return;
        }
        TrainingTask next = trainingTaskMapper.selectOne(new LambdaQueryWrapper<TrainingTask>()
                .eq(TrainingTask::getStatus, TrainingTaskStatus.QUEUED)
                .orderByAsc(TrainingTask::getCreateTime)
                .last("LIMIT 1"));
        if (next != null) {
            start(next.getTaskId());
        }
    }
}
