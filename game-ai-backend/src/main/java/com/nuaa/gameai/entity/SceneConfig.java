package com.nuaa.gameai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "scene_config", autoResultMap = true)
public class SceneConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sceneName;
    private Integer mapWidth;
    private Integer mapHeight;
    private Integer redDroneCount;
    private Integer blueDroneCount;
    /** JSON: obstacles, positions, params, winCondition */
    @TableField("scene_json")
    private String sceneJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
