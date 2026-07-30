package com.team.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置 —— 前后端分离后前端通过独立域名/端口访问后端 API，需要开放 CORS
 */
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        // 开发阶段：允许本地常用静态服务端口
                        // 生产部署时改为实际前端域名，例如 "https://pay.example.com"
                        .allowedOrigins(
                                "http://localhost:3000",
                                "http://localhost:5500",
                                "http://127.0.0.1:5500",
                                "http://localhost:8081"
                        )
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("*")
                        .allowCredentials(false)
                        .maxAge(3600);
            }
        };
    }
}

