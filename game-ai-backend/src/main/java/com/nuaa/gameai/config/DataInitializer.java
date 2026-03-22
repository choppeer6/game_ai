package com.nuaa.gameai.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nuaa.gameai.entity.AlgorithmConfig;
import com.nuaa.gameai.entity.SceneConfig;
import com.nuaa.gameai.entity.SysUser;
import com.nuaa.gameai.mapper.AlgorithmConfigMapper;
import com.nuaa.gameai.mapper.SceneConfigMapper;
import com.nuaa.gameai.mapper.SysUserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final SceneConfigMapper sceneConfigMapper;
    private final AlgorithmConfigMapper algorithmConfigMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserMapper sysUserMapper, SceneConfigMapper sceneConfigMapper,
                           AlgorithmConfigMapper algorithmConfigMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.sceneConfigMapper = sceneConfigMapper;
        this.algorithmConfigMapper = algorithmConfigMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin"));
        if (count == null || count == 0) {
            SysUser u = new SysUser();
            u.setUsername("admin");
            u.setPasswordHash(passwordEncoder.encode("admin123"));
            u.setDisplayName("管理员");
            u.setRole("ADMIN");
            sysUserMapper.insert(u);
        }
        Long c2 = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "commander"));
        if (c2 == null || c2 == 0) {
            SysUser u2 = new SysUser();
            u2.setUsername("commander");
            u2.setPasswordHash(passwordEncoder.encode("commander123"));
            u2.setDisplayName("战术指挥");
            u2.setRole("COMMANDER");
            sysUserMapper.insert(u2);
        }

        insertSceneIfAbsent("演示-无人机集群对抗（大图）", 32, 32, 3, 3,
                "{\"win_condition\":{\"type\":\"eliminate\",\"max_steps\":300},\"obstacles\":[],\"params\":{\"speed\":1,\"sensor_radius\":5}}");
        insertSceneIfAbsent("快速训练场景（8×8）", 8, 8, 2, 2,
                "{\"win_condition\":{\"type\":\"eliminate\",\"max_steps\":100},\"obstacles\":[],\"params\":{\"speed\":1,\"sensor_radius\":5}}");
        insertSceneIfAbsent("中型对抗场景（12×12）", 12, 12, 3, 3,
                "{\"win_condition\":{\"type\":\"eliminate\",\"max_steps\":200},\"obstacles\":[[3,3],[3,8],[8,3],[8,8]],\"params\":{\"speed\":1,\"sensor_radius\":5}}");

        insertAlgoIfAbsent("快速验证 IPPO",  "IPPO",
                "{\"learning_rate\":0.001,\"discount_factor\":0.99,\"max_epochs\":100,"
                + "\"rollout_episodes\":10,\"ppo_epochs\":4,\"clip_coef\":0.2,"
                + "\"vf_coef\":0.5,\"ent_coef\":0.05,\"max_grad_norm\":0.5}");
        insertAlgoIfAbsent("标准训练 IPPO",  "IPPO",
                "{\"learning_rate\":0.001,\"discount_factor\":0.99,\"max_epochs\":500,"
                + "\"rollout_episodes\":20,\"ppo_epochs\":6,\"clip_coef\":0.2,"
                + "\"vf_coef\":0.5,\"ent_coef\":0.05,\"max_grad_norm\":0.5}");
        insertAlgoIfAbsent("高质量训练 IPPO", "IPPO",
                "{\"learning_rate\":0.0005,\"discount_factor\":0.99,\"max_epochs\":1000,"
                + "\"rollout_episodes\":30,\"ppo_epochs\":8,\"clip_coef\":0.15,"
                + "\"vf_coef\":0.5,\"ent_coef\":0.02,\"max_grad_norm\":0.5}");
    }

    private void insertSceneIfAbsent(String name, int w, int h, int red, int blue, String json) {
        Long cnt = sceneConfigMapper.selectCount(
                new LambdaQueryWrapper<SceneConfig>().eq(SceneConfig::getSceneName, name));
        if (cnt == null || cnt == 0) {
            SceneConfig s = new SceneConfig();
            s.setSceneName(name);
            s.setMapWidth(w);
            s.setMapHeight(h);
            s.setRedDroneCount(red);
            s.setBlueDroneCount(blue);
            s.setSceneJson(json);
            sceneConfigMapper.insert(s);
        }
    }

    private void insertAlgoIfAbsent(String templateName, String algoName, String hyperParameters) {
        Long cnt = algorithmConfigMapper.selectCount(
                new LambdaQueryWrapper<AlgorithmConfig>().eq(AlgorithmConfig::getTemplateName, templateName));
        if (cnt == null || cnt == 0) {
            AlgorithmConfig a = new AlgorithmConfig();
            a.setTemplateName(templateName);
            a.setAlgoName(algoName);
            a.setHyperParameters(hyperParameters);
            algorithmConfigMapper.insert(a);
        }
    }
}
