package com.nuaa.gameai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuaa.gameai.entity.SceneConfig;
import com.nuaa.gameai.mapper.SceneConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SceneConfigService {

    private final SceneConfigMapper sceneConfigMapper;
    private final ObjectMapper objectMapper;

    public Page<SceneConfig> page(int current, int size) {
        return sceneConfigMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<SceneConfig>()
                .orderByDesc(SceneConfig::getCreateTime));
    }

    public SceneConfig get(Long id) {
        return sceneConfigMapper.selectById(id);
    }

    public void save(SceneConfig scene) {
        validate(scene);
        if (scene.getId() == null) {
            sceneConfigMapper.insert(scene);
        } else {
            sceneConfigMapper.updateById(scene);
        }
    }

    public void delete(Long id) {
        sceneConfigMapper.deleteById(id);
    }

    private void validate(SceneConfig s) {
        if (!StringUtils.hasText(s.getSceneName())) {
            throw new IllegalArgumentException("场景名称不能为空");
        }
        if (s.getMapWidth() == null || s.getMapHeight() == null || s.getMapWidth() < 1 || s.getMapHeight() < 1) {
            throw new IllegalArgumentException("地图尺寸不合法");
        }
        if (s.getRedDroneCount() == null || s.getBlueDroneCount() == null || s.getRedDroneCount() < 1 || s.getBlueDroneCount() < 1) {
            throw new IllegalArgumentException("无人机数量不合法");
        }
        if (StringUtils.hasText(s.getSceneJson())) {
            try {
                objectMapper.readTree(s.getSceneJson());
            } catch (Exception e) {
                throw new IllegalArgumentException("扩展 JSON 无法解析: " + e.getMessage());
            }
        }
    }
}
