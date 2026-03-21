package com.nuaa.gameai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("training_log")
public class TrainingLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private Integer epoch;
    private Double redWinRate;
    private Double cumulativeReward;
    private Double lossValue;
    private Double valueEstimate;
    private String rawMetrics;
    private LocalDateTime createTime;
}
