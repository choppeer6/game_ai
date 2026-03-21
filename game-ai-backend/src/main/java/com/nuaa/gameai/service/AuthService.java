package com.nuaa.gameai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nuaa.gameai.dto.LoginRequest;
import com.nuaa.gameai.dto.LoginResponse;
import com.nuaa.gameai.entity.SysUser;
import com.nuaa.gameai.mapper.SysUserMapper;
import com.nuaa.gameai.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest req) {
        SysUser u = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername()));
        if (u == null || !passwordEncoder.matches(req.getPassword(), u.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = jwtUtil.createToken(u.getUsername(), u.getRole());
        return new LoginResponse(token, u.getUsername(), u.getRole(), u.getDisplayName());
    }
}
