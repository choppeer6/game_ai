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

        Long sc = sceneConfigMapper.selectCount(new LambdaQueryWrapper<>());
        if (sc == null || sc == 0) {
            SceneConfig s = new SceneConfig();
            s.setSceneName("演示-无人机集群对抗");
            s.setMapWidth(32);
            s.setMapHeight(32);
            s.setRedDroneCount(3);
            s.setBlueDroneCount(3);
            s.setSceneJson("{\"win_condition\":{\"type\":\"eliminate\"},\"obstacles\":[],\"params\":{\"speed\":1,\"sensor_radius\":5}}");
            sceneConfigMapper.insert(s);
        }

        Long ac = algorithmConfigMapper.selectCount(new LambdaQueryWrapper<>());
        if (ac == null || ac == 0) {
            AlgorithmConfig a = new AlgorithmConfig();
            a.setTemplateName("默认 MAPPO");
            a.setAlgoName("MAPPO");
            a.setHyperParameters("{\"learning_rate\":0.0003,\"discount_factor\":0.99,\"max_epochs\":20,\"rollout_episodes\":4,\"ppo_epochs\":2,\"clip_coef\":0.2}");
            algorithmConfigMapper.insert(a);
        }
    }
}
