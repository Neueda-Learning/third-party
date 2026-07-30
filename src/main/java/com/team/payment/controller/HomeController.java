package com.team.payment.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 首页控制器
 * 提供前端HTML入口
 */
@Controller
public class HomeController {

    /**
     * 首页
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }
}

