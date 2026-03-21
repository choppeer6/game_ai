package com.nuaa.gameai.controller;

import com.nuaa.gameai.dto.ApiResult;
import com.nuaa.gameai.dto.LoginRequest;
import com.nuaa.gameai.dto.LoginResponse;
import com.nuaa.gameai.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResult.ok(authService.login(req));
    }
}
