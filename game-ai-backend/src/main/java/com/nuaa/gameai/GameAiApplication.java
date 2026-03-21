package com.nuaa.gameai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.nuaa.gameai.mapper")
@EnableAsync
public class GameAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameAiApplication.class, args);
    }
}
