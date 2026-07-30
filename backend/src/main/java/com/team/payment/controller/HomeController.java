package com.team.payment.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 根路径提示 —— 前后端分离后后端仅提供 REST API，前端独立部署。
 */
@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, String> index() {
        return Map.of(
                "service", "Payment Processing API",
                "status", "running",
                "docs", "/api/payments  |  /api/accounts"
        );
    }
}
