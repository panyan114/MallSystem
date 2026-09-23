package com.mall.controller;

import com.mall.vo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class IndexController {

    @GetMapping("/")
    public Result<Map<String, Object>> index() {
        Map<String, Object> data = new HashMap<>();
        data.put("service", "mall-system backend");
        data.put("status", "running");
        data.put("frontend", "http://localhost:3000");
        data.put("apiPrefix", "/api");
        return Result.success(data);
    }
}
