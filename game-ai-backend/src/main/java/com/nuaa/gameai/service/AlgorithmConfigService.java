package com.nuaa.gameai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nuaa.gameai.entity.AlgorithmConfig;
import com.nuaa.gameai.mapper.AlgorithmConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AlgorithmConfigService {

    private final AlgorithmConfigMapper algorithmConfigMapper;

    public Page<AlgorithmConfig> page(int current, int size) {
        return algorithmConfigMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<AlgorithmConfig>()
                .orderByDesc(AlgorithmConfig::getCreateTime));
    }

    public AlgorithmConfig get(Long id) {
        return algorithmConfigMapper.selectById(id);
    }

    public void save(AlgorithmConfig cfg) {
        if (!StringUtils.hasText(cfg.getTemplateName())) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        if (!StringUtils.hasText(cfg.getAlgoName())) {
            throw new IllegalArgumentException("算法名称不能为空");
        }
        if (!StringUtils.hasText(cfg.getHyperParameters())) {
            throw new IllegalArgumentException("超参数 JSON 不能为空");
        }
        if (cfg.getId() == null) {
            algorithmConfigMapper.insert(cfg);
        } else {
            algorithmConfigMapper.updateById(cfg);
        }
    }

    public void delete(Long id) {
        algorithmConfigMapper.deleteById(id);
    }
}
