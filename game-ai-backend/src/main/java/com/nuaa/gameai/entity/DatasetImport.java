package com.nuaa.gameai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dataset_import")
public class DatasetImport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String sourceType;
    private String originalFilename;
    private String storagePath;
    private String status;
    private Integer rowCount;
    private String errorMessage;
    private LocalDateTime createTime;
}
