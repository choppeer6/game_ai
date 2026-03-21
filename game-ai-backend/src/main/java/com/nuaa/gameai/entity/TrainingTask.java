package com.nuaa.gameai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("training_task")
public class TrainingTask {
    @TableId
    private String taskId;
    private Long sceneId;
    private Long algoTemplateId;
    private String algoName;
    private String hyperParameters;
    private Integer status;
    private Integer queueOrder;
    private Integer currentEpoch;
    private String errorMessage;
    private String checkpointPath;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
}
