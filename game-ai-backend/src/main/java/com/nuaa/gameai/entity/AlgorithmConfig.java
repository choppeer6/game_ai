package com.nuaa.gameai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("algorithm_config")
public class AlgorithmConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateName;
    private String algoName;
    private String hyperParameters;
    private LocalDateTime createTime;
}
