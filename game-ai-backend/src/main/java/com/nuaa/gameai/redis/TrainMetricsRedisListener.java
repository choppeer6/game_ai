package com.nuaa.gameai.redis;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuaa.gameai.entity.TrainingLog;
import com.nuaa.gameai.entity.TrainingTask;
import com.nuaa.gameai.mapper.TrainingLogMapper;
import com.nuaa.gameai.mapper.TrainingTaskMapper;
import com.nuaa.gameai.service.TrainingTaskService;
import com.nuaa.gameai.util.TrainingTaskStatus;
import com.nuaa.gameai.websocket.WebSocketSessionRegistry;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
public class TrainMetricsRedisListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final TrainingLogMapper trainingLogMapper;
    private final TrainingTaskMapper trainingTaskMapper;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final TrainingTaskService trainingTaskService;

    public TrainMetricsRedisListener(
            ObjectMapper objectMapper,
            TrainingLogMapper trainingLogMapper,
            TrainingTaskMapper trainingTaskMapper,
            WebSocketSessionRegistry webSocketSessionRegistry,
            @Lazy TrainingTaskService trainingTaskService) {
        this.objectMapper = objectMapper;
        this.trainingLogMapper = trainingLogMapper;
        this.trainingTaskMapper = trainingTaskMapper;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.trainingTaskService = trainingTaskService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            JsonNode n = objectMapper.readTree(body);
            String taskId = n.path("task_id").asText(null);
            if (taskId == null || taskId.isEmpty()) {
                return;
            }
            int epoch = n.path("epoch").asInt(0);

            if (epoch < 0 || n.has("error")) {
                String err = n.has("error") ? n.get("error").asText() : "训练引擎异常";
                trainingTaskMapper.update(null, new LambdaUpdateWrapper<TrainingTask>()
                        .eq(TrainingTask::getTaskId, taskId)
                        .set(TrainingTask::getStatus, TrainingTaskStatus.FAILED)
                        .set(TrainingTask::getErrorMessage, err != null && err.length() > 1800 ? err.substring(0, 1800) : err)
                        .set(TrainingTask::getEndTime, LocalDateTime.now()));
                webSocketSessionRegistry.broadcastTask(taskId, body);
                trainingTaskService.tryStartNextQueued();
                return;
            }

            Double redWin = n.has("red_win_rate") ? n.get("red_win_rate").asDouble() : null;
            Double loss = n.has("loss_value") ? n.get("loss_value").asDouble() : null;
            Double cumReward = n.has("cumulative_reward") ? n.get("cumulative_reward").asDouble() : null;
            Double valueEst = n.has("value_estimate") ? n.get("value_estimate").asDouble() : null;
            boolean done = n.path("done").asBoolean(false);
            String ck = n.has("checkpoint_path") ? n.get("checkpoint_path").asText(null) : null;

            TrainingLog log = new TrainingLog();
            log.setTaskId(taskId);
            log.setEpoch(epoch);
            log.setRedWinRate(redWin);
            log.setLossValue(loss);
            log.setCumulativeReward(cumReward);
            log.setValueEstimate(valueEst);
            log.setRawMetrics(body);
            log.setCreateTime(LocalDateTime.now());
            trainingLogMapper.insert(log);

            var uw = new LambdaUpdateWrapper<TrainingTask>()
                    .eq(TrainingTask::getTaskId, taskId)
                    .set(TrainingTask::getCurrentEpoch, epoch);
            if (ck != null && !ck.isEmpty()) {
                uw.set(TrainingTask::getCheckpointPath, ck);
            }
            trainingTaskMapper.update(null, uw);

            webSocketSessionRegistry.broadcastTask(taskId, body);

            if (done) {
                trainingTaskMapper.update(null, new LambdaUpdateWrapper<TrainingTask>()
                        .eq(TrainingTask::getTaskId, taskId)
                        .set(TrainingTask::getStatus, TrainingTaskStatus.DONE)
                        .set(TrainingTask::getEndTime, LocalDateTime.now()));
                trainingTaskService.tryStartNextQueued();
            }
        } catch (Exception e) {
            // 解析失败不阻塞 Redis 线程
        }
    }
}
