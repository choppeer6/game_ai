package com.nuaa.gameai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nuaa.gameai.entity.TrainingLog;
import com.nuaa.gameai.entity.TrainingTask;
import com.nuaa.gameai.mapper.TrainingLogMapper;
import com.nuaa.gameai.mapper.TrainingTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompareReportService {

    private final TrainingTaskMapper trainingTaskMapper;
    private final TrainingLogMapper trainingLogMapper;

    public Map<String, Object> compare(List<String> taskIds) {
        Map<String, Object> result = new HashMap<>();
        Map<String, List<TrainingLog>> series = new HashMap<>();
        for (String tid : taskIds) {
            TrainingTask t = trainingTaskMapper.selectById(tid);
            if (t != null) {
                List<TrainingLog> logs = trainingLogMapper.selectList(new LambdaQueryWrapper<TrainingLog>()
                        .eq(TrainingLog::getTaskId, tid)
                        .orderByAsc(TrainingLog::getEpoch));
                series.put(tid, logs);
            }
        }
        result.put("series", series);
        return result;
    }

    public String buildHtmlReport(List<String> taskIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'><title>战术策略报告</title></head><body>");
        sb.append("<h1>高价值战术决策摘要</h1>");
        sb.append("<p>离线评估：训练完成后可在 Python 引擎调用 <code>POST /api/eval/rollout</code>（传入 checkpoint_dir 与可选 scene_config）获取固定种子下的胜率与平均回报。</p>");
        for (String tid : taskIds) {
            TrainingTask t = trainingTaskMapper.selectById(tid);
            if (t == null) {
                continue;
            }
            sb.append("<h2>任务 ").append(tid).append("</h2>");
            sb.append("<p>算法: ").append(t.getAlgoName()).append("</p>");
            if (t.getCheckpointPath() != null) {
                sb.append("<p>策略检查点目录: ").append(t.getCheckpointPath()).append("</p>");
            }
            List<TrainingLog> logs = trainingLogMapper.selectList(new LambdaQueryWrapper<TrainingLog>()
                    .eq(TrainingLog::getTaskId, tid)
                    .orderByDesc(TrainingLog::getEpoch)
                    .last("LIMIT 5"));
            sb.append("<ul>");
            for (TrainingLog l : logs) {
                sb.append("<li>epoch ").append(l.getEpoch())
                        .append(" 胜率 ").append(l.getRedWinRate())
                        .append(" 价值 ").append(l.getValueEstimate())
                        .append("</li>");
            }
            sb.append("</ul>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    public List<String> listTaskIds() {
        return trainingTaskMapper.selectList(new LambdaQueryWrapper<TrainingTask>()
                        .orderByDesc(TrainingTask::getCreateTime))
                .stream()
                .map(TrainingTask::getTaskId)
                .collect(Collectors.toList());
    }
}
