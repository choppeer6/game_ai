package com.nuaa.gameai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dataset_value_row")
public class DatasetValueRow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long importId;
    private Long ts;
    private String agentId;
    private String action;
    private Double valueScore;
    private String extraJson;
}
